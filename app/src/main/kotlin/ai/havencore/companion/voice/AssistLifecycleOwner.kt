package ai.havencore.companion.voice

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * `VoiceInteractionSession` is not an `Activity` / `Fragment`, so it has no
 * built-in [LifecycleOwner], [SavedStateRegistryOwner], or
 * [ViewModelStoreOwner]. ComposeView's `setContent` requires all three on
 * the host view (otherwise `LocalLifecycleOwner.current` and any
 * `rememberSaveable` / animation that touches them crashes), so we attach
 * this minimal shim to the ComposeView's view tree before composition.
 *
 * The session pumps its own lifecycle events (onCreate / onShow / onHide /
 * onDestroy) into [LifecycleRegistry] manually, and clears the
 * [ViewModelStore] on destroy so any view-models scoped to this owner are
 * cleaned up between invocations.
 */
class AssistLifecycleOwner :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {

    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this).also {
        it.performAttach()
        it.performRestore(null)
    }

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    fun onCreate()  { registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE) }
    fun onStart()   { registry.handleLifecycleEvent(Lifecycle.Event.ON_START) }
    fun onResume()  { registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
    fun onPause()   { registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE) }
    fun onStop()    { registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP) }
    fun onDestroy() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}
