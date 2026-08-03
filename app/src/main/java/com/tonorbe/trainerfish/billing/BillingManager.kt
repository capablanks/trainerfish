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
    private var isConnecting = false
    private var pendingPurchaseActivity: java.lang.ref.WeakReference<Activity>? = null
    private var pendingRestoreCallback: ((Boolean) -> Unit)? = null

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext

        // Keep the already-verified entitlement when UI code calls init again
        // (for example before Restore Purchases).
        if (billingClient != null) return

        // Release policy: only Google Play Billing may unlock Pro.
        // Clear any legacy/local testing flag from older builds before the Play query returns.
        PremiumPrefs(appContext!!).isPro = false
        _isPro.value = false

        val client = BillingClient.newBuilder(appContext!!)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()

        billingClient = client
        startConnection(client)
    }

    private fun startConnection(client: BillingClient) {
        if (isConnecting) return
        isConnecting = true
        client.startConnection(this)
    }

    fun shutdown() {
        billingClient?.endConnection()
        billingClient = null
        isConnecting = false
        pendingPurchaseActivity = null
        pendingRestoreCallback = null
    }

    // Sole production Pro gate: this value is true only after Play Billing reports
    // the trainerfish_pro product as PURCHASED in this app session.
    fun isProUnlocked(context: Context): Boolean {
        return _isPro.value
    }

    private fun updatePro(isProNow: Boolean) {
        val ctx = appContext ?: return
        PremiumPrefs(ctx).isPro = isProNow
        _isPro.value = isProNow
    }

    // --- BillingClientStateListener ---

    override fun onBillingSetupFinished(result: BillingResult) {
        isConnecting = false
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            queryExistingPurchases()
            pendingPurchaseActivity?.get()?.let { activity ->
                pendingPurchaseActivity = null
                launchPurchase(activity)
            }

            pendingRestoreCallback?.let { callback ->
                pendingRestoreCallback = null
                restorePurchases(appContext ?: return@let, callback)
            }
        } else {
            pendingRestoreCallback?.let { callback ->
                pendingRestoreCallback = null
                callback(false)
            }
        }
    }

    override fun onBillingServiceDisconnected() {
        isConnecting = false
        // Billing 9's automatic service reconnection keeps this client usable and
        // reconnects before the next billing request. Do not discard the instance.
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

            val proPurchases = purchases.filter { p ->
                p.products.contains(PRODUCT_PRO) &&
                        p.purchaseState == Purchase.PurchaseState.PURCHASED
            }

            // Always update entitlement so refunds and revocations are honored.
            proPurchases.forEach { maybeAcknowledge(it) }
            updatePro(proPurchases.isNotEmpty())
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
        val client = billingClient
        if (client == null) {
            pendingPurchaseActivity = java.lang.ref.WeakReference(activity)
            init(activity.applicationContext)
            return
        }
        if (!client.isReady) {
            pendingPurchaseActivity = java.lang.ref.WeakReference(activity)
            startConnection(client)
            return
        }

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_PRO)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync

            val details = queryResult.productDetailsList.firstOrNull()
                ?: return@queryProductDetailsAsync

            val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParams))
                .build()

            client.launchBillingFlow(activity, flowParams)
        }
    }

    // --- Restore Purchases (manual user-triggered) ---
    fun restorePurchases(
        context: Context,
        onDone: (Boolean) -> Unit
    ) {
        appContext = context.applicationContext
        val client = billingClient ?: run {
            pendingRestoreCallback = onDone
            init(context)
            return
        }
        if (!client.isReady) {
            pendingRestoreCallback = onDone
            startConnection(client)
            return
        }

        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->

            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                onDone(false)
                return@queryPurchasesAsync
            }

            val proPurchases = purchases.filter { p ->
                p.products.contains(PRODUCT_PRO) &&
                        p.purchaseState == Purchase.PurchaseState.PURCHASED
            }

            // Always update entitlement so refunds and revocations are honored.
            proPurchases.forEach { maybeAcknowledge(it) }
            val hasPro = proPurchases.isNotEmpty()
            updatePro(hasPro)
            onDone(hasPro)
        }
    }


    // --- PurchasesUpdatedListener ---

    override fun onPurchasesUpdated(
        result: BillingResult,
        purchases: List<Purchase>?
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
