package ru.zona.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import ru.zona.app.core.design.ZonaCard
import ru.zona.app.core.mvi.collectState
import zona.resources.Res
import zona.resources.chat_title
import ru.zona.app.feature.chat.ChatIntent
import ru.zona.app.feature.chat.ChatListIntent
import ru.zona.app.feature.chat.ChatListStore
import ru.zona.app.feature.chat.ChatStore
import ru.zona.app.feature.chat.ConversationDto
import ru.zona.app.ui.common.ZonaTopBar

@Composable
fun ChatListScreen(
    store: ChatListStore,
    onOpen: (ConversationDto) -> Unit,
) {
    val state by store.collectState()
    LaunchedEffect(Unit) { store.dispatch(ChatListIntent.Load) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            org.jetbrains.compose.resources.stringResource(Res.string.chat_title),
            "Общайтесь с преподавателями и учениками",
        )
        when {
            state.loading -> LoadingState()
            state.error != null -> MessageState("Ошибка", state.error!!, actionText = "Повторить", onAction = { store.dispatch(ChatListIntent.Load) })
            state.conversations.isEmpty() -> MessageState("Нет диалогов", "Начните чат со страницы преподавателя")
            else ->
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.conversations, key = { it.id }) { c ->
                        ZonaCard(Modifier.fillMaxWidth(), onClick = { onOpen(c) }) {
                            Column {
                                Text(c.peerName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(c.lastMessage ?: "Нет сообщений", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
        }
    }
}

@Composable
fun ChatScreen(
    peerName: String,
    currentUserId: Long,
    store: ChatStore,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    isGroup: Boolean = false,
) {
    val state by store.collectState { eff -> when (eff) { is ru.zona.app.feature.chat.ChatEffect.Message -> onMessage(eff.text) } }
    LaunchedEffect(Unit) { store.dispatch(ChatIntent.Load) }

    Column(Modifier.fillMaxSize()) {
        ZonaTopBar(title = peerName, onBack = onBack)
        when {
            state.loading -> LoadingState(Modifier.weight(1f))
            else ->
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.messages, key = { it.id }) { m ->
                        MessageRow(
                            m = m,
                            mine = m.senderId == currentUserId,
                            showSender = isGroup,
                            onReply = { store.dispatch(ChatIntent.StartReply(m)) },
                        )
                    }
                }
        }
        state.replyTo?.let { rep ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    Column {
                        Text("Ответ ${rep.senderName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(rep.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                IconButton(onClick = { store.dispatch(ChatIntent.CancelReply) }) {
                    Icon(Icons.Default.Close, contentDescription = "Отменить ответ")
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.draft,
                onValueChange = { store.dispatch(ChatIntent.SetDraft(it)) },
                placeholder = { Text("Сообщение…") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                maxLines = 4,
            )
            IconButton(onClick = { store.dispatch(ChatIntent.Send) }, enabled = state.draft.isNotBlank() && !state.sending) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    m: ru.zona.app.feature.chat.MessageDto,
    mine: Boolean,
    showSender: Boolean,
    onReply: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine) {
            ru.zona.app.core.media.Avatar(base64 = m.senderAvatar, name = m.senderName.ifBlank { "?" }, size = 32.dp)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(2.dp))
        }
        Surface(
            color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(onClick = {}, onLongClick = onReply),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (showSender && !mine) {
                    Text(m.senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                }
                if (m.replyToId != null) {
                    Surface(
                        color = (if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(m.replyToSender ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(m.replyToText ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 1, color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(2.dp))
                }
                Text(
                    m.text,
                    color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        ru.zona.app.core.util.formatDateTime(m.sentAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = (if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.7f),
                    )
                    if (mine) {
                        Text(
                            if (m.readAt != null) "✓✓" else "✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (m.readAt != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}
