package ru.zona.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.zona.app.core.design.ZonaPrimaryButton
import ru.zona.app.core.design.ZonaSecondaryButton
import ru.zona.app.core.model.User
import ru.zona.app.core.model.UserRole
import ru.zona.app.feature.profile.domain.ProfileRepository
import ru.zona.app.feature.profile.presentation.ProfileStore

@Composable
fun HomeScreen(
    user: User,
    profileRepository: ProfileRepository,
    onUserUpdated: (User) -> Unit,
    onLogout: () -> Unit,
    onMessage: (String) -> Unit,
) {
    var showProfile by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val profileStore = remember(user.id) { ProfileStore(user, profileRepository, scope) }

    if (showProfile) {
        profileStore.applyUser(user)
        ProfileScreen(
            store = profileStore,
            onBack = { showProfile = false },
            onSaved = { updated ->
                onUserUpdated(updated)
                showProfile = false
            },
            onMessage = onMessage,
        )
    } else {
        HomeMain(
            user = user,
            onOpenProfile = { showProfile = true },
            onLogout = onLogout,
        )
    }
}

@Composable
private fun HomeMain(
    user: User,
    onOpenProfile: () -> Unit,
    onLogout: () -> Unit,
) {
    val roleLabel =
        when (user.role) {
            UserRole.STUDENT -> "Ученик"
            UserRole.TEACHER -> "Преподаватель"
            UserRole.ADMIN -> "Администратор"
        }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Привет, ${user.displayName}!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            "Роль: $roleLabel",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Здесь появятся курсы, занятия и задания.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        ZonaPrimaryButton(text = "Профиль", onClick = onOpenProfile)
        ZonaSecondaryButton(text = "Выйти", onClick = onLogout)
    }
}
