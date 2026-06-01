package ru.zona.app.core.util

/** Форматирование цены из копеек/центов в «199.00 ₵» либо «Бесплатно». */
fun formatPrice(cents: Long?): String =
    if (cents == null || cents <= 0) {
        "Бесплатно"
    } else {
        val whole = cents / 100
        val frac = (cents % 100).toInt()
        val fracStr = if (frac < 10) "0$frac" else "$frac"
        "$whole.$fracStr ₵"
    }

fun formatBalance(cents: Long): String {
    val whole = cents / 100
    val frac = (cents % 100).toInt()
    val fracStr = if (frac < 10) "0$frac" else "$frac"
    return "$whole.$fracStr ₵"
}
