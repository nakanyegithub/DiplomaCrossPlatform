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

/** «дд.мм.гггг» — для выбранной даты (используем UTC, т.к. DatePicker отдаёт полночь UTC). */
fun formatDate(epochMs: Long): String {
    val dt = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.UTC)
    return "${dt.dayOfMonth.pad()}.${dt.monthNumber.pad()}.${dt.year}"
}

private fun Int.pad(): String = if (this < 10) "0$this" else "$this"
