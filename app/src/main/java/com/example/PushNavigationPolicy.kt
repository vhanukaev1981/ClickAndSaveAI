package com.example

internal const val PUSH_TYPE_EXTRA = "type"
internal const val PUSH_TYPE_NEW_INVOICE = "NEW_INVOICE"
internal const val PUSH_TYPE_VERIFIED_SAVINGS_OPPORTUNITY = "VERIFIED_SAVINGS_OPPORTUNITY"
internal const val PUSH_TYPE_TEST = "PUSH_TEST"

internal fun destinationTabForPushType(pushType: String?): Int? {
    return when (pushType?.trim()) {
        PUSH_TYPE_NEW_INVOICE -> 1
        PUSH_TYPE_VERIFIED_SAVINGS_OPPORTUNITY -> 2
        PUSH_TYPE_TEST -> 0
        else -> null
    }
}
