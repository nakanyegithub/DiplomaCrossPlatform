package ru.zona.app.feature.learning.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.zona.app.core.mvi.MviStore
import ru.zona.app.core.result.Outcome
import ru.zona.app.feature.learning.data.CourseDto
import ru.zona.app.feature.learning.data.CreateCourseRequest
import ru.zona.app.feature.learning.domain.LearningRepository

data class AuthoringState(
    val title: String = "",
    val description: String = "",
    val languageTo: String = "",
    val level: String = "A1",
    val priceText: String = "",
    val emoji: String = "🚀",
    val saving: Boolean = false,
    val myCourses: List<CourseDto> = emptyList(),
)

sealed interface AuthoringIntent {
    data object Load : AuthoringIntent
    data class SetTitle(val v: String) : AuthoringIntent
    data class SetDescription(val v: String) : AuthoringIntent
    data class SetLanguageTo(val v: String) : AuthoringIntent
    data class SetLevel(val v: String) : AuthoringIntent
    data class SetPrice(val v: String) : AuthoringIntent
    data class SetEmoji(val v: String) : AuthoringIntent
    data object Create : AuthoringIntent
}

sealed interface AuthoringEffect {
    data class Message(val text: String) : AuthoringEffect
}

class AuthoringStore(
    private val repo: LearningRepository,
    scope: CoroutineScope,
) : MviStore<AuthoringState, AuthoringIntent, AuthoringEffect>(AuthoringState(), scope) {

    override fun onIntent(intent: AuthoringIntent) {
        when (intent) {
            AuthoringIntent.Load -> load()
            is AuthoringIntent.SetTitle -> setState { it.copy(title = intent.v) }
            is AuthoringIntent.SetDescription -> setState { it.copy(description = intent.v) }
            is AuthoringIntent.SetLanguageTo -> setState { it.copy(languageTo = intent.v) }
            is AuthoringIntent.SetLevel -> setState { it.copy(level = intent.v) }
            is AuthoringIntent.SetPrice -> setState { it.copy(priceText = intent.v.filter { c -> c.isDigit() }) }
            is AuthoringIntent.SetEmoji -> setState { it.copy(emoji = intent.v.take(2).ifBlank { "🚀" }) }
            AuthoringIntent.Create -> create()
        }
    }

    private fun load() {
        scope.launch {
            when (val r = repo.myCourses()) {
                is Outcome.Success -> setState { it.copy(myCourses = r.data) }
                is Outcome.Failure -> emit(AuthoringEffect.Message(r.message))
            }
        }
    }

    private fun create() {
        val st = currentState
        if (st.saving || st.title.isBlank()) return
        setState { it.copy(saving = true) }
        scope.launch {
            val priceCents = st.priceText.toLongOrNull()?.let { it * 100 }
            val req =
                CreateCourseRequest(
                    title = st.title.trim(),
                    description = st.description.trim(),
                    languageTo = st.languageTo.trim(),
                    level = st.level,
                    priceCents = priceCents,
                    coverEmoji = st.emoji,
                )
            when (val r = repo.createCourse(req)) {
                is Outcome.Success -> {
                    setState { AuthoringState(myCourses = it.myCourses) }
                    emit(AuthoringEffect.Message("Курс «${r.data.title}» создан 🚀"))
                    load()
                }
                is Outcome.Failure -> {
                    setState { it.copy(saving = false) }
                    emit(AuthoringEffect.Message(r.message))
                }
            }
        }
    }
}
