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
import ru.zona.app.core.design.ZonaTextField
import ru.zona.app.core.mvi.collectState
import org.jetbrains.compose.resources.stringResource
import zona.resources.Res
import zona.resources.action_retry
import zona.resources.cards_create_deck
import zona.resources.cards_deck_name
import zona.resources.cards_empty
import zona.resources.cards_new_deck
import zona.resources.cards_subtitle
import zona.resources.cards_title
import zona.resources.state_error
import ru.zona.app.feature.flashcards.DeckDto
import ru.zona.app.feature.flashcards.DecksIntent
import ru.zona.app.feature.flashcards.DecksStore
import ru.zona.app.feature.flashcards.ManageDeckIntent
import ru.zona.app.feature.flashcards.ManageDeckStore
import ru.zona.app.feature.flashcards.StudyIntent
import ru.zona.app.feature.flashcards.StudyStore
import ru.zona.app.ui.common.ZonaTopBar

@Composable
fun DecksScreen(
    store: DecksStore,
    canCreate: Boolean,
    onOpenDeck: (DeckDto) -> Unit,
    onManageDeck: (DeckDto) -> Unit,
) {
    val state by store.collectState()
    LaunchedEffect(Unit) { store.dispatch(DecksIntent.Load) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            stringResource(Res.string.cards_title),
            stringResource(Res.string.cards_subtitle),
        )
        if (canCreate) {
            ZonaCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(Res.string.cards_new_deck), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    ZonaTextField(state.newDeckTitle, { store.dispatch(DecksIntent.SetNewDeckTitle(it)) }, stringResource(Res.string.cards_deck_name))
                    ZonaPrimaryButton(stringResource(Res.string.cards_create_deck), enabled = state.newDeckTitle.isNotBlank()) {
                        store.dispatch(DecksIntent.Create)
                    }
                }
            }
        }
        when {
            state.loading -> LoadingState()
            state.error != null -> MessageState(stringResource(Res.string.state_error), state.error!!, actionText = stringResource(Res.string.action_retry), onAction = { store.dispatch(DecksIntent.Load) })
            state.decks.isEmpty() -> MessageState(stringResource(Res.string.cards_empty), if (canCreate) "Создайте первую колоду выше" else "Колоды появятся вместе с курсами")
            else ->
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.decks, key = { it.id }) { deck ->
                        ZonaCard(Modifier.fillMaxWidth(), onClick = { onOpenDeck(deck) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("🃏", style = MaterialTheme.typography.headlineMedium)
                                    Column(Modifier.weight(1f)) {
                                        Text(deck.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Text("${deck.cardCount} карточек", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (deck.dueCount > 0) ZonaBadge("${deck.dueCount} к повтору", content = MaterialTheme.colorScheme.secondary)
                                }
                                if (canCreate) {
                                    ZonaSecondaryButton("Добавить карточки") { onManageDeck(deck) }
                                }
                            }
                        }
                    }
                }
        }
    }
}

@Composable
fun ManageDeckScreen(
    title: String,
    store: ManageDeckStore,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by store.collectState { eff -> when (eff) { is ru.zona.app.feature.flashcards.ManageDeckEffect.Message -> onMessage(eff.text) } }
    LaunchedEffect(Unit) { store.dispatch(ManageDeckIntent.Load) }

    Column(Modifier.fillMaxSize()) {
        ZonaTopBar(title = "Колода: $title", onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ZonaCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Новая карточка", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        ZonaTextField(state.front, { store.dispatch(ManageDeckIntent.SetFront(it)) }, "Лицевая сторона (слово)")
                        ZonaTextField(state.back, { store.dispatch(ManageDeckIntent.SetBack(it)) }, "Оборот (перевод)")
                        ZonaPrimaryButton(if (state.saving) "Добавляем…" else "Добавить карточку", enabled = state.front.isNotBlank() && state.back.isNotBlank() && !state.saving) {
                            store.dispatch(ManageDeckIntent.AddCard)
                        }
                    }
                }
            }
            if (state.cards.isNotEmpty()) {
                item { Text("Карточки (${state.cards.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            }
            items(state.cards, key = { it.id }) { c ->
                ZonaCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(c.front, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(c.back, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
