package ru.zona.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.zona.app.core.media.Avatar
import ru.zona.app.core.media.rememberFilePicker
import ru.zona.app.core.media.rememberImagePicker
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
import zona.resources.Res
import zona.resources.profile_title
import ru.zona.app.feature.profile.presentation.ProfileEffect
import ru.zona.app.feature.profile.presentation.ProfileIntent
import ru.zona.app.feature.profile.presentation.ProfileStore

@Composable
fun ProfileScreen(
    store: ProfileStore,
    walletRepository: ru.zona.app.feature.wallet.WalletRepository,
    certificatesStore: ru.zona.app.feature.profile.CertificatesStore,
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
    val certState by certificatesStore.collectState { eff ->
        when (eff) { is ru.zona.app.feature.profile.CertificatesEffect.Message -> onMessage(eff.text) }
    }
    LaunchedEffect(Unit) {
        store.dispatch(ProfileIntent.Refresh)
        certificatesStore.dispatch(ru.zona.app.feature.profile.CertificatesIntent.Load)
    }
    val pickCertificate = rememberFilePicker { picked ->
        if (picked != null) certificatesStore.dispatch(ru.zona.app.feature.profile.CertificatesIntent.Add(picked.name))
    }

    var balanceCents by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(state.user.id, state.user.xp) {
        when (val r = walletRepository.wallet()) {
            is ru.zona.app.core.result.Outcome.Success -> balanceCents = r.data.balanceCents
            is ru.zona.app.core.result.Outcome.Failure -> Unit
        }
    }

    val pickAvatar = rememberImagePicker { picked ->
        if (picked != null) store.dispatch(ProfileIntent.SetAvatarUrl(picked))
    }

    val user = state.user
    val roleLabel = when (user.role) {
        UserRole.STUDENT -> "Ученик"
        UserRole.TEACHER -> "Преподаватель"
        UserRole.ADMIN -> "Администратор"
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(org.jetbrains.compose.resources.stringResource(Res.string.profile_title), user.email)
        if (state.refreshing && !state.editing) {
            LoadingState(Modifier.weight(1f))
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ZonaCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Avatar(base64 = user.avatarUrl, name = user.displayName, size = 72.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(user.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ZonaBadge(roleLabel)
                                ZonaBadge("⭐ ${user.xp} XP", content = MaterialTheme.colorScheme.secondary)
                            }
                            balanceCents?.let { bal ->
                                ZonaBadge("💰 ${ru.zona.app.core.util.formatBalance(bal)}", content = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                if (user.bio.isNotBlank() && !state.editing) {
                    ZonaCard(Modifier.fillMaxWidth()) {
                        Text(user.bio, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (state.editing) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Avatar(base64 = state.avatarUrl.ifBlank { null }, name = state.displayName, size = 110.dp, modifier = Modifier.clickable { pickAvatar() })
                            ZonaSecondaryButton("Сменить фото") { pickAvatar() }
                            if (state.avatarUrl.isNotBlank()) {
                                Text("Нажмите, чтобы заменить", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    ZonaTextField(state.displayName, { store.dispatch(ProfileIntent.SetDisplayName(it)) }, "Имя")
                    ZonaTextField(state.bio, { store.dispatch(ProfileIntent.SetBio(it)) }, "О себе", singleLine = false, minLines = 3)
                    ZonaPrimaryButton(if (state.saving) "Сохранение…" else "Сохранить", enabled = state.displayName.isNotBlank() && !state.saving) {
                        store.dispatch(ProfileIntent.Save)
                    }
                    ZonaSecondaryButton("Отмена", enabled = !state.saving) { store.dispatch(ProfileIntent.CancelEdit) }
                } else {
                    // Сертификаты — доступны всегда, в т.ч. преподавателю.
                    ZonaCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Мои сертификаты и документы", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            if (certState.items.isEmpty()) {
                                Text("Пока ничего не прикреплено", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                certState.items.forEach { c ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("📎 ${c.fileName}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        androidx.compose.material3.TextButton(onClick = { certificatesStore.dispatch(ru.zona.app.feature.profile.CertificatesIntent.Remove(c.id)) }) {
                                            Text("Удалить", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                            ZonaSecondaryButton("📎 Прикрепить сертификат") { pickCertificate() }
                        }
                    }
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
