package com.societyconnect.billing

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

// One Play subscription product with two base plans (monthly/yearly). These
// ids must exactly match what's created in Play Console — pricing itself is
// configured there, not hardcoded here, so it's read live via priceFor().
class BillingManager(
    private val activity: Activity,
    private val onPurchased: (plan: String) -> Unit
) : PurchasesUpdatedListener {

    private var productDetails: ProductDetails? = null
    private var pendingPlan: String? = null

    val isReady: Boolean get() = productDetails != null

    private val client = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun start(onProductsReady: (ProductDetails?) -> Unit) {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    onProductsReady(null)
                    return
                }
                queryProductDetails(onProductsReady)
                acknowledgePendingPurchases()
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun queryProductDetails(onProductsReady: (ProductDetails?) -> Unit) {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        client.queryProductDetailsAsync(params) { _, list ->
            productDetails = list.firstOrNull()
            onProductsReady(productDetails)
        }
    }

    // Play auto-refunds any purchase left unacknowledged for too long — catch
    // anything our PurchasesUpdatedListener missed (e.g. process death mid-flow).
    private fun acknowledgePendingPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        client.queryPurchasesAsync(params) { _, purchases ->
            purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
                .forEach { purchase ->
                    val ackParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    client.acknowledgePurchase(ackParams) {}
                }
        }
    }

    fun priceFor(plan: String): String? =
        offerFor(plan)?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice

    fun priceMicrosFor(plan: String): Long? =
        offerFor(plan)?.pricingPhases?.pricingPhaseList?.firstOrNull()?.priceAmountMicros

    private fun offerFor(plan: String): ProductDetails.SubscriptionOfferDetails? {
        val basePlanId = if (plan == "YEARLY") BASE_PLAN_YEARLY else BASE_PLAN_MONTHLY
        return productDetails?.subscriptionOfferDetails?.firstOrNull { it.basePlanId == basePlanId }
    }

    fun launchPurchase(plan: String) {
        val details = productDetails ?: return
        val offer = offerFor(plan) ?: return
        pendingPlan = plan
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offer.offerToken)
                        .build()
                )
            )
            .build()
        client.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val plan = pendingPlan ?: "MONTHLY"
        pendingPlan = null
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases == null) return

        for (purchase in purchases) {
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            if (!purchase.isAcknowledged) {
                val ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                client.acknowledgePurchase(ackParams) {}
            }
            onPurchased(plan)
        }
    }

    fun openManageSubscriptions() {
        val uri = Uri.parse(
            "https://play.google.com/store/account/subscriptions?sku=$PRODUCT_ID&package=${activity.packageName}"
        )
        activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    fun endConnection() = client.endConnection()

    companion object {
        const val PRODUCT_ID = "society_connect_pro"
        const val BASE_PLAN_MONTHLY = "monthly"
        const val BASE_PLAN_YEARLY = "yearly"
    }
}
