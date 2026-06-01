package ru.zona.app.core.util

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** «дд.мм чч:мм» в локальной зоне. */
fun formatDateTime(epochMs: Long): String {
    val dt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault())
    val d = dt.dayOfMonth.pad()
    val mo = dt.monthNumber.pad()
    val h = dt.hour.pad()
    val mi = dt.minute.pad()
    return "$d.$mo $h:$mi"
}

private fun Int.pad(): String = if (this < 10) "0$this" else "$this"
