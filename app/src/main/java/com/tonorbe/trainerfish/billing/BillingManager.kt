package com.tonorbe.trainerfish.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.tonorbe.trainerfish.PremiumPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BillingManager : PurchasesUpdatedListener, BillingClientStateListener {

    // 👇 MUST match the product id in Play Console (managed product)
    private const val PRODUCT_PRO = "trainerfish_pro"

    private var appContext: Context? = null
    private var billingClient: BillingClient? = null

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    fun init(context: Context) {
        if (billingClient != null) return   // already initialised

        appContext = context.applicationContext

        val client = BillingClient.newBuilder(appContext!!)
            .enablePendingPurchases()
            .setListener(this)
            .build()

        billingClient = client
        client.startConnection(this)
    }

    fun shutdown() {
        billingClient?.endConnection()
        billingClient = null
    }

    // Read from prefs for initial value
    fun isProUnlocked(context: Context): Boolean {
        return PremiumPrefs(context).isPro || _isPro.value
    }

    private fun updatePro(isProNow: Boolean) {
        val ctx = appContext ?: return
        PremiumPrefs(ctx).isPro = isProNow
        _isPro.value = isProNow
    }

    // --- BillingClientStateListener ---

    override fun onBillingSetupFinished(result: BillingResult) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            queryExistingPurchases()
        }
    }

    override fun onBillingServiceDisconnected() {
        // Google recommends reconnecting lazily next time we need billing.
        // We'll just let init() be called again as needed.
        billingClient = null
    }

    // Check if user already owns Pro
    private fun queryExistingPurchases() {
        val client = billingClient ?: return
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync

            val hasPro = purchases.any { p ->
                p.products.contains(PRODUCT_PRO) &&
                        p.purchaseState == Purchase.PurchaseState.PURCHASED
            }

            if (hasPro) {
                purchases.forEach { maybeAcknowledge(it) }
                updatePro(true)
            }
        }
    }

    private fun maybeAcknowledge(purchase: Purchase) {
        val client = billingClient ?: return
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client.acknowledgePurchase(params) { /* ignore result for now */ }
        }
    }

    // --- Launch purchase flow from UI ---

    fun launchPurchase(activity: Activity) {
        val client = billingClient ?: return

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_PRO)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        client.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync

            val details = productDetailsList.firstOrNull() ?: return@queryProductDetailsAsync

            val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParams))
                .build()

            client.launchBillingFlow(activity, flowParams)
        }
    }

    // --- PurchasesUpdatedListener ---

    override fun onPurchasesUpdated(
        result: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases == null) return

        var gotPro = false

        for (purchase in purchases) {
            if (purchase.products.contains(PRODUCT_PRO)
                && purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            ) {
                gotPro = true
                maybeAcknowledge(purchase)
            }
        }

        if (gotPro) {
            updatePro(true)
        }
    }
}
