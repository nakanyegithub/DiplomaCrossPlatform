package ru.zona.app.feature.learning.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.zona.app.core.mvi.MviStore
import ru.zona.app.core.result.Outcome
import ru.zona.app.feature.learning.data.ExerciseDto
import ru.zona.app.feature.learning.domain.LearningRepository

data class LessonState(
    val loading: Boolean = true,
    val exercises: List<ExerciseDto> = emptyList(),
    val index: Int = 0,
    val answer: String = "",
    val selectedChoices: Set<Int> = emptySet(),
    val checking: Boolean = false,
    val lastCorrect: Boolean? = null,
    val xpGained: Int = 0,
    val finished: Boolean = false,
    val error: String? = null,
) {
    val current: ExerciseDto? get() = exercises.getOrNull(index)
    val progress: Float get() = if (exercises.isEmpty()) 0f else index.toFloat() / exercises.size
}

sealed interface LessonIntent {
    data object Load : LessonIntent
    data class SetAnswer(val text: String) : LessonIntent
    data class ToggleChoice(val idx: Int) : LessonIntent
    data object Check : LessonIntent
    data object Next : LessonIntent
}

sealed interface LessonEffect {
    data class Message(val text: String) : LessonEffect
}

class LessonStore(
    private val lessonId: Long,
    private val repo: LearningRepository,
    scope: CoroutineScope,
) : MviStore<LessonState, LessonIntent, LessonEffect>(LessonState(), scope) {

    override fun onIntent(intent: LessonIntent) {
        when (intent) {
            LessonIntent.Load -> load()
            is LessonIntent.SetAnswer -> setState { it.copy(answer = intent.text) }
            is LessonIntent.ToggleChoice ->
                setState {
                    val ex = it.current
                    if (ex?.type == "SINGLE_CHOICE") {
                        it.copy(selectedChoices = setOf(intent.idx))
                    } else {
                        val s = it.selectedChoices.toMutableSet()
                        if (!s.add(intent.idx)) s.remove(intent.idx)
                        it.copy(selectedChoices = s)
                    }
                }
            LessonIntent.Check -> check()
            LessonIntent.Next -> next()
        }
    }

    private fun load() {
        setState { it.copy(loading = true, error = null) }
        scope.launch {
            when (val r = repo.exercises(lessonId)) {
                is Outcome.Success -> setState { LessonState(loading = false, exercises = r.data, finished = r.data.isEmpty()) }
                is Outcome.Failure -> setState { it.copy(loading = false, error = r.message) }
            }
        }
    }

    private fun check() {
        val st = currentState
        val ex = st.current ?: return
        if (st.checking) return
        val answer =
            when (ex.type) {
                "TEXT_INPUT" -> st.answer
                "MULTI_CHOICE" -> st.selectedChoices.sorted().joinToString(",")
                else -> st.selectedChoices.firstOrNull()?.toString() ?: ""
            }
        if (answer.isBlank()) return
        setState { it.copy(checking = true) }
        scope.launch {
            when (val r = repo.submit(ex.id, answer)) {
                is Outcome.Success -> {
                    setState { it.copy(checking = false, lastCorrect = r.data.correct, xpGained = it.xpGained + r.data.xpEarned) }
                    if (r.data.xpEarned > 0) emit(LessonEffect.Message("+${r.data.xpEarned} XP ⭐"))
                }
                is Outcome.Failure -> {
                    setState { it.copy(checking = false) }
                    emit(LessonEffect.Message(r.message))
                }
            }
        }
    }

    private fun next() {
        setState {
            val nextIdx = it.index + 1
            if (nextIdx >= it.exercises.size) {
                it.copy(finished = true, lastCorrect = null)
            } else {
                it.copy(index = nextIdx, answer = "", selectedChoices = emptySet(), lastCorrect = null)
            }
        }
    }
}
