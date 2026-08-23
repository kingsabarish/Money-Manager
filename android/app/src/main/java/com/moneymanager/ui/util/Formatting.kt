package com.moneymanager.ui.util

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Formats a money amount using the device's default currency. */
fun BigDecimal.formatAsCurrency(): String =
    NumberFormat.getCurrencyInstance().format(this)

private val dayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** Formats a calendar day for section headers, e.g. "23 Aug 2026". */
fun LocalDate.formatAsDay(): String = dayFormatter.format(this)
