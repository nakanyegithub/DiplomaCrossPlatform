package ru.zona.app.feature.learning.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.zona.app.core.mvi.MviStore
import ru.zona.app.core.result.Outcome
import ru.zona.app.feature.learning.data.CourseDto
import ru.zona.app.feature.learning.data.LessonDto
import ru.zona.app.feature.learning.domain.LearningRepository

data class CourseDetailState(
    val loading: Boolean = true,
    val course: CourseDto? = null,
    val lessons: List<LessonDto> = emptyList(),
    val enrolling: Boolean = false,
    val error: String? = null,
)

sealed interface CourseDetailIntent {
    data object Load : CourseDetailIntent
    data object Enroll : CourseDetailIntent
}

sealed interface CourseDetailEffect {
    data class Message(val text: String) : CourseDetailEffect
    data object Changed : CourseDetailEffect
}

class CourseDetailStore(
    private val courseId: Long,
    private val repo: LearningRepository,
    scope: CoroutineScope,
) : MviStore<CourseDetailState, CourseDetailIntent, CourseDetailEffect>(CourseDetailState(), scope) {

    override fun onIntent(intent: CourseDetailIntent) {
        when (intent) {
            CourseDetailIntent.Load -> load()
            CourseDetailIntent.Enroll -> enroll()
        }
    }

    private fun load() {
        setState { it.copy(loading = true, error = null) }
        scope.launch {
            when (val r = repo.detail(courseId)) {
                is Outcome.Success -> setState { it.copy(loading = false, course = r.data.course, lessons = r.data.lessons) }
                is Outcome.Failure -> setState { it.copy(loading = false, error = r.message) }
            }
        }
    }

    private fun enroll() {
        if (currentState.enrolling) return
        setState { it.copy(enrolling = true) }
        scope.launch {
            when (val r = repo.enroll(courseId)) {
                is Outcome.Success -> {
                    setState { it.copy(enrolling = false, course = r.data) }
                    emit(CourseDetailEffect.Message("Вы записаны на курс 🚀"))
                    emit(CourseDetailEffect.Changed)
                    load()
                }
                is Outcome.Failure -> {
                    setState { it.copy(enrolling = false) }
                    emit(CourseDetailEffect.Message(r.message))
                }
            }
        }
    }
}
