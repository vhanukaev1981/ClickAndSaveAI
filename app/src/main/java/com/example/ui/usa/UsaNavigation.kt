package com.example.ui.usa

enum class UsaTab(val label: String, val tag: String) {
    HOME("Home", "usa_nav_home"),
    BILLS("Bills", "usa_nav_bills"),
    SAVINGS("Savings", "usa_nav_savings"),
    ACTIVITY("Activity", "usa_nav_activity"),
    PROFILE("Profile", "usa_nav_profile")
}

sealed class UsaDestination {
    object MainApp : UsaDestination()
    object Launch : UsaDestination()
    object Onboard1 : UsaDestination()
    object Onboard2 : UsaDestination()
    object OnboardConnect : UsaDestination()
    object OnboardMonitoring : UsaDestination()
    object OpportunityDetail : UsaDestination()
    object ActionHandoff : UsaDestination()
    object ContinuedMonitoring : UsaDestination()
    object NewBillReview : UsaDestination()
    object SavingsVerification : UsaDestination()
    object HouseholdProfile : UsaDestination()
    object ConnectedAccounts : UsaDestination()
    object MonitoringPreferences : UsaDestination()
    object Notifications : UsaDestination()
    object PrivacyData : UsaDestination()
    object Security : UsaDestination()
    object HelpAbout : UsaDestination()
    object StateLibrary : UsaDestination()
}

enum class UsaSheetType {
    NONE,
    TRUE_COST,
    DECISION
}
