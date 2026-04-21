package utility

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

object PurchaseManager {

    const val PRODUCT_ID = "unlock_all" // TODO replace with real Google Play product ID before launch

    private var billingClient: BillingClient? = null
    private var onUnlocked: (() -> Unit)? = null

    fun initialize(activity: Activity, onUnlocked: () -> Unit) {
        this.onUnlocked = onUnlocked
        val appContext = activity.applicationContext
        billingClient = BillingClient.newBuilder(appContext)
            .setListener { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                    purchases.forEach { handlePurchase(activity, it) }
                }
            }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {}
            override fun onBillingServiceDisconnected() {}
        })
    }

    fun purchaseUnlockAll(activity: Activity) {
        val client = billingClient ?: return
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val queryParams = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()
        client.queryProductDetailsAsync(queryParams) { billingResult, productDetailsList ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val details = productDetailsList.firstOrNull() ?: return@queryProductDetailsAsync
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .build()
                    )
                )
                .build()
            activity.runOnUiThread { client.launchBillingFlow(activity, flowParams) }
        }
    }

    fun restorePurchases(activity: Activity, onResult: (Boolean) -> Unit) {
        val client = billingClient ?: run { activity.runOnUiThread { onResult(false) }; return }
        val queryParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(queryParams) { billingResult, purchases ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                activity.runOnUiThread { onResult(false) }
                return@queryPurchasesAsync
            }
            val matched = purchases.filter { it.products.contains(PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED }
            val found = matched.isNotEmpty()
            if (found) {
                Storage.saveUnlockProgress(100)
                matched.forEach { handlePurchase(activity, it) }
            }
            activity.runOnUiThread { onResult(found) }
        }
    }

    fun destroy() {
        billingClient?.endConnection()
        billingClient = null
        onUnlocked = null
    }

    private fun handlePurchase(activity: Activity, purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        Storage.saveUnlockProgress(100)
        activity.runOnUiThread { onUnlocked?.invoke() }
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient?.acknowledgePurchase(params) { _ -> }
        }
    }
}
