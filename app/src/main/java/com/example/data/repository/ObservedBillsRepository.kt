package com.example.data.repository

import com.example.data.local.InvoiceItem
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

data class ObservedBillsSnapshot(
    val bills: List<BackendInvoice>,
    val sourceMessageIds: List<String>,
    val sourceSetComplete: Boolean,
    val sourceCount: Int,
    val generatedAt: String
)

data class ObservedBillsRefreshResult(
    val refreshedBills: Int,
    val removedStaleSources: Int,
    val sourceSetComplete: Boolean
)

internal fun computeStaleObservedSourceIds(
    localSourceIds: List<String>,
    authoritativeSourceIds: List<String>,
    sourceSetComplete: Boolean
): List<String> {
    if (!sourceSetComplete) return emptyList()
    val authoritative = authoritativeSourceIds
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toSet()
    return localSourceIds
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .filterNot(authoritative::contains)
}

internal fun BackendInvoice.toObservedInvoiceItem(): InvoiceItem = InvoiceItem(
    providerName = providerName,
    category = category,
    monthlyCost = monthlyCost,
    billDate = receivedDate,
    sourceMessageId = sourceMessageId,
    sourceType = "GMAIL_READONLY",
    verificationStatus = verificationStatus,
    recommendedAlternative = "טרם בוצעה השוואה מאומתת",
    alternativeMonthlyCost = 0.0,
    potentialMonthlySavings = 0.0,
    status = "נבדק אוטומטית"
)

class ObservedBillsRepository(
    private val shoppingRepository: ShoppingRepository,
    private val functionsProvider: () -> FirebaseFunctions = {
        FirebaseFunctions.getInstance("europe-west1")
    }
) {
    private val functions: FirebaseFunctions by lazy(functionsProvider)

    suspend fun fetchSnapshot(): ObservedBillsSnapshot {
        val response = functions.getHttpsCallable("getObservedBills")
            .call()
            .await()
            .data
            .asObservedStringMap()

        val bills = (response["bills"] as? List<*>)
            .orEmpty()
            .mapNotNull(::parseBackendInvoice)
        val sourceMessageIds = (response["sourceMessageIds"] as? List<*>)
            .orEmpty()
            .mapNotNull { it as? String }
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()

        return ObservedBillsSnapshot(
            bills = bills,
            sourceMessageIds = sourceMessageIds,
            sourceSetComplete = response["sourceSetComplete"] as? Boolean ?: false,
            sourceCount = (response["sourceCount"] as? Number)?.toInt() ?: sourceMessageIds.size,
            generatedAt = response["generatedAt"] as? String ?: ""
        )
    }

    suspend fun refreshObservedBills(): ObservedBillsRefreshResult {
        val snapshot = fetchSnapshot()

        snapshot.bills.forEach { bill ->
            shoppingRepository.upsertObservedGmailInvoice(bill.toObservedInvoiceItem())
        }

        val localSourceIds = shoppingRepository.getObservedGmailSourceIds()
        val stale = computeStaleObservedSourceIds(
            localSourceIds = localSourceIds,
            authoritativeSourceIds = snapshot.sourceMessageIds,
            sourceSetComplete = snapshot.sourceSetComplete
        )
        shoppingRepository.deleteObservedGmailInvoicesBySourceIds(stale)

        return ObservedBillsRefreshResult(
            refreshedBills = snapshot.bills.size,
            removedStaleSources = stale.size,
            sourceSetComplete = snapshot.sourceSetComplete
        )
    }

    private fun parseBackendInvoice(item: Any?): BackendInvoice? {
        val map = item.asObservedStringMapOrNull() ?: return null
        val sourceMessageId = (map["sourceMessageId"] as? String)?.trim().orEmpty()
        val providerName = (map["providerName"] as? String)?.trim().orEmpty()
        val monthlyCost = (map["monthlyCost"] as? Number)?.toDouble()
        if (sourceMessageId.isEmpty() || providerName.isEmpty() || monthlyCost == null || !monthlyCost.isFinite() || monthlyCost <= 0.0) {
            return null
        }
        return BackendInvoice(
            sourceMessageId = sourceMessageId,
            providerName = providerName,
            category = (map["category"] as? String)?.trim().orEmpty().ifBlank { "other" },
            monthlyCost = monthlyCost,
            receivedDate = map["receivedDate"] as? String ?: "",
            verificationStatus = map["verificationStatus"] as? String ?: "UNVERIFIED_GMAIL_IMPORT"
        )
    }
}

private fun Any?.asObservedStringMap(): Map<String, Any?> =
    asObservedStringMapOrNull() ?: error("Unexpected observed bills backend response")

private fun Any?.asObservedStringMapOrNull(): Map<String, Any?>? {
    val raw = this as? Map<*, *> ?: return null
    return raw.entries.associate { (key, value) -> key.toString() to value }
}
