package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.CouponItem
import com.example.ui.MainViewModel
import com.example.ui.theme.AiVioletPrimary
import com.example.ui.theme.EmeraldSavings

@Composable
fun CouponsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val coupons by viewModel.coupons.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    var couponSearch by remember { mutableStateOf("") }

    val categories = listOf("הכל", "טכנולוגיה", "אופנה", "סופרמרקט", "בריאות")

    val filteredCoupons = coupons.filter { coupon ->
        val matchesCategory = if (selectedCategory == "הכל" || selectedCategory == "All") true else coupon.category.contains(selectedCategory, ignoreCase = true)
        val matchesSearch = if (couponSearch.isBlank()) true else {
            coupon.storeName.contains(couponSearch, ignoreCase = true) ||
            coupon.promoCode.contains(couponSearch, ignoreCase = true) ||
            coupon.discountText.contains(couponSearch, ignoreCase = true)
        }
        matchesCategory && matchesSearch
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("coupons_screen")
    ) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "קופונים פעילים והחזר כספי (Cashback)",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "קודי קופון מאומתים בזמן אמת עם העתקה בלחיצה אחת",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = couponSearch,
                    onValueChange = { couponSearch = it },
                    placeholder = { Text("חפש לפי שם חנות (למשל אמזון, נייקי, KSP)...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("coupon_search_input"),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { viewModel.setCategoryFilter(cat) },
                            label = { Text(cat) }
                        )
                    }
                }
            }
        }

        if (filteredCoupons.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LocalOffer,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "לא נמצאו קופונים מתאימים",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp, start = 16.dp, end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredCoupons, key = { it.id }) { coupon ->
                    CouponDetailCard(
                        coupon = coupon,
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Promo Code", coupon.promoCode))
                            viewModel.copyCoupon(coupon)
                            Toast.makeText(context, "הקוד '${coupon.promoCode}' הועתק בהצלחה!", Toast.LENGTH_SHORT).show()
                        },
                        onToggleFavorite = {
                            viewModel.toggleFavoriteCoupon(coupon)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CouponDetailCard(
    coupon: CouponItem,
    onCopy: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = AiVioletPrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = coupon.storeName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = AiVioletPrimary
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    if (coupon.cashbackPercent > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = EmeraldSavings.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "+${coupon.cashbackPercent}% החזר (Cashback)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldSavings
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (coupon.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                        contentDescription = "מועדף",
                        tint = if (coupon.isFavorite) Color(0xFFFFB800) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = coupon.discountText,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = null,
                        tint = EmeraldSavings,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = coupon.verifiedStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldSavings
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = coupon.expirationNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "${coupon.copyCount} שימושים",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onCopy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSavings)
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "העתק קוד קופון: ${coupon.promoCode}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
