package ru.zona.app.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import ru.zona.app.core.design.ZonaPrimaryButton
import ru.zona.app.core.mvi.collectState
import org.jetbrains.compose.resources.stringResource
import zona.resources.Res
import zona.resources.action_retry
import zona.resources.session_book
import zona.resources.session_send_request
import zona.resources.sessions_booked
import zona.resources.sessions_create
import zona.resources.sessions_delete
import zona.resources.sessions_empty_all
import zona.resources.sessions_empty_mine
import zona.resources.sessions_pending
import zona.resources.sessions_requests
import zona.resources.sessions_subtitle
import zona.resources.sessions_tab_mine
import zona.resources.sessions_tab_upcoming
import zona.resources.sessions_title
import zona.resources.state_empty
import zona.resources.state_error
import ru.zona.app.core.util.formatDateTime
import ru.zona.app.core.util.formatPrice
import ru.zona.app.feature.sessions.SessionDto
import ru.zona.app.feature.sessions.SessionsEffect
import ru.zona.app.feature.sessions.SessionsIntent
import ru.zona.app.feature.sessions.SessionsStore
import ru.zona.app.feature.sessions.SessionsTab

@Composable
fun SessionsScreen(
    store: SessionsStore,
    canCreate: Boolean,
    currentUserId: Long,
    onCreate: () -> Unit,
    onOpenRequests: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by store.collectState { eff -> when (eff) { is SessionsEffect.Message -> onMessage(eff.text) } }
    LaunchedEffect(Unit) { store.dispatch(SessionsIntent.Load) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            org.jetbrains.compose.resources.stringResource(Res.string.sessions_title),
            org.jetbrains.compose.resources.stringResource(Res.string.sessions_subtitle),
        )
        if (canCreate) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ZonaPrimaryButton(stringResource(Res.string.sessions_create), onClick = onCreate)
                ru.zona.app.core.design.ZonaSecondaryButton(stringResource(Res.string.sessions_requests), onClick = onOpenRequests)
            }
        }
        TabRow(selectedTabIndex = if (state.tab == SessionsTab.Upcoming) 0 else 1, containerColor = MaterialTheme.colorScheme.background) {
            Tab(selected = state.tab == SessionsTab.Upcoming, onClick = { store.dispatch(SessionsIntent.SetTab(SessionsTab.Upcoming)) }, text = { Text(stringResource(Res.string.sessions_tab_upcoming)) })
            Tab(selected = state.tab == SessionsTab.Mine, onClick = { store.dispatch(SessionsIntent.SetTab(SessionsTab.Mine)) }, text = { Text(stringResource(Res.string.sessions_tab_mine)) })
        }
        when {
            state.loading -> LoadingState()
            state.error != null -> MessageState(stringResource(Res.string.state_error), state.error!!, actionText = stringResource(Res.string.action_retry), onAction = { store.dispatch(SessionsIntent.Load) })
            state.sessions.isEmpty() -> MessageState(stringResource(Res.string.state_empty), stringResource(if (state.tab == SessionsTab.Mine) Res.string.sessions_empty_mine else Res.string.sessions_empty_all))
            else ->
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.sessions, key = { it.id }) { s ->
                        SessionCard(
                            s = s,
                            mine = s.teacherId == currentUserId,
                            onBook = { store.dispatch(SessionsIntent.Book(s)) },
                            onDelete = { store.dispatch(SessionsIntent.Delete(s.id)) },
                        )
                    }
                }
        }
    }
}

@Composable
private fun SessionCard(s: SessionDto, mine: Boolean, onBook: () -> Unit, onDelete: () -> Unit) {
    ZonaCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (s.type == "GROUP") "👥" else "👤", style = MaterialTheme.typography.titleLarge)
                Text(s.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            if (s.description.isNotBlank()) Text(s.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${s.teacherName} · ${formatDateTime(s.startsAt)} · ${s.durationMinutes} мин", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ZonaBadge(if (s.type == "GROUP") "Группа ${s.bookedCount}/${s.capacity}" else "Индивидуально")
                ZonaBadge(formatPrice(s.priceCents))
            }
            when {
                mine -> ru.zona.app.core.design.ZonaSecondaryButton(stringResource(Res.string.sessions_delete), onClick = onDelete)
                s.myStatus == "BOOKED" -> ZonaBadge(stringResource(Res.string.sessions_booked), content = MaterialTheme.colorScheme.secondary)
                s.myStatus == "PENDING" -> ZonaBadge(stringResource(Res.string.sessions_pending), content = MaterialTheme.colorScheme.tertiary)
                else -> ZonaPrimaryButton(
                    (if (s.type == "INDIVIDUAL") stringResource(Res.string.session_send_request) else stringResource(Res.string.session_book)) + " · " + formatPrice(s.priceCents),
                    onClick = onBook,
                )
            }
        }
    }
}
