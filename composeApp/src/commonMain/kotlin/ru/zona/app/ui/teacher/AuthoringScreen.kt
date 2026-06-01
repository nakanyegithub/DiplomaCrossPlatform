package ru.zona.app.ui.teacher

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
import ru.zona.app.core.design.ScreenHeader
import ru.zona.app.core.design.ZonaBadge
import ru.zona.app.core.design.ZonaCard
import ru.zona.app.core.design.ZonaPrimaryButton
import ru.zona.app.core.design.ZonaTextField
import ru.zona.app.core.mvi.collectState
import ru.zona.app.core.util.formatPrice
import ru.zona.app.feature.learning.presentation.AuthoringEffect
import ru.zona.app.feature.learning.presentation.AuthoringIntent
import ru.zona.app.feature.learning.presentation.AuthoringStore

@Composable
fun AuthoringScreen(
    store: AuthoringStore,
    onEditCourse: (Long, String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by store.collectState { eff -> when (eff) { is AuthoringEffect.Message -> onMessage(eff.text) } }
    LaunchedEffect(Unit) { store.dispatch(AuthoringIntent.Load) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Мои курсы", "Создавайте курсы и обучайте учеников")
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ZonaCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Новый курс", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    ZonaTextField(state.title, { store.dispatch(AuthoringIntent.SetTitle(it)) }, "Название курса")
                    ZonaTextField(state.description, { store.dispatch(AuthoringIntent.SetDescription(it)) }, "Описание", singleLine = false, minLines = 2)
                    ZonaTextField(state.languageTo, { store.dispatch(AuthoringIntent.SetLanguageTo(it)) }, "Язык (English, Español…)")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) { ZonaTextField(state.level, { store.dispatch(AuthoringIntent.SetLevel(it)) }, "Уровень (A1)") }
                        Column(Modifier.weight(1f)) { ZonaTextField(state.emoji, { store.dispatch(AuthoringIntent.SetEmoji(it)) }, "Эмодзи") }
                    }
                    ZonaTextField(state.priceText, { store.dispatch(AuthoringIntent.SetPrice(it)) }, "Цена в ₵ (пусто = бесплатно)")
                    ZonaPrimaryButton(if (state.saving) "Создаём…" else "Создать курс", enabled = !state.saving && state.title.isNotBlank()) {
                        store.dispatch(AuthoringIntent.Create)
                    }
                }
            }
            if (state.myCourses.isNotEmpty()) {
                Text("Созданные курсы (нажмите, чтобы добавить уроки)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                state.myCourses.forEach { c ->
                    ZonaCard(Modifier.fillMaxWidth(), onClick = { onEditCourse(c.id, c.title) }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(c.coverEmoji, style = MaterialTheme.typography.headlineSmall)
                            Column(Modifier.weight(1f)) {
                                Text(c.title, style = MaterialTheme.typography.titleSmall)
                                Text("${c.lessonCount} уроков · нажмите для редактирования", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            ZonaBadge(formatPrice(c.priceCents))
                        }
                    }
                }
            }
        }
    }
}
