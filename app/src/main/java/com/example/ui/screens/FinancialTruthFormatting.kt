package com.example.ui.screens

internal fun money(value: Double?): String = value?.let { "₪${String.format("%,.0f", it)}" } ?: "לא ידוע"

internal fun formatAuthoritativeMoney(value: Double?): String = money(value)

internal fun formatAuthoritativeCount(value: Int?): String = value?.toString() ?: "לא ידוע"

internal fun formatVerifiedSavings(value: Double?): String = value?.let { "₪${String.format("%,.0f", it)}" } ?: "דרוש אימות"
