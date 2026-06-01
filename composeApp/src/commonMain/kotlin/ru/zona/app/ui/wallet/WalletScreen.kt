package ru.zona.app.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import ru.zona.app.core.util.formatBalance
import ru.zona.app.feature.wallet.WalletEffect
import ru.zona.app.feature.wallet.WalletIntent
import ru.zona.app.feature.wallet.WalletStore
import ru.zona.app.ui.common.ZonaTopBar

@Composable
fun WalletScreen(
    store: WalletStore,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by store.collectState { eff -> when (eff) { is WalletEffect.Message -> onMessage(eff.text) } }
    LaunchedEffect(Unit) { store.dispatch(WalletIntent.Load) }

    Column(Modifier.fillMaxSize()) {
        ZonaTopBar(title = "Кошелёк", onBack = onBack)
        when {
            state.loading -> LoadingState()
            state.error != null -> MessageState("Ошибка", state.error!!, actionText = "Повторить", onAction = { store.dispatch(WalletIntent.Load) })
            else -> {
                val w = state.wallet
                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ZonaCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Баланс", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatBalance(w?.balanceCents ?: 0), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Демо-валюта для покупки курсов и занятий", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) { ZonaSecondaryButton("+100 ₵", enabled = !state.busy) { store.dispatch(WalletIntent.TopUp(100_00)) } }
                        Box(Modifier.weight(1f)) { ZonaPrimaryButton("+500 ₵", enabled = !state.busy) { store.dispatch(WalletIntent.TopUp(500_00)) } }
                    }
                    Text("История операций", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(w?.transactions ?: emptyList(), key = { it.id }) { tx ->
                            ZonaCard(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(tx.note, style = MaterialTheme.typography.bodyMedium)
                                    val sign = if (tx.amountCents >= 0) "+" else ""
                                    ZonaBadge("$sign${formatBalance(tx.amountCents)}", content = if (tx.amountCents >= 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
