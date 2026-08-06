package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.DealAnalysisResult
import com.example.ai.GeminiShoppingService
import com.example.ai.ReceiptScanResult
import com.example.data.local.AppDatabase
import com.example.data.local.CouponItem
import com.example.data.local.InvoiceItem
import com.example.data.local.SavingsRecord
import com.example.data.local.WatchlistItem
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthState
import com.example.data.repository.GmailRepository
import com.example.data.repository.GmailSyncState
import com.example.data.repository.ShoppingRepository
import com.example.data.repository.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class PriceHikeAlertItem(
    val id: Long,
    val serviceName: String,
    val planName: String,
    val daysUntilExpiry: Int = 14,
    val expiryDate: String,
    val currentPrice: Double,
    val expectedPriceAfterHike: Double,
    val alternativeProvider: String,
    val alternativePrice: Double,
    val category: String,
    val isSwitchRequested: Boolean = false
)

data class DailyTip(
    val id: Int,
    val title: String,
    val category: String,
    val iconSymbol: String,
    val description: String,
    val estimatedMonthlySavings: Double,
    val actionText: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = ShoppingRepository(db)
    private val geminiService = GeminiShoppingService()

    val authRepository = AuthRepository(application)
    val gmailRepository = GmailRepository(repository)

    val userSession: StateFlow<UserSession> = authRepository.userSession
    val authState: StateFlow<AuthState> = authRepository.authState
    val isGmailConnected: StateFlow<Boolean> = gmailRepository.isConnected
    val connectedEmail: StateFlow<String> = gmailRepository.connectedEmail
    val lastScanTime: StateFlow<String> = gmailRepository.lastScanTime

    private val _isSyncingGmail = MutableStateFlow(false)
    val isSyncingGmail: StateFlow<Boolean> = _isSyncingGmail.asStateFlow()

    private val _gmailSyncStep = MutableStateFlow("")
    val gmailSyncStep: StateFlow<String> = _gmailSyncStep.asStateFlow()

    init {
        viewModelScope.launch {
            gmailRepository.syncState.collect { state ->
                when (state) {
                    is GmailSyncState.Syncing -> {
                        _isSyncingGmail.value = true
                        _gmailSyncStep.value = state.stepMessage
                    }
                    is GmailSyncState.Success -> {
                        _isSyncingGmail.value = false
                        _gmailSyncStep.value = state.message
                    }
                    is GmailSyncState.Error -> {
                        _isSyncingGmail.value = false
                        _gmailSyncStep.value = state.errorMessage
                    }
                    GmailSyncState.Idle -> {
                        _isSyncingGmail.value = false
                        _gmailSyncStep.value = ""
                    }
                }
            }
        }
    }

    val monthlySavingsGoal = MutableStateFlow(2000.0)
    val preferredElectricityProvider = MutableStateFlow("לא נבחר")
    val preferredCellularProvider = MutableStateFlow("לא נבחר")
    val preferredInternetProvider = MutableStateFlow("לא נבחר")
    val preferredInsuranceProvider = MutableStateFlow("לא נבחר")
    val preferredStreamingProvider = MutableStateFlow("לא נבחר")
    val autoSwitchAlertsEnabled = MutableStateFlow(false)
    val minSavingsThreshold = MutableStateFlow(20.0)

    fun updatePreferences(
        goal: Double,
        electricity: String,
        cellular: String,
        internet: String,
        insurance: String,
        streaming: String,
        autoSwitch: Boolean,
        minThreshold: Double
    ) {
        monthlySavingsGoal.value = goal.coerceAtLeast(0.0)
        preferredElectricityProvider.value = electricity
        preferredCellularProvider.value = cellular
        preferredInternetProvider.value = internet
        preferredInsuranceProvider.value = insurance
        preferredStreamingProvider.value = streaming
        autoSwitchAlertsEnabled.value = autoSwitch
        minSavingsThreshold.value = minThreshold.coerceAtLeast(0.0)
    }

    val dailyTips = listOf(
        DailyTip(
            id = 1,
            title = "בדיקת תנאי חשבון חשמל",
            category = "חשמל",
            iconSymbol = "⚡",
            description = "בדוק את תנאי המסלול מול מקור רשמי ועדכני לפני קבלת החלטה.",
            estimatedMonthlySavings = 0.0,
            actionText = "פתח קטלוג הדגמה"
        ),
        DailyTip(
            id = 2,
            title = "בדיקת חבילת סלולר משפחתית",
            category = "סלולר",
            iconSymbol = "📱",
            description = "השווה נפח גלישה, מספר קווים, תקופת מבצע ומחיר לאחר המבצע.",
            estimatedMonthlySavings = 0.0,
            actionText = "בדוק אפשרויות"
        ),
        DailyTip(
            id = 3,
            title = "בדיקת כפילויות ביטוח",
            category = "ביטוח",
            iconSymbol = "🛡️",
            description = "יש לבצע בדיקה מול מקורות מוסמכים ולוודא התאמת כיסוי לפני שינוי פוליסה.",
            estimatedMonthlySavings = 0.0,
            actionText = "קבל מידע כללי"
        )
    )

    val currentTipIndex = MutableStateFlow(0)

    fun nextDailyTip() {
        currentTipIndex.value = (currentTipIndex.value + 1) % dailyTips.size
    }

    fun prevDailyTip() {
        currentTipIndex.value = if (currentTipIndex.value == 0) {
            dailyTips.lastIndex
        } else {
            currentTipIndex.value - 1
        }
    }

    val showPushBanner = MutableStateFlow(false)
    val pushNotificationTitle = MutableStateFlow("הדגמת הודעה מקומית")
    val pushNotificationBody = MutableStateFlow("לא נשלחה התראת Push ולא בוצעה פעולה חיצונית.")
    val priceHikeAlerts = MutableStateFlow<List<PriceHikeAlertItem>>(emptyList())

    fun triggerSimulatedPushNotification() {
        pushNotificationTitle.value = "הדגמת הודעה מקומית"
        pushNotificationBody.value = "זו תצוגה בתוך האפליקציה בלבד, לא התראת Push אמיתית."
        showPushBanner.value = true
    }

    fun triggerPriceHikeSimulatedPush() {
        pushNotificationTitle.value = "הדגמת התראת מחיר"
        pushNotificationBody.value = "אין כרגע ניטור מחירים או התראה מתוזמנת ברקע."
        showPushBanner.value = true
    }

    fun dismissPushBanner() {
        showPushBanner.value = false
    }

    fun connectGmail(email: String = "") {
        viewModelScope.launch {
            val session = userSession.value
            gmailRepository.fetchAndParseInvoices(
                accessToken = session.gmailOAuthAccessToken,
                userEmail = email.ifBlank { session.email }
            )
        }
    }

    fun triggerGmailSync() = connectGmail()

    fun disconnectGmail() {
        gmailRepository.disconnectGmail()
    }

    fun signInWithGoogle(webClientId: String = "") {
        viewModelScope.launch {
            authRepository.signInWithGoogle(webClientId)
        }
    }

    fun signOut() {
        authRepository.signOut()
        disconnectGmail()
    }

    val selectedTab = MutableStateFlow(0)
    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow("All")

    val watchlistItems: StateFlow<List<WatchlistItem>> = repository.watchlistItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val coupons: StateFlow<List<CouponItem>> = repository.coupons
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val savingsRecords: StateFlow<List<SavingsRecord>> = repository.savingsRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val invoices: StateFlow<List<InvoiceItem>> = repository.invoices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalMonthlySavingsPotential: StateFlow<Double> = repository.totalMonthlySavingsPotential
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)
    val totalMonthlyCost: StateFlow<Double> = repository.totalMonthlyCost
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)
    val totalSavings: StateFlow<Double> = repository.totalSavingsAmount
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    fun requestProviderSwitch(invoice: InvoiceItem) {
        viewModelScope.launch { repository.requestProviderSwitch(invoice) }
    }

    fun addManualInvoice(
        providerName: String,
        category: String,
        monthlyCost: Double,
        recommendedAlternative: String,
        alternativeCost: Double,
        savings: Double
    ) {
        viewModelScope.launch {
            val note = recommendedAlternative.takeIf { it.isNotBlank() }
                ?.let { "טרם אומת. טקסט שהוזן: $it" }
                ?: "טרם בוצעה השוואה מאומתת"
            repository.addInvoice(
                InvoiceItem(
                    providerName = providerName.ifBlank { "ספק לא צוין" },
                    category = category,
                    monthlyCost = monthlyCost.coerceAtLeast(0.0),
                    recommendedAlternative = note,
                    alternativeMonthlyCost = monthlyCost.coerceAtLeast(0.0),
                    potentialMonthlySavings = 0.0,
                    status = "נוסף ידנית - ממתין לאימות",
                    isSwitchRequested = false,
                    accountNumber = "",
                    billDate = ""
                )
            )
        }
        @Suppress("UNUSED_VARIABLE")
        val ignoredUnverifiedCalculation = alternativeCost + savings
    }

    fun deleteInvoice(id: Long) {
        viewModelScope.launch { repository.deleteInvoice(id) }
    }

    val aiDealAnalysis = MutableStateFlow<DealAnalysisResult?>(null)
    val isAnalyzingDeal = MutableStateFlow(false)
    val chatMessages = MutableStateFlow(
        listOf(
            ChatMessage(
                text = "שירות ה-AI אינו מחובר כרגע. לא יישלח מידע לספק חיצוני.",
                isUser = false
            )
        )
    )
    val isAiChatLoading = MutableStateFlow(false)
    val receiptScanResult = MutableStateFlow<ReceiptScanResult?>(null)
    val isReceiptScanning = MutableStateFlow(false)

    fun setTab(index: Int) {
        selectedTab.value = index.coerceIn(0, 4)
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setCategoryFilter(category: String) {
        selectedCategory.value = category
    }

    fun analyzeDeal(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            isAnalyzingDeal.value = true
            try {
                aiDealAnalysis.value = geminiService.analyzeDeal(query)
            } finally {
                isAnalyzingDeal.value = false
            }
        }
    }

    fun sendChatMessage(userText: String) {
        if (userText.isBlank()) return
        val userMessage = ChatMessage(text = userText, isUser = true)
        chatMessages.value = chatMessages.value + userMessage

        viewModelScope.launch {
            isAiChatLoading.value = true
            try {
                val history = chatMessages.value.takeLast(6).joinToString("\n") {
                    if (it.isUser) "User: ${it.text}" else "AI: ${it.text}"
                }
                val reply = geminiService.chatWithAi(userText, history)
                chatMessages.value = chatMessages.value + ChatMessage(text = reply, isUser = false)
            } finally {
                isAiChatLoading.value = false
            }
        }
    }

    fun addWatchlistItem(
        name: String,
        store: String,
        originalPrice: Double,
        currentPrice: Double,
        targetPrice: Double,
        category: String
    ) {
        viewModelScope.launch {
            repository.addWatchlistItem(
                WatchlistItem(
                    name = name,
                    storeName = store,
                    originalPrice = originalPrice,
                    currentPrice = currentPrice,
                    targetPrice = targetPrice,
                    category = category,
                    priceHistoryCsv = currentPrice.toString()
                )
            )
        }
    }

    fun deleteWatchlistItem(id: Long) {
        viewModelScope.launch { repository.removeWatchlistItem(id) }
    }

    fun copyCoupon(coupon: CouponItem) {
        viewModelScope.launch { repository.incrementCouponCopy(coupon) }
    }

    fun toggleFavoriteCoupon(coupon: CouponItem) {
        viewModelScope.launch { repository.toggleCouponFavorite(coupon) }
    }

    fun addSavingsRecord(title: String, store: String, amount: Double, category: String, note: String) {
        if (amount <= 0.0) return
        viewModelScope.launch {
            repository.addSavings(
                SavingsRecord(
                    title = title,
                    storeName = store,
                    amountSaved = amount,
                    category = category,
                    note = note
                )
            )
        }
    }

    fun scanReceipt(bitmap: Bitmap) {
        viewModelScope.launch {
            isReceiptScanning.value = true
            try {
                receiptScanResult.value = geminiService.scanReceiptOrCoupon(bitmap)
            } finally {
                isReceiptScanning.value = false
            }
        }
    }

    fun reportReceiptScanUnavailable() {
        receiptScanResult.value = ReceiptScanResult(
            storeName = "לא נותח",
            totalAmount = 0.0,
            estimatedSavings = 0.0,
            itemSummary = "צילום וסריקת קבלה אינם מחוברים בגרסה זו.",
            cashbackTips = "לא הופקה המלצה."
        )
    }

    fun clearReceiptResult() {
        receiptScanResult.value = null
    }
}
