package com.example

internal const val PUSH_TYPE_EXTRA = "type"
internal const val PUSH_OPPORTUNITY_ID_EXTRA = "opportunityId"
internal const val PUSH_OFFER_ID_EXTRA = "offerId"
internal const val PUSH_TYPE_NEW_INVOICE = "NEW_INVOICE"
internal const val PUSH_TYPE_VERIFIED_SAVINGS_OPPORTUNITY = "VERIFIED_SAVINGS_OPPORTUNITY"
internal const val PUSH_TYPE_TEST = "PUSH_TEST"

internal data class PushNavigationTarget(
    val tab: Int,
    val opportunityId: String? = null,
    val offerId: String? = null
)

internal fun destinationTabForPushType(pushType: String?): Int? {
    return when (pushType?.trim()) {
        PUSH_TYPE_NEW_INVOICE -> 1
        PUSH_TYPE_VERIFIED_SAVINGS_OPPORTUNITY -> 2
        PUSH_TYPE_TEST -> 0
        else -> null
    }
}

internal fun navigationTargetForPush(
    pushType: String?,
    opportunityId: String?,
    offerId: String?
): PushNavigationTarget? {
    return when (pushType?.trim()) {
        PUSH_TYPE_NEW_INVOICE -> PushNavigationTarget(tab = 1)
        PUSH_TYPE_TEST -> PushNavigationTarget(tab = 0)
        PUSH_TYPE_VERIFIED_SAVINGS_OPPORTUNITY -> {
            val exactOpportunityId = opportunityId?.trim().orEmpty()
            val exactOfferId = offerId?.trim().orEmpty()
            if (exactOpportunityId.isBlank() || exactOfferId.isBlank()) {
                null
            } else {
                PushNavigationTarget(
                    tab = 2,
                    opportunityId = exactOpportunityId,
                    offerId = exactOfferId
                )
            }
        }
        else -> null
    }
}

internal fun pendingIntentRequestCodeForPushTarget(target: PushNavigationTarget?): Int {
    return when (target?.tab) {
        0 -> 100
        1 -> 101
        2 -> {
            val exactPair = "${target.opportunityId.orEmpty()}\u0000${target.offerId.orEmpty()}"
            1_000 + (exactPair.hashCode() and 0x3fffffff)
        }
        else -> 199
    }
}
