package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.data.repository.BackendInvoice
import com.example.data.repository.BackendRepository
import com.example.data.repository.FinancialOpportunity
import com.example.ui.theme.ClickAndSaveTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

private sealed interface ExactPushState {
    data object Loading : ExactPushState
    data class Bill(val invoice: BackendInvoice) : ExactPushState
    data class Opportunity(val opportunity: FinancialOpportunity) : ExactPushState
    data class Stale(val message: String) : ExactPushState
    data class Failed(val message: String) : ExactPushState
}

class PushTargetActivity : ComponentActivity() {
    private val backendRepository = BackendRepository()
    private var state by mutableStateOf<ExactPushState>(ExactPushState.Loading)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClickAndSaveTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (val current = state) {
                            ExactPushState.Loading -> CircularProgressIndicator()
                            is ExactPushState.Bill -> {
                                Text("החיוב המדויק", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(current.invoice.providerName)
                                Text("${current.invoice.monthlyCost} ₪")
                                Text(current.invoice.verificationStatus)
                            }
                            is ExactPushState.Opportunity -> {
                                Text("הזדמנות החיסכון המדויקת", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(current.opportunity.providerName)
                                current.opportunity.potentialMonthlySaving?.let { Text("חיסכון חודשי פוטנציאלי: $it ₪") }
                                Text(current.opportunity.status)
                            }
                            is ExactPushState.Stale -> Text(current.message)
                            is ExactPushState.Failed -> Text(current.message)
                        }
                        Button(onClick = { finish() }, modifier = Modifier.padding(top = 24.dp)) {
                            Text("סגור")
                        }
                    }
                }
            }
        }
        loadExactTarget()
    }

    private fun loadExactTarget() {
        if (FirebaseAuth.getInstance().currentUser == null) {
            state = ExactPushState.Stale("ההתראה אינה זמינה בחשבון הנוכחי. לא נפתח יעד אחר.")
            return
        }
        val type = intent.getStringExtra(PUSH_TYPE_EXTRA)?.trim().orEmpty()
        lifecycleScope.launch {
            state = runCatching {
                when (type) {
                    PUSH_TYPE_NEW_INVOICE -> {
                        val sourceMessageId = intent.getStringExtra("sourceMessageId")?.trim().orEmpty()
                        if (sourceMessageId.isBlank()) {
                            ExactPushState.Stale("החיוב שאליו הפנתה ההתראה אינו זמין עוד. לא נפתח חיוב אחר.")
                        } else {
                            val exact = backendRepository.scanGmailInvoices().invoices
                                .firstOrNull { it.sourceMessageId == sourceMessageId }
                            exact?.let(ExactPushState::Bill)
                                ?: ExactPushState.Stale("החיוב שאליו הפנתה ההתראה אינו זמין עוד. לא נפתח חיוב אחר.")
                        }
                    }
                    PUSH_TYPE_VERIFIED_SAVINGS_OPPORTUNITY -> {
                        val opportunityId = intent.getStringExtra(PUSH_OPPORTUNITY_ID_EXTRA)?.trim().orEmpty()
                        val offerId = intent.getStringExtra(PUSH_OFFER_ID_EXTRA)?.trim().orEmpty()
                        val exact = backendRepository.getFinancialHome().opportunities.firstOrNull {
                            it.id == opportunityId && it.matchedOffer?.offerId == offerId
                        }
                        exact?.let(ExactPushState::Opportunity)
                            ?: ExactPushState.Stale("הזדמנות החיסכון שאליה הפנתה ההתראה אינה זמינה עוד. לא נפתחה הזדמנות אחרת.")
                    }
                    else -> ExactPushState.Stale("יעד ההתראה אינו נתמך. לא נפתח יעד אחר.")
                }
            }.getOrElse {
                ExactPushState.Failed("לא ניתן לאמת כרגע את יעד ההתראה. לא נפתח יעד אחר.")
            }
        }
    }
}
