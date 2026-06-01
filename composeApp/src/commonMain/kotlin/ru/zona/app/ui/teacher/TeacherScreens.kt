package ru.zona.app.ui.teacher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.zona.app.core.design.LoadingState
import ru.zona.app.core.design.MessageState
import ru.zona.app.core.design.ScreenHeader
import ru.zona.app.core.design.ZonaBadge
import ru.zona.app.core.design.ZonaCard
import ru.zona.app.core.design.ZonaPrimaryButton
import ru.zona.app.core.design.ZonaSecondaryButton
import ru.zona.app.core.design.ZonaTextField
import ru.zona.app.core.mvi.collectState
import ru.zona.app.core.util.formatPrice
import ru.zona.app.feature.teacher.AdminIntent
import ru.zona.app.feature.teacher.AdminStore
import ru.zona.app.feature.teacher.ApplicationIntent
import ru.zona.app.feature.teacher.ApplicationStore
import ru.zona.app.feature.teacher.TeacherDto
import ru.zona.app.feature.teacher.TeachersIntent
import ru.zona.app.feature.teacher.TeachersStore
import ru.zona.app.ui.common.ZonaTopBar

@Composable
fun TeachersScreen(
    store: TeachersStore,
    onOpenChat: (Long, String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by store.collectState { eff -> when (eff) { is ru.zona.app.feature.teacher.TeachersEffect.Message -> onMessage(eff.text) } }
    LaunchedEffect(Unit) { store.dispatch(TeachersIntent.Load) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Преподаватели", "Найдите наставника для своей миссии")
        when {
            state.loading -> LoadingState()
            state.error != null -> MessageState("Ошибка", state.error!!, actionText = "Повторить", onAction = { store.dispatch(TeachersIntent.Load) })
            state.teachers.isEmpty() -> MessageState("Пока пусто", "Преподаватели появятся после модерации заявок")
            else ->
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.teachers, key = { it.id }) { t -> TeacherCard(t) { onOpenChat(t.id, t.displayName) } }
                }
        }
    }
}

@Composable
private fun TeacherCard(t: TeacherDto, onChat: () -> Unit) {
    ZonaCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("🧑‍🚀", style = MaterialTheme.typography.headlineMedium)
                Column(Modifier.weight(1f)) {
                    Text(t.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (t.headline.isNotBlank()) Text(t.headline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                t.ratingAvg?.let { ZonaBadge("★ ${(it * 10).toInt() / 10.0}") }
            }
            if (t.bio.isNotBlank()) Text(t.bio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            t.pricePerHourCents?.let { ZonaBadge("${formatPrice(it)}/час") }
            ZonaSecondaryButton("Написать", onClick = onChat)
        }
    }
}

@Composable
fun ApplicationScreen(
    store: ApplicationStore,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by store.collectState { eff -> when (eff) { is ru.zona.app.feature.teacher.ApplicationEffect.Message -> onMessage(eff.text) } }
    LaunchedEffect(Unit) { store.dispatch(ApplicationIntent.Load) }

    val pickFile = ru.zona.app.core.media.rememberFilePicker { picked ->
        if (picked != null) store.dispatch(ApplicationIntent.AttachFile(picked.name))
    }

    Column(Modifier.fillMaxSize()) {
        ZonaTopBar(title = "Стать преподавателем", onBack = onBack)
        when {
            state.loading -> LoadingState()
            else ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val app = state.application
                    if (app != null && app.status != "REJECTED" && app.status != "NEED_INFO") {
                        ZonaCard(Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Статус заявки", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ZonaBadge(statusLabel(app.status), content = MaterialTheme.colorScheme.secondary)
                                Text(app.motivation, style = MaterialTheme.typography.bodyMedium)
                                if (app.documents.isNotEmpty()) {
                                    Text("Документы:", style = MaterialTheme.typography.labelMedium)
                                    app.documents.forEach { Text("• ${it.fileName}", style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                        Text("Дождитесь решения администратора.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        app?.adminMessage?.let {
                            ZonaCard(Modifier.fillMaxWidth()) { Text("Комментарий: $it", color = MaterialTheme.colorScheme.error) }
                        }
                        Text("Расскажите о своём опыте и приложите документы (дипломы, сертификаты).", style = MaterialTheme.typography.bodyMedium)
                        ZonaTextField(state.headline, { store.dispatch(ApplicationIntent.SetHeadline(it)) }, "Кратко о себе (например: «Преподаватель английского, 5 лет»)")
                        ZonaTextField(state.motivation, { store.dispatch(ApplicationIntent.SetMotivation(it)) }, "Мотивация и опыт", singleLine = false, minLines = 4)

                        ZonaCard(Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Документы", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                if (state.attachedFiles.isEmpty()) {
                                    Text("Файлы не прикреплены", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    state.attachedFiles.forEach { fileName ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("📎 $fileName", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                            androidx.compose.material3.TextButton(onClick = { store.dispatch(ApplicationIntent.RemoveFile(fileName)) }) {
                                                Text("Удалить", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                                ZonaSecondaryButton("📎 Прикрепить файл") { pickFile() }
                            }
                        }

                        ZonaPrimaryButton(if (state.submitting) "Отправляем…" else "Отправить заявку", enabled = !state.submitting && state.motivation.isNotBlank()) {
                            store.dispatch(ApplicationIntent.Submit)
                        }
                    }
                }
        }
    }
}

@Composable
fun AdminScreen(
    store: AdminStore,
    onMessage: (String) -> Unit,
) {
    val state by store.collectState { eff -> when (eff) { is ru.zona.app.feature.teacher.AdminEffect.Message -> onMessage(eff.text) } }
    LaunchedEffect(Unit) { store.dispatch(AdminIntent.Load) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Модерация заявок", "Заявки кандидатов в преподаватели")
        when {
            state.loading -> LoadingState()
            state.error != null -> MessageState("Ошибка", state.error!!, actionText = "Повторить", onAction = { store.dispatch(AdminIntent.Load) })
            state.applications.isEmpty() -> MessageState("Нет заявок", "Все заявки обработаны 🎉")
            else ->
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.applications, key = { it.id }) { app ->
                        ZonaCard(Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(app.userName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                if (app.headline.isNotBlank()) Text(app.headline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(app.motivation, style = MaterialTheme.typography.bodyMedium)
                                if (app.documents.isNotEmpty()) {
                                    Text("Документы:", style = MaterialTheme.typography.labelMedium)
                                    app.documents.forEach { Text("• ${it.fileName}", style = MaterialTheme.typography.bodySmall) }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                                        ZonaSecondaryButton("Отклонить") { store.dispatch(AdminIntent.Reject(app.id)) }
                                    }
                                    androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                                        ZonaPrimaryButton("Одобрить") { store.dispatch(AdminIntent.Approve(app.id)) }
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }
}

private fun statusLabel(s: String) = when (s) {
    "PENDING" -> "На рассмотрении"
    "APPROVED" -> "Одобрена"
    "REJECTED" -> "Отклонена"
    "NEED_INFO" -> "Нужны уточнения"
    else -> s
}
