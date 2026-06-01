package ru.zona.app.ui.learning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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
import ru.zona.app.core.design.ZonaBadge
import ru.zona.app.core.design.ZonaCard
import ru.zona.app.core.design.ZonaPrimaryButton
import ru.zona.app.core.mvi.collectState
import ru.zona.app.core.util.formatPrice
import ru.zona.app.feature.learning.data.LessonDto
import ru.zona.app.feature.learning.presentation.CourseDetailEffect
import ru.zona.app.feature.learning.presentation.CourseDetailIntent
import ru.zona.app.feature.learning.presentation.CourseDetailStore
import ru.zona.app.ui.common.ZonaTopBar

@Composable
fun CourseDetailScreen(
    store: CourseDetailStore,
    onBack: () -> Unit,
    onOpenLesson: (LessonDto) -> Unit,
    onOpenTeacher: (Long, String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by store.collectState { eff ->
        when (eff) {
            is CourseDetailEffect.Message -> onMessage(eff.text)
            CourseDetailEffect.Changed -> Unit
        }
    }
    LaunchedEffect(Unit) { store.dispatch(CourseDetailIntent.Load) }

    Column(Modifier.fillMaxSize()) {
        ZonaTopBar(title = state.course?.title ?: "Курс", onBack = onBack)
        when {
            state.loading -> LoadingState()
            state.error != null -> MessageState("Ошибка", state.error!!, actionText = "Повторить", onAction = { store.dispatch(CourseDetailIntent.Load) })
            state.course != null -> {
                val course = state.course!!
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        ZonaCard(Modifier.fillMaxWidth()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(course.coverEmoji, style = MaterialTheme.typography.displayMedium)
                                Text(course.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(course.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (course.gallery.isNotEmpty()) {
                                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(course.gallery) { photo ->
                                            ru.zona.app.core.media.CourseImage(base64 = photo, width = 200.dp, height = 130.dp)
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ZonaBadge("${course.languageTo} · ${course.level}")
                                    ZonaBadge(formatPrice(course.priceCents))
                                }
                                androidx.compose.material3.TextButton(onClick = { onOpenTeacher(course.teacherId, course.teacherName) }) {
                                    Text("👤 Преподаватель: ${course.teacherName} ›", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                if (!course.enrolled) {
                                    ZonaPrimaryButton(
                                        text = if (state.enrolling) "Записываем…" else "Записаться · ${formatPrice(course.priceCents)}",
                                        enabled = !state.enrolling,
                                    ) { store.dispatch(CourseDetailIntent.Enroll) }
                                } else {
                                    ZonaBadge("Вы записаны ✓", content = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }
                    }
                    item { Text("Программа курса", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    items(state.lessons, key = { it.id }) { lesson ->
                        LessonRow(lesson, enrolled = course.enrolled || course.teacherId == course.teacherId && course.enrolled) {
                            if (course.enrolled) onOpenLesson(lesson) else onMessage("Сначала запишитесь на курс")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonRow(lesson: LessonDto, enrolled: Boolean, onClick: () -> Unit) {
    ZonaCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val icon = when {
                lesson.completed -> Icons.Default.CheckCircle
                else -> Icons.Default.PlayArrow
            }
            Icon(icon, null, tint = if (lesson.completed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(lesson.title, style = MaterialTheme.typography.titleSmall)
                Text("${lesson.exerciseCount} упражнений", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (lesson.completed) ZonaBadge("Готово", content = MaterialTheme.colorScheme.secondary)
        }
    }
}
