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
import kotlinx.coroutines.flow.*
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

    val isSyncingGmail = MutableStateFlow(false)
    val gmailSyncStep = MutableStateFlow("")

    init {
        viewModelScope.launch {
            gmailRepository.syncState.collect { state ->
                when (state) {
                    is GmailSyncState.Syncing -> {
                        isSyncingGmail.value = true
                        gmailSyncStep.value = state.stepMessage
                    }
                    is GmailSyncState.Success -> {
                        isSyncingGmail.value = false
                        gmailSyncStep.value = state.message
                    }
                    is GmailSyncState.Error -> {
                        isSyncingGmail.value = false
                        gmailSyncStep.value = state.errorMessage
                    }
                    is GmailSyncState.Idle -> {
                        isSyncingGmail.value = false
                        gmailSyncStep.value = ""
                    }
                }
            }
        }
    }

    // User Preferences & Savings Goal State
    val monthlySavingsGoal = MutableStateFlow(2000.0)
    val preferredElectricityProvider = MutableStateFlow("אלקטרה פאוור")
    val preferredCellularProvider = MutableStateFlow("019 מובייל")
    val preferredInternetProvider = MutableStateFlow("בזק סיבים")
    val preferredInsuranceProvider = MutableStateFlow("הראל ביטוח")
    val preferredStreamingProvider = MutableStateFlow("FreeTV")
    val autoSwitchAlertsEnabled = MutableStateFlow(true)
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
        monthlySavingsGoal.value = goal
        preferredElectricityProvider.value = electricity
        preferredCellularProvider.value = cellular
        preferredInternetProvider.value = internet
        preferredInsuranceProvider.value = insurance
        preferredStreamingProvider.value = streaming
        autoSwitchAlertsEnabled.value = autoSwitch
        minSavingsThreshold.value = minThreshold
    }

    // Daily Savings Tips Data & State
    val dailyTips = listOf(
        DailyTip(
            id = 1,
            title = "אופטימיזציית חשמל במסלול הייטק / לילה",
            category = "חשמל",
            iconSymbol = "⚡",
            description = "מעבר למסלול חשמל מוזל בשעות העומס או מסלול הייטק מעניק 5%-7% הנחה קבועה על כל חשבון החשמל הביתי.",
            estimatedMonthlySavings = 85.0,
            actionText = "עבור לאלקטרה פאוור"
        ),
        DailyTip(
            id = 2,
            title = "איחוד קווי סלולר למשפחה",
            category = "סלולר",
            iconSymbol = "📱",
            description = "ניוד 3 קווי סלולר לחבילה משפחתית מורחבת ב-019 מובייל או Wecom יוריד את התשלום החודשי מ-₪90 ל-₪30 בלבד.",
            estimatedMonthlySavings = 60.0,
            actionText = "השווה חבילות סלולר"
        ),
        DailyTip(
            id = 3,
            title = "ביטול כפילויות ביטוח בריאות ותאונות",
            category = "ביטוח",
            iconSymbol = "🛡️",
            description = "סריקה אוטומטית ב'הר הביטוח' מזהה כפילויות בפוליסות פרטיות וקופות חולים המביאות לבזבוז מיותר.",
            estimatedMonthlySavings = 140.0,
            actionText = "סרוק בהר הביטוח"
        ),
        DailyTip(
            id = 4,
            title = "שדרוג לסיבים ללא דמי שכירות ראוטר",
            category = "אינטרנט",
            iconSymbol = "🌐",
            description = "מעבר לתשתית בזק סיבים או סלקום פייבר הכוללת פטור מדמי שכירות נתב חודשיים.",
            estimatedMonthlySavings = 35.0,
            actionText = "בדוק סיבים בכתובתך"
        ),
        DailyTip(
            id = 5,
            title = "פטור מעמלות דמי כרטיס אשראי",
            category = "משק בית",
            iconSymbol = "💳",
            description = "המרת כרטיסי האשראי הבנקאיים לכרטיסי מועדון ללא עמלת דמי כרטיס חודשיים למשך שנתיים.",
            estimatedMonthlySavings = 40.0,
            actionText = "בקש פטור מדמי כרטיס"
        ),
        DailyTip(
            id = 6,
            title = "ניקוי מנויי TV וסטרימינג שאינם בשימוש",
            category = "טלוויזיה",
            iconSymbol = "📺",
            description = "ביטול ערוצי פרימיום ומנויי תוכן שהתווספו אוטומטית בתום תקופת הטבה ראשונית.",
            estimatedMonthlySavings = 55.0,
            actionText = "בדוק מנויים פעילים"
        )
    )

    val currentTipIndex = MutableStateFlow(0)

    fun nextDailyTip() {
        currentTipIndex.value = (currentTipIndex.value + 1) % dailyTips.size
    }

    fun prevDailyTip() {
        val prev = currentTipIndex.value - 1
        currentTipIndex.value = if (prev < 0) dailyTips.size - 1 else prev
    }

    // Push notification simulation state
    val showPushBanner = MutableStateFlow(false)
    val pushNotificationTitle = MutableStateFlow("🔔 חשבונית חשמל חדשה שורבבה למייל (₪450)")
    val pushNotificationBody = MutableStateFlow("מצאנו חלופה ב-₪418/חודש! לחץ לצפייה ומעבר בקליק")

    // Price Hike Saver Alerts (שומר מסך מפני התייקרויות - התראה 14 יום לפני פקיעה)
    val priceHikeAlerts = MutableStateFlow(
        listOf(
            PriceHikeAlertItem(
                id = 1L,
                serviceName = "פלאפון סלולר",
                planName = "חבילת 100GB ללא הגבלה",
                daysUntilExpiry = 14,
                expiryDate = "10/08/2026",
                currentPrice = 29.90,
                expectedPriceAfterHike = 69.90,
                alternativeProvider = "סלקום סלולר",
                alternativePrice = 25.00,
                category = "סלולר"
            ),
            PriceHikeAlertItem(
                id = 2L,
                serviceName = "פרטנר אינטרנט סיבים",
                planName = "חבילת 1000Mb סיבים אופטיים",
                daysUntilExpiry = 14,
                expiryDate = "10/08/2026",
                currentPrice = 69.00,
                expectedPriceAfterHike = 119.00,
                alternativeProvider = "בזק סיבים (Bezeq Fiber)",
                alternativePrice = 65.00,
                category = "אינטרנט/סיבים"
            )
        )
    )

    fun triggerSimulatedPushNotification() {
        showPushBanner.value = true
    }

    fun triggerPriceHikeSimulatedPush() {
        pushNotificationTitle.value = "🛡️ שומר מסך: התראת התייקרות בעוד 14 ימים!"
        pushNotificationBody.value = "מבצע הסלולר ב-פלאפון (₪29.90) פוקע בעוד 14 ימים והמחיר יקפוץ ל-₪69.90. לחץ עכשיו למעבר לספק מוזל ב-₪25/חודש!"
        showPushBanner.value = true
    }

    fun dismissPushBanner() {
        showPushBanner.value = false
    }

    fun connectGmail(email: String = "vadim.hanukaev1981@gmail.com") {
        viewModelScope.launch {
            val token = userSession.value.gmailOAuthAccessToken
            gmailRepository.fetchAndParseInvoices(accessToken = token, userEmail = email)
        }
    }

    fun disconnectGmail() {
        gmailRepository.disconnectGmail()
    }

    fun triggerGmailSync() {
        viewModelScope.launch {
            val token = userSession.value.gmailOAuthAccessToken
            val email = userSession.value.email.ifBlank { "vadim.hanukaev1981@gmail.com" }
            gmailRepository.fetchAndParseInvoices(accessToken = token, userEmail = email)
        }
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val coupons: StateFlow<List<CouponItem>> = repository.coupons
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savingsRecords: StateFlow<List<SavingsRecord>> = repository.savingsRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val invoices: StateFlow<List<InvoiceItem>> = repository.invoices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalMonthlySavingsPotential: StateFlow<Double> = repository.totalMonthlySavingsPotential
        .map { it ?: 464.40 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 464.40)

    val totalMonthlyCost: StateFlow<Double> = repository.totalMonthlyCost
        .map { it ?: 3229.00 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3229.00)

    val totalSavings: StateFlow<Double> = repository.totalSavingsAmount
        .map { it ?: 129.50 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 129.50)

    fun requestProviderSwitch(invoice: InvoiceItem) {
        viewModelScope.launch {
            repository.requestProviderSwitch(invoice)
        }
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
            val newInvoice = InvoiceItem(
                providerName = providerName,
                category = category,
                monthlyCost = monthlyCost,
                recommendedAlternative = recommendedAlternative,
                alternativeMonthlyCost = alternativeCost,
                potentialMonthlySavings = savings,
                status = "פוענח - הצעה מוכנה",
                isSwitchRequested = false
            )
            repository.addInvoice(newInvoice)
        }
    }

    fun deleteInvoice(id: Long) {
        viewModelScope.launch {
            repository.deleteInvoice(id)
        }
    }


    val aiDealAnalysis = MutableStateFlow<DealAnalysisResult?>(null)
    val isAnalyzingDeal = MutableStateFlow(false)

    val chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = "שלום! אני סייען ה-AI של Click & Save AI. שאל אותי לגבי קופונים, הוזלת חשבוניות חשמל/תקשורת/ביטוח, או השוואת מחירים!",
                isUser = false
            )
        )
    )
    val isAiChatLoading = MutableStateFlow(false)

    val receiptScanResult = MutableStateFlow<ReceiptScanResult?>(null)
    val isReceiptScanning = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            repository.seedSampleDataIfNeeded()
        }
    }

    fun setTab(index: Int) {
        selectedTab.value = index
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
            val result = geminiService.analyzeDeal(query)
            aiDealAnalysis.value = result
            isAnalyzingDeal.value = false
        }
    }

    fun sendChatMessage(userText: String) {
        if (userText.isBlank()) return
        val currentList = chatMessages.value.toMutableList()
        val userMsg = ChatMessage(text = userText, isUser = true)
        currentList.add(userMsg)
        chatMessages.value = currentList

        viewModelScope.launch {
            isAiChatLoading.value = true
            val history = currentList.takeLast(6).joinToString("\n") {
                if (it.isUser) "User: ${it.text}" else "AI: ${it.text}"
            }
            val reply = geminiService.chatWithAi(userText, history)
            val updated = chatMessages.value.toMutableList()
            updated.add(ChatMessage(text = reply, isUser = false))
            chatMessages.value = updated
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
            val item = WatchlistItem(
                name = name,
                storeName = store,
                originalPrice = originalPrice,
                currentPrice = currentPrice,
                targetPrice = targetPrice,
                category = category,
                priceHistoryCsv = "${originalPrice.toInt()},${((originalPrice + currentPrice)/2).toInt()},${currentPrice.toInt()}"
            )
            repository.addWatchlistItem(item)
        }
    }

    fun deleteWatchlistItem(id: Long) {
        viewModelScope.launch {
            repository.removeWatchlistItem(id)
        }
    }

    fun copyCoupon(coupon: CouponItem) {
        viewModelScope.launch {
            repository.incrementCouponCopy(coupon)
        }
    }

    fun toggleFavoriteCoupon(coupon: CouponItem) {
        viewModelScope.launch {
            repository.toggleCouponFavorite(coupon)
        }
    }

    fun addSavingsRecord(title: String, store: String, amount: Double, category: String, note: String) {
        viewModelScope.launch {
            val record = SavingsRecord(
                title = title,
                storeName = store,
                amountSaved = amount,
                category = category,
                note = note
            )
            repository.addSavings(record)
        }
    }

    fun scanReceipt(bitmap: Bitmap) {
        viewModelScope.launch {
            isReceiptScanning.value = true
            val result = geminiService.scanReceiptOrCoupon(bitmap)
            receiptScanResult.value = result
            isReceiptScanning.value = false
            
            // Auto add to savings log
            addSavingsRecord(
                title = "Receipt Match: ${result.storeName}",
                store = result.storeName,
                amount = result.estimatedSavings,
                category = "Scanned Receipt",
                note = result.itemSummary
            )
        }
    }

    fun clearReceiptResult() {
        receiptScanResult.value = null
    }
}
