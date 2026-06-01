package ru.zona.app.ui.teacher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.zona.app.core.design.LoadingState
import ru.zona.app.core.design.ZonaBadge
import ru.zona.app.core.design.ZonaCard
import ru.zona.app.core.design.ZonaPrimaryButton
import ru.zona.app.core.design.ZonaSecondaryButton
import ru.zona.app.core.design.ZonaTextField
import ru.zona.app.core.mvi.collectState
import ru.zona.app.feature.learning.presentation.CourseEditorEffect
import ru.zona.app.feature.learning.presentation.CourseEditorIntent
import ru.zona.app.feature.learning.presentation.CourseEditorStore
import ru.zona.app.ui.common.ZonaTopBar

private val EX_TYPES = listOf(
    "SINGLE_CHOICE" to "Один вариант",
    "MULTI_CHOICE" to "Несколько",
    "TEXT_INPUT" to "Ввод текста",
)

@Composable
fun CourseEditorScreen(
    title: String,
    store: CourseEditorStore,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by store.collectState { eff -> when (eff) { is CourseEditorEffect.Message -> onMessage(eff.text) } }
    LaunchedEffect(Unit) { store.dispatch(CourseEditorIntent.Load) }

    Column(Modifier.fillMaxSize()) {
        ZonaTopBar(title = "Редактор: $title", onBack = onBack)
        if (state.loading) {
            LoadingState()
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ZonaCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Новый урок", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        ZonaTextField(state.newLessonTitle, { store.dispatch(CourseEditorIntent.SetLessonTitle(it)) }, "Название урока")
                        ZonaPrimaryButton("Добавить урок", enabled = state.newLessonTitle.isNotBlank() && !state.saving) {
                            store.dispatch(CourseEditorIntent.AddLesson)
                        }
                    }
                }
            }

            if (state.lessons.isEmpty()) {
                item { Text("Добавьте первый урок, затем упражнения к нему.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                item { Text("Уроки (выберите, чтобы добавить упражнение)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                items(state.lessons, key = { it.id }) { lesson ->
                    val selected = state.selectedLessonId == lesson.id
                    ZonaCard(Modifier.fillMaxWidth(), onClick = { store.dispatch(CourseEditorIntent.SelectLesson(lesson.id)) }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(if (selected) "🟢" else "⚪", style = MaterialTheme.typography.titleMedium)
                            Column(Modifier.weight(1f)) {
                                Text(lesson.title, style = MaterialTheme.typography.titleSmall)
                                Text("${lesson.exerciseCount} упражнений", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selected) ZonaBadge("выбран")
                        }
                    }
                }

                item {
                    ZonaCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Новое упражнение", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                EX_TYPES.forEach { (id, label) ->
                                    Box(Modifier.weight(1f)) {
                                        if (state.exType == id) {
                                            ZonaPrimaryButton(label) {}
                                        } else {
                                            ZonaSecondaryButton(label) { store.dispatch(CourseEditorIntent.SetType(id)) }
                                        }
                                    }
                                }
                            }
                            ZonaTextField(state.prompt, { store.dispatch(CourseEditorIntent.SetPrompt(it)) }, "Вопрос / задание")

                            if (state.exType == "TEXT_INPUT") {
                                ZonaTextField(state.correctText, { store.dispatch(CourseEditorIntent.SetCorrectText(it)) }, "Правильный ответ")
                            } else {
                                val correctSet =
                                    if (state.exType == "MULTI_CHOICE")
                                        state.correctText.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                                    else setOf(state.correctIndex)
                                Text("Варианты (нажмите «верный», чтобы отметить):", style = MaterialTheme.typography.labelMedium)
                                state.choices.forEachIndexed { idx, choice ->
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(Modifier.weight(1f)) {
                                            ZonaTextField(choice, { store.dispatch(CourseEditorIntent.SetChoice(idx, it)) }, "Вариант ${idx + 1}")
                                        }
                                        TextButton(onClick = {
                                            if (state.exType == "MULTI_CHOICE") store.dispatch(CourseEditorIntent.ToggleCorrectIndex(idx))
                                            else store.dispatch(CourseEditorIntent.SetCorrectIndex(idx))
                                        }) {
                                            Text(if (idx in correctSet) "✅ верный" else "отметить", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                                ZonaSecondaryButton("+ Добавить вариант") { store.dispatch(CourseEditorIntent.AddChoice) }
                            }

                            ZonaPrimaryButton(if (state.saving) "Сохраняем…" else "Добавить упражнение", enabled = state.prompt.isNotBlank() && !state.saving) {
                                store.dispatch(CourseEditorIntent.AddExercise)
                            }
                        }
                    }
                }
            }
        }
    }
}
