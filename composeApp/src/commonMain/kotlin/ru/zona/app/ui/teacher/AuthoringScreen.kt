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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import ru.zona.app.core.design.ScreenHeader
import ru.zona.app.core.design.ZonaBadge
import ru.zona.app.core.design.ZonaCard
import ru.zona.app.core.design.ZonaPrimaryButton
import ru.zona.app.core.design.ZonaSecondaryButton
import ru.zona.app.core.design.ZonaTextField
import ru.zona.app.core.media.CourseImage
import ru.zona.app.core.media.rememberImagePicker
import ru.zona.app.core.mvi.collectState
import ru.zona.app.core.util.formatPrice
import ru.zona.app.feature.learning.presentation.AuthoringEffect
import ru.zona.app.feature.learning.presentation.AuthoringIntent
import ru.zona.app.feature.learning.presentation.AuthoringStore

@Composable
fun AuthoringScreen(
    store: AuthoringStore,
    onEditCourse: (Long, String) -> Unit,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by store.collectState { eff -> when (eff) { is AuthoringEffect.Message -> onMessage(eff.text) } }
    LaunchedEffect(Unit) { store.dispatch(AuthoringIntent.Load) }
    val pickPhoto = rememberImagePicker { picked -> if (picked != null) store.dispatch(AuthoringIntent.AddPhoto(picked)) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ru.zona.app.ui.common.ZonaTopBar("Мои курсы", onBack = onBack)
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

                    Text("Фотографии курса (до 5)", style = MaterialTheme.typography.labelMedium)
                    if (state.gallery.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.gallery) { photo ->
                                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                    CourseImage(base64 = photo, width = 120.dp, height = 84.dp)
                                    androidx.compose.material3.TextButton(onClick = { store.dispatch(AuthoringIntent.RemovePhoto(photo)) }) {
                                        Text("Убрать", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                    if (state.gallery.size < 5) {
                        ZonaSecondaryButton("🖼 Добавить фото") { pickPhoto() }
                    }

                    ZonaPrimaryButton(if (state.saving) "Создаём…" else "Создать курс", enabled = !state.saving && state.title.isNotBlank()) {
                        store.dispatch(AuthoringIntent.Create)
                    }
                }
            }
            if (state.myCourses.isNotEmpty()) {
                Text("Мои курсы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                state.myCourses.forEach { c ->
                    ZonaCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(c.coverEmoji, style = MaterialTheme.typography.headlineSmall)
                                Column(Modifier.weight(1f)) {
                                    Text(c.title, style = MaterialTheme.typography.titleSmall)
                                    Text("${c.lessonCount} уроков", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                ZonaBadge(formatPrice(c.priceCents))
                            }
                            ZonaPrimaryButton("📚 Программа курса (уроки и задания)") { onEditCourse(c.id, c.title) }
                            ru.zona.app.core.design.ZonaSecondaryButton("🗑 Удалить курс") { store.dispatch(AuthoringIntent.DeleteCourse(c.id)) }
                        }
                    }
                }
            }
        }
    }
}
