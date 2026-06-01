package ru.zona.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.zona.app.core.design.LoadingState
import ru.zona.app.core.design.ZonaCard
import ru.zona.app.core.design.ZonaPrimaryButton
import ru.zona.app.core.design.ZonaSecondaryButton
import ru.zona.app.core.design.ZonaTextField
import ru.zona.app.core.model.User
import ru.zona.app.core.model.UserRole
import ru.zona.app.core.mvi.collectState
import ru.zona.app.feature.profile.presentation.ProfileEffect
import ru.zona.app.feature.profile.presentation.ProfileIntent
import ru.zona.app.feature.profile.presentation.ProfileState
import ru.zona.app.feature.profile.presentation.ProfileStore

@Composable
fun ProfileScreen(
    store: ProfileStore,
    onBack: () -> Unit,
    onSaved: (User) -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by store.collectState { effect ->
        when (effect) {
            is ProfileEffect.Message -> onMessage(effect.text)
            is ProfileEffect.Saved -> onSaved(effect.user)
        }
    }

    LaunchedEffect(Unit) {
        store.dispatch(ProfileIntent.Refresh)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text("Профиль", style = MaterialTheme.typography.titleLarge)
        }

        if (state.refreshing && !state.editing) {
            LoadingState(Modifier.weight(1f))
        } else {
            ProfileBody(
                state = state,
                onIntent = store::dispatch,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ProfileBody(
    state: ProfileState,
    onIntent: (ProfileIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roleLabel =
        when (state.user.role) {
            UserRole.STUDENT -> "Ученик"
            UserRole.TEACHER -> "Преподаватель"
            UserRole.ADMIN -> "Администратор"
        }

    Column(
        modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ZonaCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(state.user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Роль: $roleLabel", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (state.editing) {
            ZonaTextField(state.displayName, { onIntent(ProfileIntent.SetDisplayName(it)) }, "Имя")
            ZonaTextField(
                value = state.bio,
                onValueChange = { onIntent(ProfileIntent.SetBio(it)) },
                label = "О себе",
                singleLine = false,
                minLines = 3,
            )
            ZonaTextField(
                state.avatarUrl,
                { onIntent(ProfileIntent.SetAvatarUrl(it)) },
                "Ссылка на аватар (необязательно)",
            )
            ZonaPrimaryButton(
                text = if (state.saving) "Сохранение…" else "Сохранить",
                enabled = state.displayName.isNotBlank() && !state.saving,
            ) {
                onIntent(ProfileIntent.Save)
            }
            ZonaSecondaryButton(text = "Отмена", enabled = !state.saving) {
                onIntent(ProfileIntent.CancelEdit)
            }
        } else {
            ZonaCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.user.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.user.bio.ifBlank { "Биография не заполнена" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.user.avatarUrl?.let { url ->
                        Text("Аватар: $url", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            ZonaPrimaryButton(text = "Редактировать") { onIntent(ProfileIntent.StartEdit) }
        }
    }
}
