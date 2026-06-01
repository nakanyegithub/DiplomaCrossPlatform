package ru.zona.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.zona.app.core.design.LoadingState
import ru.zona.app.core.design.ScreenHeader
import ru.zona.app.core.design.ZonaBadge
import ru.zona.app.core.design.ZonaCard
import ru.zona.app.core.design.ZonaPrimaryButton
import ru.zona.app.core.design.ZonaSecondaryButton
import ru.zona.app.core.design.ZonaTextField
import ru.zona.app.core.model.User
import ru.zona.app.core.model.UserRole
import ru.zona.app.core.mvi.collectState
import ru.zona.app.feature.profile.presentation.ProfileEffect
import ru.zona.app.feature.profile.presentation.ProfileIntent
import ru.zona.app.feature.profile.presentation.ProfileStore

@Composable
fun ProfileScreen(
    store: ProfileStore,
    onUserUpdated: (User) -> Unit,
    onMessage: (String) -> Unit,
    onOpenWallet: () -> Unit,
    onBecomeTeacher: () -> Unit,
    onLogout: () -> Unit,
) {
    val state by store.collectState { effect ->
        when (effect) {
            is ProfileEffect.Message -> onMessage(effect.text)
            is ProfileEffect.Saved -> onUserUpdated(effect.user)
        }
    }
    LaunchedEffect(Unit) { store.dispatch(ProfileIntent.Refresh) }

    val user = state.user
    val roleLabel = when (user.role) {
        UserRole.STUDENT -> "Ученик"
        UserRole.TEACHER -> "Преподаватель"
        UserRole.ADMIN -> "Администратор"
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Профиль", user.email)
        if (state.refreshing && !state.editing) {
            LoadingState(Modifier.weight(1f))
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ZonaCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(user.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ZonaBadge(roleLabel)
                            ZonaBadge("⭐ ${user.xp} XP", content = MaterialTheme.colorScheme.secondary)
                        }
                        Text(user.bio.ifBlank { "Биография не заполнена" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (state.editing) {
                    ZonaTextField(state.displayName, { store.dispatch(ProfileIntent.SetDisplayName(it)) }, "Имя")
                    ZonaTextField(state.bio, { store.dispatch(ProfileIntent.SetBio(it)) }, "О себе", singleLine = false, minLines = 3)
                    ZonaTextField(state.avatarUrl, { store.dispatch(ProfileIntent.SetAvatarUrl(it)) }, "Ссылка на аватар (необязательно)")
                    ZonaPrimaryButton(if (state.saving) "Сохранение…" else "Сохранить", enabled = state.displayName.isNotBlank() && !state.saving) {
                        store.dispatch(ProfileIntent.Save)
                    }
                    ZonaSecondaryButton("Отмена", enabled = !state.saving) { store.dispatch(ProfileIntent.CancelEdit) }
                } else {
                    ZonaPrimaryButton("Редактировать профиль") { store.dispatch(ProfileIntent.StartEdit) }
                    ZonaSecondaryButton("Кошелёк") { onOpenWallet() }
                    if (user.role == UserRole.STUDENT) {
                        ZonaSecondaryButton("Стать преподавателем") { onBecomeTeacher() }
                    }
                    ZonaSecondaryButton("Выйти из аккаунта") { onLogout() }
                }
            }
        }
    }
}
