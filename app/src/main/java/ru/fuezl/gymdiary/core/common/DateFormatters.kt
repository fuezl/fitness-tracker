package ru.fuezl.gymdiary.core.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ruLocale = Locale.forLanguageTag("ru-RU")
private val zone: ZoneId = ZoneId.systemDefault()
private val dateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", ruLocale)
private val dayDateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", ruLocale)
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", ruLocale)
private val monthFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", ruLocale)

fun formatDate(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(dateFormatter)
fun formatDayDate(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(dayDateFormatter)
fun formatTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(timeFormatter)
fun formatMonth(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(monthFormatter)

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "$h ч $m мин" else "$m мин"
}

fun Double.formatKg(): String = if (this % 1.0 == 0.0) "%.0f кг".format(this) else "%.1f кг".format(this)
