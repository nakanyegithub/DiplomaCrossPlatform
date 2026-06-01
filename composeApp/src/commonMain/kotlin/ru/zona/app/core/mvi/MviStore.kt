package ru.zona.app.core.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Базовый однонаправленный стор: [S] — состояние для UI, [I] — намерения, [E] — одноразовые эффекты.
 */
abstract class MviStore<S, I, E>(
    initialState: S,
    protected val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    protected val currentState: S get() = _state.value

    abstract fun onIntent(intent: I)

    fun dispatch(intent: I) = onIntent(intent)

    protected fun setState(reducer: (S) -> S) = _state.update(reducer)

    protected fun emit(effect: E) {
        scope.launch { _effects.send(effect) }
    }
}

/** Подписка на состояние + эффекты стора внутри Compose. */
@Composable
fun <S, I, E> MviStore<S, I, E>.collectState(onEffect: (E) -> Unit = {}): State<S> {
    val scope = rememberCoroutineScope()
    DisposableEffect(this) {
        val job = scope.launch { effects.collect(onEffect) }
        onDispose { job.cancel() }
    }
    return state.collectAsState()
}

/** Удерживает экземпляр стора на время жизни композиции. */
@Composable
fun <T> rememberStore(factory: () -> T): T = remember { factory() }
