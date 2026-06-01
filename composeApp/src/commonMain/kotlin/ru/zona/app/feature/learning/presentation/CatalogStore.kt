package ru.zona.app.feature.learning.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.zona.app.core.mvi.MviStore
import ru.zona.app.core.result.Outcome
import ru.zona.app.feature.learning.data.CourseDto
import ru.zona.app.feature.learning.domain.LearningRepository

enum class CatalogTab { All, Mine }

data class CatalogState(
    val tab: CatalogTab = CatalogTab.All,
    val query: String = "",
    val loading: Boolean = true,
    val courses: List<CourseDto> = emptyList(),
    val error: String? = null,
)

sealed interface CatalogIntent {
    data object Load : CatalogIntent
    data class SetTab(val tab: CatalogTab) : CatalogIntent
    data class SetQuery(val query: String) : CatalogIntent
}

class CatalogStore(
    private val repo: LearningRepository,
    scope: CoroutineScope,
) : MviStore<CatalogState, CatalogIntent, Unit>(CatalogState(), scope) {

    override fun onIntent(intent: CatalogIntent) {
        when (intent) {
            CatalogIntent.Load -> load()
            is CatalogIntent.SetTab -> { setState { it.copy(tab = intent.tab) }; load() }
            is CatalogIntent.SetQuery -> { setState { it.copy(query = intent.query) }; load() }
        }
    }

    private fun load() {
        setState { it.copy(loading = true, error = null) }
        scope.launch {
            val st = currentState
            val result =
                if (st.tab == CatalogTab.Mine) repo.myCourses() else repo.catalog(st.query.ifBlank { null })
            when (result) {
                is Outcome.Success -> setState { it.copy(loading = false, courses = result.data) }
                is Outcome.Failure -> setState { it.copy(loading = false, error = result.message) }
            }
        }
    }
}
