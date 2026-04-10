package com.vm.vector.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * [androidx.compose.material3.DatePicker] / [androidx.compose.material3.rememberDatePickerState] use
 * [androidx.compose.material3.DatePickerState.selectedDateMillis]: UTC midnight of the **selected calendar day**.
 * Converting with [java.util.Date], [java.text.SimpleDateFormat], or [java.time.ZoneId.systemDefault]
 * breaks in many timezones (off-by-one day). Always pair ISO calendar strings with these helpers.
 */
private val isoDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun isoCalendarDateToUtcPickerMillis(isoDate: String): Long =
    LocalDate.parse(isoDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun utcPickerMillisToIsoCalendarDate(utcMillis: Long): String =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate().format(isoDate)
