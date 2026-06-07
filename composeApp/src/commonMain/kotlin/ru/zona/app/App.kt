package ru.zona.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import ru.zona.app.app.AppPhase
import ru.zona.app.app.MainShell
import ru.zona.app.app.RootEffect
import ru.zona.app.app.RootIntent
import ru.zona.app.app.RootStore
import org.koin.compose.koinInject
import ru.zona.app.core.design.CosmicBackground
import ru.zona.app.core.design.ZonaTheme
import ru.zona.app.core.di.AppGraph
import ru.zona.app.core.mvi.collectState
import ru.zona.app.core.mvi.rememberStore
import ru.zona.app.ui.AuthScreen
import ru.zona.app.ui.SplashScreen

@Composable
fun App(graph: AppGraph = koinInject()) {
    ZonaTheme {
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        val root = rememberStore { RootStore(graph.authRepository, scope) }
        val state by root.collectState { effect ->
            when (effect) {
                is RootEffect.Message -> scope.launch { snackbar.showSnackbar(effect.text) }
            }
        }

        LaunchedEffect(Unit) { root.dispatch(RootIntent.Bootstrap) }

        CosmicBackground(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Color.Transparent,
                snackbarHost = { SnackbarHost(snackbar) },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (state.phase) {
                        AppPhase.Splash -> SplashScreen()
                        AppPhase.Auth ->
                            AuthScreen(
                                busy = state.busy,
                                onLogin = { e, p -> root.dispatch(RootIntent.Login(e, p)) },
                                onRegister = { e, p, n -> root.dispatch(RootIntent.Register(e, p, n)) },
                            )
                        AppPhase.Home ->
                            state.user?.let { user ->
                                MainShell(
                                    graph = graph,
                                    user = user,
                                    onUserUpdated = { root.dispatch(RootIntent.UpdateUser(it)) },
                                    onLogout = { root.dispatch(RootIntent.Logout) },
                                    onMessage = { scope.launch { snackbar.showSnackbar(it) } },
                                )
                            }
                    }
                }
            }
        }

        // Защита: Home без user — возвращаем на вход.
        LaunchedEffect(state.phase, state.user) {
            if (state.phase == AppPhase.Home && state.user == null) {
                root.dispatch(RootIntent.Logout)
            }
        }
    }
}
