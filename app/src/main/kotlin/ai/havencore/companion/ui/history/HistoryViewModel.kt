package ai.havencore.companion.ui.history

import ai.havencore.companion.data.SettingsRepository
import ai.havencore.companion.net.ChatApi
import ai.havencore.companion.net.ConversationSummary
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

sealed interface HistoryListState {
    data object Loading : HistoryListState
    data object Empty : HistoryListState
    data class Loaded(val rows: List<ConversationSummary>) : HistoryListState
    data class Failed(val message: String) : HistoryListState
}

class HistoryViewModel(
    private val settings: SettingsRepository,
    private val chatApi: ChatApi,
) : ViewModel() {

    private val _state = MutableStateFlow<HistoryListState>(HistoryListState.Loading)
    val state: StateFlow<HistoryListState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * Clear the persisted last session id. Call from the FAB path before
     * navigating to a brand-new chat so a future cold-start doesn't quietly
     * resume an unrelated conversation.
     */
    fun clearLastSessionForNewChat() {
        viewModelScope.launch {
            settings.setLastSessionId(null)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = HistoryListState.Loading
            val cfg = settings.configFlow.first()
            if (cfg.baseUrl.isBlank()) {
                _state.value = HistoryListState.Failed("Server URL is not set")
                return@launch
            }
            val result = chatApi.listConversations(cfg.baseUrl)
            _state.value = result.fold(
                onSuccess = { resp ->
                    if (resp.conversations.isEmpty()) {
                        HistoryListState.Empty
                    } else {
                        val sorted = resp.conversations.sortedByDescending { row ->
                            runCatching { Instant.parse(row.created_at) }
                                .getOrNull()
                                ?: Instant.EPOCH
                        }
                        HistoryListState.Loaded(sorted)
                    }
                },
                onFailure = { t ->
                    HistoryListState.Failed(
                        t.message ?: t::class.simpleName ?: "Unknown error"
                    )
                },
            )
        }
    }
}
