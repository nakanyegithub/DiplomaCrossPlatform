package ru.zona.app.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.zona.app.core.design.LoadingState
import ru.zona.app.core.design.MessageState
import ru.zona.app.core.design.ZonaBadge
import ru.zona.app.core.design.ZonaCard
import ru.zona.app.core.design.ZonaPrimaryButton
import ru.zona.app.core.design.ZonaSecondaryButton
import ru.zona.app.core.mvi.collectState
import ru.zona.app.core.util.formatDateTime
import ru.zona.app.core.util.formatPrice
import ru.zona.app.feature.sessions.BookingRequestsEffect
import ru.zona.app.feature.sessions.BookingRequestsIntent
import ru.zona.app.feature.sessions.BookingRequestsStore
import ru.zona.app.ui.common.ZonaTopBar

@Composable
fun BookingRequestsScreen(
    store: BookingRequestsStore,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by store.collectState { eff -> when (eff) { is BookingRequestsEffect.Message -> onMessage(eff.text) } }
    LaunchedEffect(Unit) { store.dispatch(BookingRequestsIntent.Load) }

    Column(Modifier.fillMaxSize()) {
        ZonaTopBar("Заявки на занятия", onBack = onBack)
        when {
            state.loading -> LoadingState()
            state.error != null -> MessageState("Ошибка", state.error!!, actionText = "Повторить", onAction = { store.dispatch(BookingRequestsIntent.Load) })
            state.requests.isEmpty() -> MessageState("Нет заявок", "Когда ученик запишется на индивидуальное занятие — заявка появится здесь")
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.requests, key = { it.bookingId }) { r ->
                    ZonaCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(r.studentName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(r.sessionTitle, style = MaterialTheme.typography.bodyMedium)
                            Text("${formatDateTime(r.startsAt)} · ${r.durationMinutes} мин", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ZonaBadge(formatPrice(r.priceCents))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.weight(1f)) { ZonaSecondaryButton("Отклонить") { store.dispatch(BookingRequestsIntent.Decline(r.bookingId)) } }
                                Box(Modifier.weight(1f)) { ZonaPrimaryButton("Принять") { store.dispatch(BookingRequestsIntent.Accept(r.bookingId)) } }
                            }
                        }
                    }
                }
            }
        }
    }
}
