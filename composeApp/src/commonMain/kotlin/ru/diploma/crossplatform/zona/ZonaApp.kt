package ru.diploma.crossplatform.zona

import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.diploma.crossplatform.zona.domain.usecase.ZonaUseCases
import ru.diploma.crossplatform.zona.presentation.mvi.collectMvi
import ru.diploma.crossplatform.zona.presentation.mvi.chat.ChatDetailIntent
import ru.diploma.crossplatform.zona.presentation.mvi.chat.ChatDetailStore
import ru.diploma.crossplatform.zona.presentation.mvi.course.CourseDetailEffect
import ru.diploma.crossplatform.zona.presentation.mvi.course.CourseDetailIntent
import ru.diploma.crossplatform.zona.presentation.mvi.course.CourseDetailStore
import ru.diploma.crossplatform.zona.ui.screens.chat.ChatDetailScreen
import ru.diploma.crossplatform.zona.presentation.mvi.rememberMviStore
import ru.diploma.crossplatform.zona.presentation.mvi.root.RootEffect
import ru.diploma.crossplatform.zona.presentation.mvi.root.RootIntent
import ru.diploma.crossplatform.zona.presentation.mvi.root.RootStore
import ru.diploma.crossplatform.zona.presentation.navigation.ZonaScreen
import ru.diploma.crossplatform.zona.ui.screens.AuthScreen
import ru.diploma.crossplatform.zona.ui.screens.CourseDetailScreen
import ru.diploma.crossplatform.zona.ui.screens.SplashScreen
import ru.diploma.crossplatform.zona.ui.screens.admin.AdminApp
import ru.diploma.crossplatform.zona.ui.screens.student.StudentApp
import ru.diploma.crossplatform.zona.ui.screens.teacher.TeacherApp

@Composable
fun ZonaApp(useCases: ZonaUseCases) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val rootStore = rememberMviStore { RootStore(useCases, scope) }
    val rootState =
        collectMvi(rootStore) { effect ->
            when (effect) {
                is RootEffect.Message -> snackbarShow(snackbar, scope, effect.text)
            }
        }

    LaunchedEffect(Unit) {
        rootStore.dispatch(RootIntent.Bootstrap)
    }

    val top = rootState.stack.lastOrNull() ?: ZonaScreen.Auth

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) {
        when (top) {
            ZonaScreen.Splash -> SplashScreen()

            ZonaScreen.Auth ->
                AuthScreen(
                    busy = rootState.authBusy,
                    onLogin = { email, pass -> rootStore.dispatch(RootIntent.Login(email, pass)) },
                    onRegister = { email, pass, name ->
                        rootStore.dispatch(RootIntent.Register(email, pass, name))
                    },
                )

            ZonaScreen.Home -> {
                val user = rootState.user
                if (user == null) {
                    rootStore.dispatch(RootIntent.Logout)
                } else {
                    when (user.role) {
                        "TEACHER" ->
                            TeacherApp(
                                user = user,
                                useCases = useCases,
                                refreshKey = rootState.teacherRefreshKey,
                                onNeedRefresh = { rootStore.dispatch(RootIntent.RefreshChildData) },
                                onOpenChat = { id, name ->
                                    rootStore.dispatch(RootIntent.OpenChat(id, name))
                                },
                                onUserUpdated = { rootStore.dispatch(RootIntent.UpdateUser(it)) },
                                onMessage = { snackbarShow(snackbar, scope, it) },
                                onLogout = { rootStore.dispatch(RootIntent.Logout) },
                            )

                        "STUDENT" ->
                            StudentApp(
                                user = user,
                                useCases = useCases,
                                refreshKey = rootState.studentRefreshKey,
                                onNeedRefresh = { rootStore.dispatch(RootIntent.RefreshChildData) },
                                onOpenChat = { id, name ->
                                    rootStore.dispatch(RootIntent.OpenChat(id, name))
                                },
                                onUserUpdated = { rootStore.dispatch(RootIntent.UpdateUser(it)) },
                                onMessage = { snackbarShow(snackbar, scope, it) },
                                onLogout = { rootStore.dispatch(RootIntent.Logout) },
                            )

                        else ->
                            AdminApp(
                                user = user,
                                useCases = useCases,
                                onMessage = { snackbarShow(snackbar, scope, it) },
                                onLogout = { rootStore.dispatch(RootIntent.Logout) },
                            )
                    }
                }
            }

            is ZonaScreen.ChatDetail -> {
                val chatStore =
                    rememberMviStore {
                        ChatDetailStore(top.conversationId, top.peerName, useCases, scope)
                    }
                val userId = rootState.user?.id ?: 0L
                ChatDetailScreen(
                    store = chatStore,
                    onBack = { rootStore.dispatch(RootIntent.Back) },
                    currentUserId = userId,
                    onMessage = { snackbarShow(snackbar, scope, it) },
                )
            }

            is ZonaScreen.CourseDetail -> {
                val courseStore =
                    rememberMviStore {
                        CourseDetailStore(top.course, useCases, scope)
                    }
                val courseState =
                    collectMvi(courseStore) { effect ->
                        when (effect) {
                            is CourseDetailEffect.Message -> snackbarShow(snackbar, scope, effect.text)
                            CourseDetailEffect.Enrolled -> rootStore.dispatch(RootIntent.RefreshChildData)
                        }
                    }
                LaunchedEffect(top.course.id) {
                    courseStore.dispatch(CourseDetailIntent.LoadLessons)
                }
                CourseDetailScreen(
                    state = courseState,
                    onBack = { rootStore.dispatch(RootIntent.Back) },
                    onEnroll = { courseStore.dispatch(CourseDetailIntent.Enroll) },
                )
            }
        }
    }
}

private fun snackbarShow(
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
    message: String,
) {
    scope.launch { snackbar.showSnackbar(message) }
}
