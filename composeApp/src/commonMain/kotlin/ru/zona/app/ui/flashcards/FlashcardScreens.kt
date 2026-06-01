package ru.zona.app.ui.flashcards

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.zona.app.core.design.LoadingState
import ru.zona.app.core.design.MessageState
import ru.zona.app.core.design.ScreenHeader
import ru.zona.app.core.design.ZonaBadge
import ru.zona.app.core.design.ZonaCard
import ru.zona.app.core.design.ZonaPrimaryButton
import ru.zona.app.core.design.ZonaSecondaryButton
import ru.zona.app.core.mvi.collectState
import ru.zona.app.feature.flashcards.DeckDto
import ru.zona.app.feature.flashcards.DecksIntent
import ru.zona.app.feature.flashcards.DecksStore
import ru.zona.app.feature.flashcards.StudyIntent
import ru.zona.app.feature.flashcards.StudyStore
import ru.zona.app.ui.common.ZonaTopBar

@Composable
fun DecksScreen(
    store: DecksStore,
    onOpenDeck: (DeckDto) -> Unit,
) {
    val state by store.collectState()
    LaunchedEffect(Unit) { store.dispatch(DecksIntent.Load) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Карточки", "Запоминай слова интервальными повторениями")
        when {
            state.loading -> LoadingState()
            state.error != null -> MessageState("Ошибка", state.error!!, actionText = "Повторить", onAction = { store.dispatch(DecksIntent.Load) })
            state.decks.isEmpty() -> MessageState("Пока нет колод", "Колоды появятся вместе с курсами")
            else ->
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.decks, key = { it.id }) { deck ->
                        ZonaCard(Modifier.fillMaxWidth(), onClick = { onOpenDeck(deck) }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("🃏", style = MaterialTheme.typography.headlineMedium)
                                Column(Modifier.weight(1f)) {
                                    Text(deck.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("${deck.cardCount} карточек", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (deck.dueCount > 0) ZonaBadge("${deck.dueCount} к повтору", content = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
        }
    }
}

@Composable
fun StudyScreen(
    title: String,
    store: StudyStore,
    onBack: () -> Unit,
) {
    val state by store.collectState()
    LaunchedEffect(Unit) { store.dispatch(StudyIntent.Load) }

    Column(Modifier.fillMaxSize()) {
        ZonaTopBar(title = title, onBack = onBack)
        when {
            state.loading -> LoadingState()
            state.error != null -> MessageState("Ошибка", state.error!!)
            state.finished -> MessageState("Колода пройдена! 🌟", "Возвращайтесь позже для повторения", actionText = "Назад", onAction = onBack)
            else -> {
                val card = state.current
                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Карточка ${state.index + 1} из ${state.cards.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (card != null) {
                        ZonaCard(Modifier.fillMaxWidth(), onClick = { store.dispatch(StudyIntent.Flip) }) {
                            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                                AnimatedContent(targetState = state.flipped, label = "flip") { flipped ->
                                    Text(
                                        if (flipped) card.back else card.front,
                                        style = MaterialTheme.typography.headlineMedium,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        Text(
                            if (state.flipped) "Помните перевод?" else "Нажмите на карточку, чтобы перевернуть",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (state.flipped) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.weight(1f)) { ZonaSecondaryButton("Не помню") { store.dispatch(StudyIntent.Answer(false)) } }
                                Box(Modifier.weight(1f)) { ZonaPrimaryButton("Помню ✓") { store.dispatch(StudyIntent.Answer(true)) } }
                            }
                        }
                    }
                }
            }
        }
    }
}
