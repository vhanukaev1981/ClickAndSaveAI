package com.example.ui.usa

enum class AppDataMode {
    DEMO,
    REAL
}

enum class MonitoringStatus {
    OPPORTUNITY,
    WATCHING,
    RENEWAL
}

enum class EmptyReason {
    NO_CONNECTION,
    NO_SAVINGS,
    NO_VERIFIED_OPTION
}

data class HouseholdSummary(
    val householdName: String,
    val monitoredAnnual: Double,
    val potentialAnnual: Double,
    val expectedAnnual: Double,
    val verifiedAnnual: Double,
    val monitoredCount: Int,
    val opportunitiesCount: Int,
    val renewalsCount: Int,
    val needsReviewCount: Int,
    val lastUpdated: String? = null
)

data class MonitoredService(
    val id: String,
    val icon: String,
    val name: String,
    val provider: String,
    val monthlyCost: Double,
    val costDisplay: String,
    val changeNote: String,
    val status: MonitoringStatus
)

data class TrueCostDetails(
    val monthlyService: Double,
    val equipment: Double,
    val activation: Double,
    val terminationCost: Double,
    val lostBundleDiscount: Double,
    val firstYearTotal: Double,
    val postPromoPrice: String
)

data class Opportunity(
    val id: String,
    val title: String,
    val category: String,
    val subtitle: String,
    val currentMonthly: Double,
    val potentialMonthly: Double,
    val annualSavings: Double,
    val whatChanged: String,
    val whyItMatters: String,
    val eligibility: String,
    val trueCost: TrueCostDetails,
    val hasBetterAlternative: Boolean = true
)

data class GuardianTimelineEvent(
    val id: String,
    val iconSymbol: String,
    val timeLabel: String,
    val title: String,
    val detail: String
)

data class ConnectedAccount(
    val id: String,
    val iconSymbol: String,
    val name: String,
    val purpose: String,
    val status: String,
    val isConnected: Boolean
)

data class HouseholdDetailItem(
    val label: String,
    val value: String,
    val tag: String
)

data class NotificationItem(
    val id: String,
    val title: String,
    val body: String,
    val actionText: String,
    val targetScreen: String
)

sealed class FinancialDataState {
    data class Demo(val summary: HouseholdSummary) : FinancialDataState()
    data class Loading(val previousData: HouseholdSummary? = null) : FinancialDataState()
    data class Live(val summary: HouseholdSummary, val lastUpdated: String) : FinancialDataState()
    data class Partial(val summary: HouseholdSummary, val missingScopes: List<String>) : FinancialDataState()
    data class Stale(val summary: HouseholdSummary, val lastUpdated: String) : FinancialDataState()
    data class Offline(val cachedData: HouseholdSummary, val lastUpdated: String) : FinancialDataState()
    data class Empty(val reason: EmptyReason) : FinancialDataState()
    data class Error(val message: String, val isRecoverable: Boolean) : FinancialDataState()
}

object UsaDemoData {
    val summary = HouseholdSummary(
        householdName = "Rivera household",
        monitoredAnnual = 18420.0,
        potentialAnnual = 2364.0,
        expectedAnnual = 288.0,
        verifiedAnnual = 0.0,
        monitoredCount = 8,
        opportunitiesCount = 8,
        renewalsCount = 2,
        needsReviewCount = 1,
        lastUpdated = "Just now"
    )

    val primaryOpportunity = Opportunity(
        id = "internet-promo-ended",
        title = "Internet bill increased after promo ended",
        category = "Internet",
        subtitle = "A comparable 500 Mbps option may lower this recurring cost.",
        currentMonthly = 94.0,
        potentialMonthly = 62.0,
        annualSavings = 384.0,
        whatChanged = "Your promotional rate expired. Current 500 Mbps plan increased to $94/month.",
        whyItMatters = "A comparable 500 Mbps option is available at an estimated $62/month, saving $32/month.",
        eligibility = "Comparable speed tier · Availability and eligibility must be verified · No action has been taken",
        trueCost = TrueCostDetails(
            monthlyService = 55.0,
            equipment = 7.0,
            activation = 0.0,
            terminationCost = 0.0,
            lostBundleDiscount = 0.0,
            firstYearTotal = 744.0,
            postPromoPrice = "$78/mo"
        ),
        hasBetterAlternative = true
    )

    val monitoredServices = listOf(
        MonitoredService(
            id = "service-internet",
            icon = "⌁",
            name = "Internet",
            provider = "Current provider",
            monthlyCost = 94.0,
            costDisplay = "$94/mo",
            changeNote = "Promo ended",
            status = MonitoringStatus.OPPORTUNITY
        ),
        MonitoredService(
            id = "service-mobile",
            icon = "▯",
            name = "Mobile",
            provider = "Current carrier",
            monthlyCost = 165.0,
            costDisplay = "$165/mo",
            changeNote = "No recent change",
            status = MonitoringStatus.WATCHING
        ),
        MonitoredService(
            id = "service-electricity",
            icon = "⚡",
            name = "Electricity",
            provider = "Current supplier",
            monthlyCost = 142.0,
            costDisplay = "$142/mo",
            changeNote = "Term ends in 38 days",
            status = MonitoringStatus.RENEWAL
        ),
        MonitoredService(
            id = "service-auto-ins",
            icon = "▣",
            name = "Auto insurance",
            provider = "Current carrier",
            monthlyCost = 178.0,
            costDisplay = "$178/mo",
            changeNote = "Renewal quote increased",
            status = MonitoringStatus.OPPORTUNITY
        ),
        MonitoredService(
            id = "service-streaming",
            icon = "⌂",
            name = "Streaming",
            provider = "Multiple services",
            monthlyCost = 47.0,
            costDisplay = "$47/mo",
            changeNote = "One price increase",
            status = MonitoringStatus.WATCHING
        )
    )

    val timelineEvents = listOf(
        GuardianTimelineEvent(
            id = "evt-1",
            iconSymbol = "!",
            timeLabel = "Today, 9:42 AM",
            title = "Internet price increase detected",
            detail = "Review recommended"
        ),
        GuardianTimelineEvent(
            id = "evt-2",
            iconSymbol = "✓",
            timeLabel = "Today, 7:10 AM",
            title = "Mobile plan checked",
            detail = "No better verified option found"
        ),
        GuardianTimelineEvent(
            id = "evt-3",
            iconSymbol = "◷",
            timeLabel = "Yesterday",
            title = "Insurance renewal approaching",
            detail = "23 days remaining"
        ),
        GuardianTimelineEvent(
            id = "evt-4",
            iconSymbol = "↗",
            timeLabel = "September 12",
            title = "Provider handoff opened",
            detail = "Waiting for your next bill"
        ),
        GuardianTimelineEvent(
            id = "evt-5",
            iconSymbol = "◎",
            timeLabel = "September 2",
            title = "Savings verification completed",
            detail = "$23.79/month verified"
        )
    )

    val connectedAccounts = listOf(
        ConnectedAccount(
            id = "acc-1",
            iconSymbol = "G",
            name = "Authorized account",
            purpose = "Recurring bill discovery",
            status = "Active",
            isConnected = true
        )
    )

    val householdDetails = listOf(
        HouseholdDetailItem("Household members", "2 adults · 1 child", "Optional"),
        HouseholdDetailItem("Mobile lines", "4 lines", "Added"),
        HouseholdDetailItem("Service area", "Austin, TX", "Added"),
        HouseholdDetailItem("Vehicles & drivers", "Not provided", "Optional"),
        HouseholdDetailItem("Internet needs", "Remote work · streaming", "Added"),
        HouseholdDetailItem("Contract tolerance", "No long commitments", "Added")
    )

    val notifications = listOf(
        NotificationItem(
            id = "notif-1",
            title = "Price increased",
            body = "Your internet bill rose $18 after the promo ended. Review a potential $32/month alternative.",
            actionText = "Review opportunity",
            targetScreen = "opportunity"
        ),
        NotificationItem(
            id = "notif-2",
            title = "Renewal approaching",
            body = "Home insurance renews in 23 days. Coverage comparison is ready.",
            actionText = "Review renewal",
            targetScreen = "bills"
        ),
        NotificationItem(
            id = "notif-3",
            title = "Unexpected bill",
            body = "Your new bill is $26.31 above the expected amount.",
            actionText = "Review bill",
            targetScreen = "newbill"
        ),
        NotificationItem(
            id = "notif-4",
            title = "Verification complete",
            body = "Your new bill confirms $23.79/month in savings.",
            actionText = "View verification",
            targetScreen = "verified"
        )
    )
}
