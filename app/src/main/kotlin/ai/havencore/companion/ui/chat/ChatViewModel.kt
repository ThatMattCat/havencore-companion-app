package ai.havencore.companion.ui.chat

import ai.havencore.companion.data.SettingsRepository
import ai.havencore.companion.net.ChatApi
import ai.havencore.companion.net.ChatEvent
import ai.havencore.companion.net.ChatWsSession
import ai.havencore.companion.net.ParsedFrame
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

class ChatViewModel(
    private val settings: SettingsRepository,
    private val chatApi: ChatApi,
    private val ws: ChatWsSession,
    private val sessionToResume: String?,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var keyCounter: Long = 0L
    private fun nextKey(): Long = ++keyCounter

    private var hasHydrated: Boolean = false

    init {
        runSession()
    }

    fun retry() {
        runSession()
    }

    private fun runSession() {
        viewModelScope.launch {
            val cfg = settings.configFlow.first()

            // 1. Hydrate prior turns once. retry() is a re-connect, not a
            //    re-hydrate — the existing turn list stays put.
            if (sessionToResume != null && !hasHydrated) {
                val resumed = chatApi.resumeConversation(cfg.baseUrl, sessionToResume)
                resumed.onSuccess { resp ->
                    hasHydrated = true
                    val turns = ResumeMapper.toTurns(resp.messages, ::nextKey)
                    _state.update { it.copy(turns = turns, sessionId = resp.session_id) }
                }.onFailure { t ->
                    Log.w(TAG, "resume failed: ${t.message}")
                }
            }

            // 2. Open the WS. The supervisor inside ws.connect lives on
            //    viewModelScope, so it tears down when this VM is cleared.
            val frames = ws.connect(
                scope = viewModelScope,
                baseUrl = cfg.baseUrl,
                sessionId = sessionToResume ?: _state.value.sessionId,
                deviceName = cfg.deviceName,
                idleTimeout = -1,
            )

            // 3. Mirror status into the UI state.
            launch {
                ws.status.collect { s -> _state.update { it.copy(connection = mapStatus(s)) } }
            }

            // 4. Reduce inbound frames into the turn list.
            frames.collect { frame -> handleFrame(frame) }
        }
    }

    fun updateDraft(text: String) {
        _state.update { it.copy(draft = text) }
    }

    fun send() {
        val text = _state.value.draft.trim()
        if (text.isBlank() || _state.value.turnInFlight) return

        // Append user turn + placeholder assistant turn before transmitting.
        _state.update { s ->
            s.copy(
                turns = s.turns +
                    TurnItem.UserTurn(nextKey(), text) +
                    TurnItem.AssistantTurn(nextKey()),
                draft = "",
                sending = true,
                turnInFlight = true,
            )
        }

        if (!ws.send(text)) {
            // No socket — surface an error chip and re-enable Send.
            _state.update { s ->
                s.copy(
                    turns = s.turns.updateLastAssistantTurn { at ->
                        at.copy(errorText = "Not connected", thinkingIteration = null)
                    },
                    sending = false,
                    turnInFlight = false,
                )
            }
        } else {
            _state.update { it.copy(sending = false) }
        }
    }

    override fun onCleared() {
        ws.close()
    }

    private fun handleFrame(frame: ParsedFrame) {
        when (frame) {
            is ParsedFrame.Known -> reduce(frame.event)
            is ParsedFrame.Unknown -> Log.w(TAG, "unknown event type=${frame.type}")
            is ParsedFrame.Malformed -> Log.w(TAG, "malformed frame: ${frame.cause.message}")
        }
    }

    private fun reduce(event: ChatEvent) {
        when (event) {
            is ChatEvent.Session -> {
                _state.update { it.copy(sessionId = event.session_id) }
                viewModelScope.launch { settings.setLastSessionId(event.session_id) }
            }

            is ChatEvent.Thinking -> _state.update { s ->
                s.copy(turns = s.turns.updateLastAssistantTurn { it.copy(thinkingIteration = event.iteration) })
            }

            is ChatEvent.ToolCall -> _state.update { s ->
                s.copy(turns = s.turns.updateLastAssistantTurn { at ->
                    at.copy(events = at.events + TurnEvent.ToolPair(event.id, event.tool, event.args))
                })
            }

            is ChatEvent.ToolResult -> _state.update { s ->
                s.copy(turns = s.turns.updateLastAssistantTurn { at ->
                    val updated = at.events.toMutableList()
                    val idx = updated.indexOfLast { it is TurnEvent.ToolPair && it.id == event.id }
                    if (idx >= 0) {
                        val existing = updated[idx] as TurnEvent.ToolPair
                        updated[idx] = existing.copy(result = event.result, ms = event.ms)
                    } else {
                        // Defensive: synthesize a ToolPair if a tool_result
                        // arrives with no matching tool_call. Per plan.
                        updated += TurnEvent.ToolPair(
                            id = event.id,
                            tool = event.tool,
                            args = JsonObject(emptyMap()),
                            result = event.result,
                            ms = event.ms,
                        )
                    }
                    at.copy(events = updated)
                })
            }

            is ChatEvent.Reasoning -> _state.update { s ->
                s.copy(turns = s.turns.updateLastAssistantTurn { at ->
                    at.copy(events = at.events + TurnEvent.Reasoning(event.content, event.iteration))
                })
            }

            is ChatEvent.Metric -> _state.update { s ->
                s.copy(turns = s.turns.updateLastAssistantTurn { it.copy(metric = event) })
            }

            is ChatEvent.Done -> _state.update { s ->
                s.copy(
                    turns = s.turns.updateLastAssistantTurn {
                        it.copy(finalText = event.content, thinkingIteration = null)
                    },
                    turnInFlight = false,
                )
            }

            is ChatEvent.Err -> _state.update { s ->
                s.copy(
                    turns = s.turns.updateLastAssistantTurn {
                        it.copy(errorText = event.error, thinkingIteration = null)
                    },
                    turnInFlight = false,
                )
            }

            is ChatEvent.SummaryReset -> _state.update { s ->
                s.copy(turns = s.turns + TurnItem.SummaryResetMarker(nextKey(), event.reason, event.summary))
            }
        }
    }

    private fun mapStatus(s: ChatWsSession.Status): ConnectionUi = when (s) {
        ChatWsSession.Status.Idle, ChatWsSession.Status.Connecting -> ConnectionUi.Connecting
        ChatWsSession.Status.Connected -> ConnectionUi.Connected
        is ChatWsSession.Status.Reconnecting -> ConnectionUi.Reconnecting(s.attempt, s.nextDelayMs)
        is ChatWsSession.Status.Closed -> ConnectionUi.Failed("Closed: ${s.reason}")
        is ChatWsSession.Status.Failed -> ConnectionUi.Failed(s.cause.message ?: "Failed")
    }

    private companion object {
        const val TAG = "ChatVM"
    }
}

private fun List<TurnItem>.updateLastAssistantTurn(
    transform: (TurnItem.AssistantTurn) -> TurnItem.AssistantTurn,
): List<TurnItem> {
    val idx = indexOfLast { it is TurnItem.AssistantTurn }
    if (idx < 0) return this
    val mut = toMutableList()
    mut[idx] = transform(mut[idx] as TurnItem.AssistantTurn)
    return mut
}
