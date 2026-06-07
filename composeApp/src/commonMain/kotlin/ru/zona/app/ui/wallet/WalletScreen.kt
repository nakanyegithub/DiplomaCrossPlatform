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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import ru.zona.app.core.design.LoadingState
import ru.zona.app.core.design.MessageState
import ru.zona.app.core.design.ZonaBadge
import ru.zona.app.core.design.ZonaCard
import ru.zona.app.core.design.ZonaPrimaryButton
import ru.zona.app.core.design.ZonaSecondaryButton
import ru.zona.app.core.design.ZonaTextField
import ru.zona.app.core.mvi.collectState
import ru.zona.app.core.util.formatBalance
import ru.zona.app.feature.wallet.WalletEffect
import ru.zona.app.feature.wallet.WalletIntent
import ru.zona.app.feature.wallet.WalletStore
import ru.zona.app.ui.common.ZonaTopBar
import zona.resources.Res
import zona.resources.action_retry
import zona.resources.state_error
import zona.resources.wallet_amount_hint
import zona.resources.wallet_balance
import zona.resources.wallet_custom_amount
import zona.resources.wallet_demo_hint
import zona.resources.wallet_history
import zona.resources.wallet_title
import zona.resources.wallet_topup
import zona.resources.wallet_topup_title

/** Глупый экран: состояние и логика в WalletStore. */
@Composable
fun WalletScreen(
    store: WalletStore,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by store.collectState { eff -> when (eff) { is WalletEffect.Message -> onMessage(eff.text) } }
    LaunchedEffect(Unit) { store.dispatch(WalletIntent.Load) }

    Column(Modifier.fillMaxSize()) {
        ZonaTopBar(title = stringResource(Res.string.wallet_title), onBack = onBack)
        when {
            state.loading -> LoadingState()
            state.error != null -> MessageState(stringResource(Res.string.state_error), state.error!!, actionText = stringResource(Res.string.action_retry), onAction = { store.dispatch(WalletIntent.Load) })
            else -> {
                val w = state.wallet
                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ZonaCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(Res.string.wallet_balance), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatBalance(w?.balanceCents ?: 0), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(stringResource(Res.string.wallet_demo_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    ZonaCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(Res.string.wallet_topup_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(100, 300, 500, 1000).forEach { amount ->
                                    Box(Modifier.weight(1f)) {
                                        ZonaSecondaryButton("+$amount", enabled = !state.busy) { store.dispatch(WalletIntent.TopUp(amount * 100L)) }
                                    }
                                }
                            }
                            Text(stringResource(Res.string.wallet_custom_amount), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    ZonaTextField(state.customAmount, { store.dispatch(WalletIntent.SetCustomAmount(it)) }, stringResource(Res.string.wallet_amount_hint))
                                }
                                Box(Modifier.weight(1f)) {
                                    ZonaPrimaryButton(stringResource(Res.string.wallet_topup), enabled = !state.busy && (state.customAmount.toLongOrNull() ?: 0) > 0) {
                                        store.dispatch(WalletIntent.TopUpCustom)
                                    }
                                }
                            }
                        }
                    }
                    Text(stringResource(Res.string.wallet_history), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
