package ru.zona.app.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.zona.app.core.design.ScreenHeader
import ru.zona.app.core.design.ZonaBadge
import ru.zona.app.core.design.ZonaCard
import ru.zona.app.core.design.ZonaPrimaryButton
import ru.zona.app.core.design.ZonaSecondaryButton
import ru.zona.app.core.design.ZonaTextField
import ru.zona.app.core.mvi.collectState
import ru.zona.app.core.util.formatDateTime
import ru.zona.app.core.util.formatPrice
import ru.zona.app.feature.sessions.CreateSessionEffect
import ru.zona.app.feature.sessions.CreateSessionIntent
import ru.zona.app.feature.sessions.CreateSessionStore

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CreateSessionScreen(
    store: CreateSessionStore,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by store.collectState { eff -> when (eff) { is CreateSessionEffect.Message -> onMessage(eff.text) } }
    LaunchedEffect(Unit) { store.dispatch(CreateSessionIntent.Load) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val dpState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = state.dateMillis)
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    dpState.selectedDateMillis?.let { store.dispatch(CreateSessionIntent.SetDate(it)) }
                    showDatePicker = false
                }) { Text("ОК") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showDatePicker = false }) { Text("Отмена") } },
        ) {
            androidx.compose.material3.DatePicker(state = dpState)
        }
    }

    if (showTimePicker) {
        val tpState = androidx.compose.material3.rememberTimePickerState(initialHour = state.hour, initialMinute = state.minute, is24Hour = true)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    store.dispatch(CreateSessionIntent.SetHour(tpState.hour))
                    store.dispatch(CreateSessionIntent.SetMinute(tpState.minute))
                    showTimePicker = false
                }) { Text("ОК") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showTimePicker = false }) { Text("Отмена") } },
            text = { androidx.compose.material3.TimePicker(state = tpState) },
        )
    }

    Column(Modifier.fillMaxSize()) {
        ru.zona.app.ui.common.ZonaTopBar("Провести занятие", onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ZonaCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) {
                                if (state.type == "GROUP") ZonaPrimaryButton("👥 Группа") {} else ZonaSecondaryButton("👥 Группа") { store.dispatch(CreateSessionIntent.SetType("GROUP")) }
                            }
                            Box(Modifier.weight(1f)) {
                                if (state.type == "INDIVIDUAL") ZonaPrimaryButton("👤 Индивид.") {} else ZonaSecondaryButton("👤 Индивид.") { store.dispatch(CreateSessionIntent.SetType("INDIVIDUAL")) }
                            }
                        }
                        ZonaTextField(state.title, { store.dispatch(CreateSessionIntent.SetTitle(it)) }, "Название занятия")
                        ZonaTextField(state.description, { store.dispatch(CreateSessionIntent.SetDescription(it)) }, "Описание", singleLine = false, minLines = 2)

                        val hh = if (state.hour < 10) "0${state.hour}" else "${state.hour}"
                        val mm = if (state.minute < 10) "0${state.minute}" else "${state.minute}"
                        Text("📅 ${ru.zona.app.core.util.formatDate(state.dateMillis)}  ⏰ $hh:$mm", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { ZonaSecondaryButton("Дата") { showDatePicker = true } }
                            Box(Modifier.weight(1f)) { ZonaSecondaryButton("Время") { showTimePicker = true } }
                        }

                        Text("Длительность занятия", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val durH = state.durationMinutes / 60
                        val durM = state.durationMinutes % 60
                        Stepper("Часы", durH, { newH -> store.dispatch(CreateSessionIntent.SetDuration(newH.coerceAtLeast(0) * 60 + durM)) })
                        Stepper("Минуты", durM, { newM -> store.dispatch(CreateSessionIntent.SetDuration(durH * 60 + ((newM % 60) + 60) % 60)) }, step = 5)
                        Text("Итого: $durH ч $durM мин", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

                        if (state.type == "GROUP") {
                            Text("Количество мест", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Stepper("Мест", state.capacity, { store.dispatch(CreateSessionIntent.SetCapacity(it)) })
                        }
                        ZonaTextField(state.priceText, { store.dispatch(CreateSessionIntent.SetPrice(it)) }, "Цена в ₵ (пусто = бесплатно)")
                        ZonaPrimaryButton(if (state.saving) "Создаём…" else "Создать занятие", enabled = state.title.isNotBlank() && !state.saving) {
                            store.dispatch(CreateSessionIntent.Create)
                        }
                    }
                }
            }
            if (state.mySessions.isNotEmpty()) {
                item { Text("Мои занятия", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                items(state.mySessions, key = { it.id }) { s ->
                    ZonaCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(s.title, style = MaterialTheme.typography.titleSmall)
                            Text("${formatDateTime(s.startsAt)} · ${s.durationMinutes} мин · ${if (s.type == "GROUP") "группа ${s.bookedCount}/${s.capacity}" else "индивидуально"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ZonaBadge(formatPrice(s.priceCents))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit, step: Int = 1) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        ZonaStepBtn("−") { onChange(value - step) }
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.width(48.dp),
        )
        ZonaStepBtn("+") { onChange(value + step) }
    }
}

@Composable
private fun ZonaStepBtn(text: String, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = androidx.compose.foundation.shape.CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}
