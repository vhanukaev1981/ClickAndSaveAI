package com.example.ui.screens

internal fun money(value: Double?): String = value?.let { "₪${String.format("%,.0f", it)}" } ?: "לא ידוע"

internal fun Int?.toString(): String = if (this == null) "לא ידוע" else "$this"

internal fun formatAuthoritativeMoney(value: Double?): String = money(value)

internal fun formatAuthoritativeCount(value: Int?): String = if (value == null) "לא ידוע" else "$value"

internal fun formatVerifiedSavings(value: Double?): String = value?.let { "₪${String.format("%,.0f", it)}" } ?: "דרוש אימות"
