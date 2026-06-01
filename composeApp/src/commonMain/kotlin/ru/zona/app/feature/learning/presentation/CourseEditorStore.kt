package ru.zona.app.feature.learning.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.zona.app.core.mvi.MviStore
import ru.zona.app.core.result.Outcome
import ru.zona.app.feature.learning.data.CreateExerciseRequest
import ru.zona.app.feature.learning.data.LessonDto
import ru.zona.app.feature.learning.domain.LearningRepository

data class CourseEditorState(
    val loading: Boolean = true,
    val lessons: List<LessonDto> = emptyList(),
    val newLessonTitle: String = "",
    // exercise draft
    val selectedLessonId: Long? = null,
    val exType: String = "SINGLE_CHOICE",
    val prompt: String = "",
    val choices: List<String> = listOf("", ""),
    val correctIndex: Int = 0,
    val correctText: String = "",
    val xp: Int = 10,
    val saving: Boolean = false,
    val error: String? = null,
)

sealed interface CourseEditorIntent {
    data object Load : CourseEditorIntent
    data class SetLessonTitle(val v: String) : CourseEditorIntent
    data object AddLesson : CourseEditorIntent
    data class SelectLesson(val id: Long?) : CourseEditorIntent
    data class SetType(val v: String) : CourseEditorIntent
    data class SetPrompt(val v: String) : CourseEditorIntent
    data class SetChoice(val index: Int, val v: String) : CourseEditorIntent
    data object AddChoice : CourseEditorIntent
    data class SetCorrectIndex(val i: Int) : CourseEditorIntent
    data class ToggleCorrectIndex(val i: Int) : CourseEditorIntent
    data class SetCorrectText(val v: String) : CourseEditorIntent
    data object AddExercise : CourseEditorIntent
    data class DeleteLesson(val lessonId: Long) : CourseEditorIntent
    data class DeleteExercise(val exerciseId: Long) : CourseEditorIntent
}

sealed interface CourseEditorEffect { data class Message(val text: String) : CourseEditorEffect }

class CourseEditorStore(
    private val courseId: Long,
    private val repo: LearningRepository,
    scope: CoroutineScope,
) : MviStore<CourseEditorState, CourseEditorIntent, CourseEditorEffect>(CourseEditorState(), scope) {

    override fun onIntent(intent: CourseEditorIntent) {
        when (intent) {
            CourseEditorIntent.Load -> load()
            is CourseEditorIntent.SetLessonTitle -> setState { it.copy(newLessonTitle = intent.v) }
            CourseEditorIntent.AddLesson -> addLesson()
            is CourseEditorIntent.SelectLesson -> setState { it.copy(selectedLessonId = intent.id) }
            is CourseEditorIntent.SetType -> setState { it.copy(exType = intent.v, correctIndex = 0) }
            is CourseEditorIntent.SetPrompt -> setState { it.copy(prompt = intent.v) }
            is CourseEditorIntent.SetChoice -> setState {
                it.copy(choices = it.choices.toMutableList().also { l -> l[intent.index] = intent.v })
            }
            CourseEditorIntent.AddChoice -> setState { it.copy(choices = it.choices + "") }
            is CourseEditorIntent.SetCorrectIndex -> setState { it.copy(correctIndex = intent.i) }
            is CourseEditorIntent.ToggleCorrectIndex -> setState {
                // для MULTI_CHOICE храним correctIndex как битовую маску? Проще — список в correctText
                val current = it.correctText.split(",").mapNotNull { s -> s.trim().toIntOrNull() }.toMutableSet()
                if (!current.add(intent.i)) current.remove(intent.i)
                it.copy(correctText = current.sorted().joinToString(","))
            }
            is CourseEditorIntent.SetCorrectText -> setState { it.copy(correctText = intent.v) }
            CourseEditorIntent.AddExercise -> addExercise()
            is CourseEditorIntent.DeleteLesson -> scope.launch {
                when (val r = repo.deleteLesson(intent.lessonId)) {
                    is Outcome.Success -> { emit(CourseEditorEffect.Message("Урок удалён")); load() }
                    is Outcome.Failure -> emit(CourseEditorEffect.Message(r.message))
                }
            }
            is CourseEditorIntent.DeleteExercise -> scope.launch {
                when (val r = repo.deleteExercise(intent.exerciseId)) {
                    is Outcome.Success -> { emit(CourseEditorEffect.Message("Упражнение удалено")); load() }
                    is Outcome.Failure -> emit(CourseEditorEffect.Message(r.message))
                }
            }
        }
    }

    private fun load() {
        setState { it.copy(loading = true, error = null) }
        scope.launch {
            when (val r = repo.detail(courseId)) {
                is Outcome.Success -> setState {
                    it.copy(loading = false, lessons = r.data.lessons, selectedLessonId = it.selectedLessonId ?: r.data.lessons.firstOrNull()?.id)
                }
                is Outcome.Failure -> setState { it.copy(loading = false, error = r.message) }
            }
        }
    }

    private fun addLesson() {
        val title = currentState.newLessonTitle.trim()
        if (title.isBlank() || currentState.saving) return
        setState { it.copy(saving = true) }
        scope.launch {
            when (val r = repo.addLesson(courseId, title)) {
                is Outcome.Success -> { setState { it.copy(saving = false, newLessonTitle = "") }; emit(CourseEditorEffect.Message("Урок добавлен")); load() }
                is Outcome.Failure -> { setState { it.copy(saving = false) }; emit(CourseEditorEffect.Message(r.message)) }
            }
        }
    }

    private fun addExercise() {
        val st = currentState
        val lessonId = st.selectedLessonId ?: run { emit(CourseEditorEffect.Message("Сначала выберите урок")); return }
        if (st.prompt.isBlank() || st.saving) return
        val (choices, correct) =
            when (st.exType) {
                "TEXT_INPUT" -> emptyList<String>() to st.correctText.trim()
                "MULTI_CHOICE" -> st.choices.filter { it.isNotBlank() } to st.correctText.ifBlank { "0" }
                else -> st.choices.filter { it.isNotBlank() } to st.correctIndex.toString()
            }
        if (st.exType != "TEXT_INPUT" && choices.size < 2) { emit(CourseEditorEffect.Message("Нужно минимум 2 варианта")); return }
        if (st.exType == "TEXT_INPUT" && correct.isBlank()) { emit(CourseEditorEffect.Message("Введите правильный ответ")); return }
        setState { it.copy(saving = true) }
        scope.launch {
            val req = CreateExerciseRequest(type = st.exType, prompt = st.prompt.trim(), choices = choices, correctAnswer = correct, xp = st.xp)
            when (val r = repo.addExercise(lessonId, req)) {
                is Outcome.Success -> {
                    setState { it.copy(saving = false, prompt = "", choices = listOf("", ""), correctIndex = 0, correctText = "") }
                    emit(CourseEditorEffect.Message("Упражнение добавлено (+${st.xp} XP)"))
                    load()
                }
                is Outcome.Failure -> { setState { it.copy(saving = false) }; emit(CourseEditorEffect.Message(r.message)) }
            }
        }
    }
}
