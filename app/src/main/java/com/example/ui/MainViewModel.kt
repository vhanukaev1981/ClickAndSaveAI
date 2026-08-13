package com.example.ui

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
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
import com.example.data.repository.BackendRepository
import com.example.data.repository.FinancialActivityRepository
import com.example.data.repository.FinancialActivityResult
import com.example.data.repository.FinancialHomeResult
import com.example.data.repository.FinancialRefreshReason
import com.example.data.repository.FinancialSessionRecovery
import com.example.data.repository.FinancialSyncState
import com.example.data.repository.GmailConnectionResult
import com.example.data.repository.GmailRepository
import com.example.data.repository.GmailScanResult
import com.example.data.repository.GmailSyncState
import com.example.data.repository.ProviderLeadRequest
import com.example.data.repository.ProviderLeadResult
import com.example.data.repository.ShoppingRepository
import com.example.data.repository.UserSession
import com.example.data.repository.activityOrNull
import com.example.data.repository.financialHomeOrNull
import com.example.data.repository.gmailConnectionOrNull
import com.example.data.repository.latestScanOrNull
import com.example.data.repository.observedRecurringMonthlySpendOrNull
import com.example.data.repository.recurringServiceCountOrNull
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

sealed class ProviderLeadUiState {
    data object Idle : ProviderLeadUiState()
    data object Submitting : ProviderLeadUiState()
    data class Success(val result: ProviderLeadResult) : ProviderLeadUiState()
    data class Error(val message: String) : ProviderLeadUiState()
}

class MainViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val shoppingRepository = ShoppingRepository(db)
    private val backendRepository = BackendRepository()
    private val financialActivityRepository = FinancialActivityRepository()
    private val geminiService = GeminiShoppingService(backendRepository)

    val authRepository = AuthRepository(application)
    val gmailRepository = GmailRepository(shoppingRepository, backendRepository)

    private val financialSessionRecovery = FinancialSessionRecovery(
        getConnectionStatus = { gmailRepository.refreshConnectionStatus().getOrThrow() },
        recoverInvoices = { gmailRepository.scanInvoices().getOrThrow() },
        getFinancialHome = { backendRepository.getFinancialHome() }
    )

    private val _financialSyncState =
        MutableStateFlow<FinancialSyncState>(FinancialSyncState.Unauthenticated)
    val financialSyncState: StateFlow<FinancialSyncState> = _financialSyncState.asStateFlow()

    val authoritativeFinancialHome: StateFlow<FinancialHomeResult?> = financialSyncState
        .map { it.financialHomeOrNull }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val latestRecoveredGmailScan: StateFlow<GmailScanResult?> = financialSyncState
        .map { it.latestScanOrNull }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val authoritativeGmailConnection: StateFlow<GmailConnectionResult?> = financialSyncState
        .map { it.gmailConnectionOrNull }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val authoritativeFinancialActivity: StateFlow<FinancialActivityResult?> = financialSyncState
        .map { it.activityOrNull }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val observedRecurringMonthlySpend: StateFlow<Double?> = financialSyncState
        .map { it.observedRecurringMonthlySpendOrNull }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recurringServiceCount: StateFlow<Int?> = financialSyncState
        .map { it.recurringServiceCountOrNull }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val userSession: StateFlow<UserSession> = authRepository.userSession
    val authState: StateFlow<AuthState> = authRepository.authState
    val isGmailConnected: StateFlow<Boolean> = financialSyncState
        .map { it.gmailConnectionOrNull?.connected == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val connectedEmail: StateFlow<String> = financialSyncState
        .map { it.gmailConnectionOrNull?.email.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val lastScanTime: StateFlow<String> = gmailRepository.lastScanTime

    private val _isSyncingGmail = MutableStateFlow(false)
    val isSyncingGmail: StateFlow<Boolean> = _isSyncingGmail.asStateFlow()

    private val _gmailSyncStep = MutableStateFlow("")
    val gmailSyncStep: StateFlow<String> = _gmailSyncStep.asStateFlow()

    private val _providerLeadState = MutableStateFlow<ProviderLeadUiState>(ProviderLeadUiState.Idle)
    val providerLeadState: StateFlow<ProviderLeadUiState> = _providerLeadState.asStateFlow()

    private val _aiErrorMessage = MutableStateFlow("")
    val aiErrorMessage: StateFlow<String> = _aiErrorMessage.asStateFlow()

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
            actionText = "פתח קטלוג"
        ),
        DailyTip(
            id = 2,
            title = "בדיקת חבילת סלולר משפחתית",
            category = "סלולר",
            iconSymbol = "📱",
            description = "השווה נפח גלישה, מספר קווים, תקופת מבצע ומחיר לאחר המבצע.",
            estimatedMonthlySavings = 0.0,
            actionText = "בדוק אפשרויות"
        )
    )
    val currentTipIndex = MutableStateFlow(0)

    fun nextDailyTip() {
        currentTipIndex.value = (currentTipIndex.value + 1) % dailyTips.size
    }

    fun prevDailyTip() {
        currentTipIndex.value = if (currentTipIndex.value == 0) dailyTips.lastIndex
        else currentTipIndex.value - 1
    }

    val showPushBanner = MutableStateFlow(false)
    val pushNotificationTitle = MutableStateFlow("הדגמת הודעה מקומית")
    val pushNotificationBody = MutableStateFlow("לא נשלחה התראת Push ולא בוצעה פעולה חיצונית.")
    val priceHikeAlerts = MutableStateFlow<List<PriceHikeAlertItem>>(emptyList())

    fun triggerSimulatedPushNotification() {
        showPushBanner.value = true
    }

    fun triggerPriceHikeSimulatedPush() {
        pushNotificationTitle.value = "הדגמת התראת מחיר"
        pushNotificationBody.value = "אין כרגע ניטור מחירים מתוזמן ברקע."
        showPushBanner.value = true
    }

    fun dismissPushBanner() {
        showPushBanner.value = false
    }

    fun refreshFinancialSession(reason: FinancialRefreshReason) {
        viewModelScope.launch { recoverFinancialSession(reason) }
    }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun recoverFinancialSession(reason: FinancialRefreshReason) {
        if (!userSession.value.isAuthenticated) {
            _financialSyncState.value = FinancialSyncState.Unauthenticated
            return
        }

        val previous = _financialSyncState.value
        val recovered = financialSessionRecovery.refresh(previous = previous) { state ->
            when (state) {
                FinancialSyncState.CheckingConnection,
                FinancialSyncState.Recovering -> _financialSyncState.value = state
                else -> Unit
            }
        }
        _financialSyncState.value = attachAuthoritativeProductEvidence(recovered, previous)
    }

    private suspend fun attachAuthoritativeProductEvidence(
        recovered: FinancialSyncState,
        previous: FinancialSyncState
    ): FinancialSyncState {
        if (
            recovered is FinancialSyncState.Unauthenticated ||
            recovered is FinancialSyncState.Disconnected ||
            recovered is FinancialSyncState.CheckingConnection ||
            recovered is FinancialSyncState.Recovering
        ) return recovered

        val connection = runCatching { backendRepository.getGmailConnectionStatus() }
            .getOrElse { error ->
                return when (recovered) {
                    is FinancialSyncState.Ready -> FinancialSyncState.Partial(
                        latestScan = recovered.latestScan,
                        financialHome = recovered.financialHome,
                        reason = safeProductReason(error),
                        gmailConnection = null,
                        activity = previous.activityOrNull
                    )
                    is FinancialSyncState.Partial -> recovered.copy(
                        reason = safeProductReason(error),
                        gmailConnection = null,
                        activity = recovered.activity ?: previous.activityOrNull
                    )
                    else -> recovered
                }
            }

        if (!connection.connected) return FinancialSyncState.Disconnected

        val activity = runCatching { financialActivityRepository.getFinancialActivity() }
            .getOrElse { error ->
                return when (recovered) {
                    is FinancialSyncState.Ready -> FinancialSyncState.Partial(
                        latestScan = recovered.latestScan,
                        financialHome = recovered.financialHome,
                        reason = safeProductReason(error),
                        gmailConnection = connection,
                        activity = previous.activityOrNull
                    )
                    is FinancialSyncState.Partial -> recovered.copy(
                        reason = safeProductReason(error),
                        gmailConnection = connection,
                        activity = recovered.activity ?: previous.activityOrNull
                    )
                    is FinancialSyncState.Failed -> FinancialSyncState.Partial(
                        latestScan = null,
                        financialHome = null,
                        reason = recovered.reason,
                        gmailConnection = connection,
                        activity = previous.activityOrNull
                    )
                    else -> recovered
                }
            }

        return when (recovered) {
            is FinancialSyncState.Ready -> recovered.copy(
                gmailConnection = connection,
                activity = activity
            )
            is FinancialSyncState.Partial -> recovered.copy(
                gmailConnection = connection,
                activity = activity
            )
            is FinancialSyncState.Failed -> FinancialSyncState.Partial(
                latestScan = null,
                financialHome = null,
                reason = recovered.reason,
                gmailConnection = connection,
                activity = activity
            )
            else -> recovered
        }
    }

    private fun safeProductReason(error: Throwable): String {
        return error.localizedMessage?.takeIf(String::isNotBlank)
            ?: "חלק מנתוני המוצר הסמכותיים אינם זמינים כרגע."
    }

    fun completeGmailAuthorization(serverAuthCode: String) {
        viewModelScope.launch {
            val email = userSession.value.email
            val connected = gmailRepository.connectWithAuthorizationCode(serverAuthCode, email)
            if (connected.isSuccess) recoverFinancialSession(FinancialRefreshReason.GMAIL_CONNECTED)
        }
    }

    fun reportGmailAuthorizationError(message: String) {
        _gmailSyncStep.value = message
        _isSyncingGmail.value = false
    }

    fun triggerGmailSync() {
        refreshFinancialSession(FinancialRefreshReason.MANUAL_SCAN)
    }

    fun disconnectGmail() {
        viewModelScope.launch {
            val disconnected = gmailRepository.disconnectGmail()
            if (disconnected.isSuccess) _financialSyncState.value = FinancialSyncState.Disconnected
        }
    }

    fun signInWithGoogle(activity: Activity, webClientId: String) {
        viewModelScope.launch { authRepository.signInWithGoogle(activity, webClientId) }
    }

    fun signOut() {
        viewModelScope.launch {
            _financialSyncState.value = FinancialSyncState.Unauthenticated
            savedStateHandle[SELECTED_TAB_KEY] = 0
            authRepository.signOut()
        }
    }

    val selectedTab: StateFlow<Int> = savedStateHandle.getStateFlow(SELECTED_TAB_KEY, 0)
    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow("All")

    val watchlistItems: StateFlow<List<WatchlistItem>> = shoppingRepository.watchlistItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val coupons: StateFlow<List<CouponItem>> = shoppingRepository.coupons
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val savingsRecords: StateFlow<List<SavingsRecord>> = shoppingRepository.savingsRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val invoices: StateFlow<List<InvoiceItem>> = financialSyncState
        .map { state ->
            state.latestScanOrNull?.invoices?.map { invoice ->
                InvoiceItem(
                    id = invoice.sourceMessageId.hashCode().toLong(),
                    providerName = invoice.providerName,
                    category = invoice.category,
                    monthlyCost = invoice.monthlyCost,
                    recommendedAlternative = "לא ידוע",
                    alternativeMonthlyCost = 0.0,
                    potentialMonthlySavings = 0.0,
                    status = "נמצא בסריקה הסמכותית",
                    isSwitchRequested = false,
                    dateAdded = 0L,
                    accountNumber = "",
                    billDate = invoice.receivedDate,
                    sourceMessageId = invoice.sourceMessageId,
                    sourceType = "GMAIL_READONLY",
                    verificationStatus = invoice.verificationStatus
                )
            }.orEmpty()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalMonthlySavingsPotential: StateFlow<Double> = shoppingRepository.totalMonthlySavingsPotential
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)
    val totalMonthlyCost: StateFlow<Double> = shoppingRepository.totalMonthlyCost
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)
    val totalSavings: StateFlow<Double> = shoppingRepository.totalSavingsAmount
        .map { it ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    fun submitProviderLead(
        invoice: InvoiceItem,
        contactName: String,
        phone: String,
        contactEmail: String,
        requestedProvider: String,
        consentAccepted: Boolean
    ) {
        if (!consentAccepted) {
            _providerLeadState.value = ProviderLeadUiState.Error(
                "נדרשת הסכמה מפורשת לשמירת הפרטים בתור הלידים."
            )
            return
        }
        viewModelScope.launch {
            _providerLeadState.value = ProviderLeadUiState.Submitting
            runCatching {
                backendRepository.createProviderLead(
                    ProviderLeadRequest(
                        contactName = contactName,
                        phone = phone,
                        contactEmail = contactEmail,
                        currentProvider = invoice.providerName,
                        requestedProvider = requestedProvider,
                        category = invoice.category,
                        invoiceLocalId = invoice.id.toString(),
                        idempotencyKey = "invoice-${invoice.id}-${phone.filter { it.isDigit() }}-${requestedProvider.hashCode()}"
                    )
                )
            }.onSuccess { result ->
                shoppingRepository.updateInvoice(
                    invoice.copy(
                        isSwitchRequested = true,
                        status = "ליד נשמר בתור הקליטה • ${result.leadId.take(8)}"
                    )
                )
                _providerLeadState.value = ProviderLeadUiState.Success(result)
            }.onFailure { error ->
                _providerLeadState.value = ProviderLeadUiState.Error(
                    error.localizedMessage ?: "שמירת הליד נכשלה."
                )
            }
        }
    }

    fun clearProviderLeadState() {
        _providerLeadState.value = ProviderLeadUiState.Idle
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
            shoppingRepository.addInvoice(
                InvoiceItem(
                    providerName = providerName.ifBlank { "ספק לא צוין" },
                    category = category,
                    monthlyCost = monthlyCost.coerceAtLeast(0.0),
                    recommendedAlternative = "טרם בוצעה השוואה מאומתת",
                    alternativeMonthlyCost = 0.0,
                    potentialMonthlySavings = 0.0,
                    status = "נוסף ידנית - ממתין לאימות",
                    sourceType = "MANUAL",
                    verificationStatus = "UNVERIFIED"
                )
            )
        }
        @Suppress("UNUSED_VARIABLE")
        val ignoredUnverifiedCalculation = "$recommendedAlternative|$alternativeCost|$savings"
    }

    fun deleteInvoice(id: Long) {
        viewModelScope.launch { shoppingRepository.deleteInvoice(id) }
    }

    val aiDealAnalysis = MutableStateFlow<DealAnalysisResult?>(null)
    val isAnalyzingDeal = MutableStateFlow(false)
    val chatMessages = MutableStateFlow(
        listOf(
            ChatMessage(
                text = "שירות ה-AI פועל דרך Backend מאומת. מחירים ותנאים עדיין דורשים מקור רשמי.",
                isUser = false
            )
        )
    )
    val isAiChatLoading = MutableStateFlow(false)
    val receiptScanResult = MutableStateFlow<ReceiptScanResult?>(null)
    val isReceiptScanning = MutableStateFlow(false)

    fun setTab(index: Int) {
        savedStateHandle[SELECTED_TAB_KEY] = index.coerceIn(0, 4)
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
            _aiErrorMessage.value = ""
            runCatching { geminiService.analyzeDeal(query) }
                .onSuccess { aiDealAnalysis.value = it }
                .onFailure { _aiErrorMessage.value = it.localizedMessage ?: "ניתוח AI נכשל." }
            isAnalyzingDeal.value = false
        }
    }

    fun sendChatMessage(userText: String) {
        if (userText.isBlank()) return
        chatMessages.value = chatMessages.value + ChatMessage(text = userText, isUser = true)
        viewModelScope.launch {
            isAiChatLoading.value = true
            val history = chatMessages.value.takeLast(6).joinToString("\n") {
                if (it.isUser) "User: ${it.text}" else "AI: ${it.text}"
            }
            runCatching { geminiService.chatWithAi(userText, history) }
                .onSuccess { reply ->
                    chatMessages.value = chatMessages.value + ChatMessage(text = reply, isUser = false)
                }
                .onFailure { error ->
                    chatMessages.value = chatMessages.value + ChatMessage(
                        text = error.localizedMessage ?: "שירות ה-AI אינו זמין כרגע.",
                        isUser = false
                    )
                }
            isAiChatLoading.value = false
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
            shoppingRepository.addWatchlistItem(
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
        viewModelScope.launch { shoppingRepository.removeWatchlistItem(id) }
    }

    fun copyCoupon(coupon: CouponItem) {
        viewModelScope.launch { shoppingRepository.incrementCouponCopy(coupon) }
    }

    fun toggleFavoriteCoupon(coupon: CouponItem) {
        viewModelScope.launch { shoppingRepository.toggleCouponFavorite(coupon) }
    }

    fun addSavingsRecord(
        title: String,
        store: String,
        amount: Double,
        category: String,
        note: String
    ) {
        if (amount <= 0.0) return
        viewModelScope.launch {
            shoppingRepository.addSavings(
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
            runCatching { geminiService.scanReceiptOrCoupon(bitmap) }
                .onSuccess { receiptScanResult.value = it }
            isReceiptScanning.value = false
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

    companion object {
        private const val SELECTED_TAB_KEY = "selected_primary_tab"
    }
}
