package ru.zona.app.ui.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
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
import ru.zona.app.core.design.ZonaCard
import ru.zona.app.core.design.ZonaPrimaryButton
import ru.zona.app.core.design.ZonaSecondaryButton
import ru.zona.app.core.design.ZonaTextField
import ru.zona.app.core.mvi.collectState
import ru.zona.app.feature.learning.presentation.LessonEffect
import ru.zona.app.feature.learning.presentation.LessonIntent
import ru.zona.app.feature.learning.presentation.LessonStore
import ru.zona.app.ui.common.ZonaTopBar

@Composable
fun LessonScreen(
    title: String,
    store: LessonStore,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by store.collectState { eff ->
        when (eff) { is LessonEffect.Message -> onMessage(eff.text) }
    }
    LaunchedEffect(Unit) { store.dispatch(LessonIntent.Load) }

    Column(Modifier.fillMaxSize()) {
        ZonaTopBar(title = title, onBack = onBack)
        when {
            state.loading -> LoadingState()
            state.error != null -> MessageState("Ошибка", state.error!!, actionText = "Повторить", onAction = { store.dispatch(LessonIntent.Load) })
            state.finished -> {
                MessageState(
                    title = "Урок пройден! 🌟",
                    message = "Вы заработали ${state.xpGained} XP. Так держать, космонавт!",
                    actionText = "Вернуться",
                    onAction = onBack,
                )
            }
            else -> {
                val ex = state.current
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (ex != null) {
                        Text("Вопрос ${state.index + 1} из ${state.exercises.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ZonaCard(Modifier.fillMaxWidth()) {
                            Text(ex.prompt, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        when (ex.type) {
                            "TEXT_INPUT" ->
                                ZonaTextField(state.answer, { store.dispatch(LessonIntent.SetAnswer(it)) }, "Ваш ответ")
                            else ->
                                ex.choices.forEachIndexed { idx, choice ->
                                    val selected = idx in state.selectedChoices
                                    ChoiceRow(choice, selected) { store.dispatch(LessonIntent.ToggleChoice(idx)) }
                                }
                        }
                        state.lastCorrect?.let { correct ->
                            Text(
                                if (correct) "Верно! ✅" else "Неверно, попробуйте ещё ❌",
                                color = if (correct) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        if (state.lastCorrect == true) {
                            ZonaPrimaryButton("Дальше") { store.dispatch(LessonIntent.Next) }
                        } else {
                            ZonaPrimaryButton(if (state.checking) "Проверяем…" else "Проверить", enabled = !state.checking) {
                                store.dispatch(LessonIntent.Check)
                            }
                            if (state.lastCorrect == false) {
                                ZonaSecondaryButton("Пропустить") { store.dispatch(LessonIntent.Next) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    ZonaCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (selected) "●" else "○", color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
