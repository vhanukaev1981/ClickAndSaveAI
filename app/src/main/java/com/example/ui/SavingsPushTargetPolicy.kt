package com.example.ui

data class SavingsPushCandidate(
    val opportunityId: String,
    val offerId: String?
)

enum class SavingsPushTargetStatus {
    NONE,
    FOCUSED,
    STALE
}

data class SavingsPushTargetResolution(
    val status: SavingsPushTargetStatus,
    val focusedOpportunityId: String? = null
)

fun resolveSavingsPushTarget(
    target: SavingsPushTarget?,
    candidates: List<SavingsPushCandidate>
): SavingsPushTargetResolution {
    if (target == null) {
        return SavingsPushTargetResolution(status = SavingsPushTargetStatus.NONE)
    }

    val exactOpportunityId = target.opportunityId.trim()
    val exactOfferId = target.offerId.trim()
    if (exactOpportunityId.isBlank() || exactOfferId.isBlank()) {
        return SavingsPushTargetResolution(status = SavingsPushTargetStatus.STALE)
    }

    val opportunity = candidates.firstOrNull {
        it.opportunityId.trim() == exactOpportunityId
    } ?: return SavingsPushTargetResolution(status = SavingsPushTargetStatus.STALE)

    val currentOfferId = opportunity.offerId?.trim().orEmpty()
    if (currentOfferId != exactOfferId) {
        return SavingsPushTargetResolution(status = SavingsPushTargetStatus.STALE)
    }

    return SavingsPushTargetResolution(
        status = SavingsPushTargetStatus.FOCUSED,
        focusedOpportunityId = exactOpportunityId
    )
}
