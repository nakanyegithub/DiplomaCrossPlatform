package ru.zona.app.ui.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import ru.zona.app.core.design.ZonaTextField
import ru.zona.app.core.mvi.collectState
import ru.zona.app.core.util.formatPrice
import ru.zona.app.feature.learning.data.CourseDto
import ru.zona.app.feature.learning.presentation.CatalogIntent
import ru.zona.app.feature.learning.presentation.CatalogStore
import ru.zona.app.feature.learning.presentation.CatalogTab

@Composable
fun CatalogScreen(
    store: CatalogStore,
    onOpenCourse: (CourseDto) -> Unit,
) {
    val state by store.collectState()
    LaunchedEffect(Unit) { store.dispatch(CatalogIntent.Load) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Каталог курсов", "Выбери курс и отправляйся в путешествие")
        TabRow(selectedTabIndex = if (state.tab == CatalogTab.All) 0 else 1, containerColor = MaterialTheme.colorScheme.background) {
            Tab(selected = state.tab == CatalogTab.All, onClick = { store.dispatch(CatalogIntent.SetTab(CatalogTab.All)) }, text = { Text("Все курсы") })
            Tab(selected = state.tab == CatalogTab.Mine, onClick = { store.dispatch(CatalogIntent.SetTab(CatalogTab.Mine)) }, text = { Text("Мои курсы") })
        }
        if (state.tab == CatalogTab.All) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                ZonaTextField(state.query, { store.dispatch(CatalogIntent.SetQuery(it)) }, "Поиск курса или языка")
            }
        }
        when {
            state.loading -> LoadingState()
            state.error != null -> MessageState("Ошибка", state.error!!, actionText = "Повторить", onAction = { store.dispatch(CatalogIntent.Load) })
            state.courses.isEmpty() -> MessageState("Пусто", if (state.tab == CatalogTab.Mine) "Вы ещё не записались на курсы" else "Курсы не найдены")
            else ->
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.courses, key = { it.id }) { course -> CourseCard(course) { onOpenCourse(course) } }
                }
        }
    }
}

@Composable
fun CourseCard(course: CourseDto, onClick: () -> Unit) {
    ZonaCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(course.coverEmoji, style = MaterialTheme.typography.displaySmall)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(course.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${course.languageTo.ifBlank { "—" }} · ${course.level} · ${course.lessonCount} уроков",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ZonaBadge(formatPrice(course.priceCents))
                    if (course.enrolled) ZonaBadge("Записан", content = MaterialTheme.colorScheme.secondary)
                }
                if (course.enrolled && course.progressPercent > 0) {
                    LinearProgressIndicator(
                        progress = { course.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
