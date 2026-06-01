package ru.zona.app.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import ru.zona.app.core.di.AppGraph
import ru.zona.app.core.model.User
import ru.zona.app.core.model.UserRole
import ru.zona.app.feature.chat.ChatListStore
import ru.zona.app.feature.chat.ChatStore
import ru.zona.app.feature.flashcards.DecksStore
import ru.zona.app.feature.flashcards.StudyStore
import ru.zona.app.feature.learning.presentation.AuthoringStore
import ru.zona.app.feature.learning.presentation.CatalogStore
import ru.zona.app.feature.learning.presentation.CourseDetailStore
import ru.zona.app.feature.learning.presentation.LessonStore
import ru.zona.app.feature.profile.presentation.ProfileStore
import ru.zona.app.feature.sessions.SessionsStore
import ru.zona.app.feature.teacher.AdminStore
import ru.zona.app.feature.teacher.ApplicationStore
import ru.zona.app.feature.teacher.TeachersStore
import ru.zona.app.feature.wallet.WalletStore
import ru.zona.app.ui.ProfileScreen
import ru.zona.app.ui.chat.ChatListScreen
import ru.zona.app.ui.chat.ChatScreen
import ru.zona.app.ui.flashcards.DecksScreen
import ru.zona.app.ui.flashcards.StudyScreen
import ru.zona.app.ui.learning.CatalogScreen
import ru.zona.app.ui.learning.CourseDetailScreen
import ru.zona.app.ui.learning.LessonScreen
import ru.zona.app.ui.sessions.SessionsScreen
import ru.zona.app.ui.teacher.AdminScreen
import ru.zona.app.ui.teacher.ApplicationScreen
import ru.zona.app.ui.teacher.AuthoringScreen
import ru.zona.app.ui.teacher.TeachersScreen
import ru.zona.app.ui.wallet.WalletScreen

private data class TabItem(val id: String, val label: String, val icon: ImageVector)

@Composable
fun MainShell(
    graph: AppGraph,
    user: User,
    onUserUpdated: (User) -> Unit,
    onLogout: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val nav = remember { NavStack() }

    val tabs = remember(user.role) {
        buildList {
            add(TabItem("catalog", "Курсы", Icons.Default.School))
            add(TabItem("cards", "Карточки", Icons.Default.Style))
            add(TabItem("sessions", "Занятия", Icons.Default.Event))
            add(TabItem("chat", "Чат", Icons.Default.Chat))
            if (user.role == UserRole.TEACHER) add(TabItem("teach", "Создать", Icons.Outlined.Add))
            if (user.role == UserRole.ADMIN) add(TabItem("admin", "Модерация", Icons.Default.AdminPanelSettings))
            add(TabItem("profile", "Профиль", Icons.Default.Person))
        }
    }
    var tabId by rememberSaveable(user.role) { mutableStateOf(tabs.first().id) }

    // Stores (held across recompositions)
    val catalogStore = remember { CatalogStore(graph.learningRepository, scope) }
    val decksStore = remember { DecksStore(graph.flashcardRepository, scope) }
    val sessionsStore = remember { SessionsStore(graph.sessionRepository, scope) }
    val chatListStore = remember { ChatListStore(graph.chatRepository, scope) }
    val profileStore = remember(user.id) { ProfileStore(user, graph.profileRepository, scope) }
    val authoringStore = remember { AuthoringStore(graph.learningRepository, scope) }
    val adminStore = remember { AdminStore(graph.teacherRepository, scope) }

    val overlay = nav.current

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (overlay == null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = tabId == tab.id,
                            onClick = { tabId = tab.id },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (overlay != null) {
                Overlay(graph, user, nav, overlay, onMessage)
            } else {
                when (tabId) {
                    "catalog" -> CatalogScreen(catalogStore) { c -> nav.push(Destination.CourseDetail(c.id, c.title)) }
                    "cards" -> DecksScreen(decksStore) { d -> nav.push(Destination.Deck(d.id, d.title)) }
                    "sessions" -> SessionsScreen(sessionsStore, onMessage)
                    "chat" -> ChatListScreen(chatListStore) { c -> nav.push(Destination.Chat(c.id, c.peerName)) }
                    "teach" -> AuthoringScreen(authoringStore, onMessage)
                    "admin" -> AdminScreen(adminStore, onMessage)
                    "profile" ->
                        ProfileScreen(
                            store = profileStore,
                            onUserUpdated = onUserUpdated,
                            onMessage = onMessage,
                            onOpenWallet = { nav.push(Destination.Wallet) },
                            onBecomeTeacher = { nav.push(Destination.Application) },
                            onLogout = onLogout,
                        )
                }
            }
        }
    }
}

@Composable
private fun Overlay(
    graph: AppGraph,
    user: User,
    nav: NavStack,
    dest: Destination,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    when (dest) {
        is Destination.CourseDetail -> {
            val store = remember(dest.courseId) { CourseDetailStore(dest.courseId, graph.learningRepository, scope) }
            CourseDetailScreen(
                store = store,
                onBack = { nav.pop() },
                onOpenLesson = { lesson -> nav.push(Destination.Lesson(lesson.id, lesson.title)) },
                onMessage = onMessage,
            )
        }
        is Destination.Lesson -> {
            val store = remember(dest.lessonId) { LessonStore(dest.lessonId, graph.learningRepository, scope) }
            LessonScreen(dest.title, store, onBack = { nav.pop() }, onMessage = onMessage)
        }
        is Destination.Deck -> {
            val store = remember(dest.deckId) { StudyStore(dest.deckId, graph.flashcardRepository, scope) }
            StudyScreen(dest.title, store, onBack = { nav.pop() })
        }
        is Destination.Chat -> {
            val store = remember(dest.conversationId) { ChatStore(dest.conversationId, graph.chatRepository, scope) }
            ChatScreen(dest.peerName, user.id, store, onBack = { nav.pop() }, onMessage = onMessage)
        }
        Destination.Teachers -> {
            val store = remember { TeachersStore(graph.teacherRepository, scope) }
            TeachersScreen(store, onOpenChat = { _, _ -> onMessage("Откройте чат из списка преподавателей") }, onMessage = onMessage)
        }
        Destination.Application -> {
            val store = remember { ApplicationStore(graph.teacherRepository, scope) }
            ApplicationScreen(store, onBack = { nav.pop() }, onMessage = onMessage)
        }
        Destination.Wallet -> {
            val store = remember { WalletStore(graph.walletRepository, scope) }
            WalletScreen(store, onBack = { nav.pop() }, onMessage = onMessage)
        }
        Destination.Authoring -> Unit
    }
}
