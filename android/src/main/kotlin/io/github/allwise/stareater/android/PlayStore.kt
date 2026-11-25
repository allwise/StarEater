package io.github.allwise.stareater.android

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import io.github.allwise.stareater.Store
import io.github.allwise.stareater.StoreListener

/**
 * The full-game unlock through Google Play Billing: one non-consumable in-app product, [PRODUCT].
 *
 * Ownership is asked for as soon as the store is reached, which is also what restores the unlock
 * after a reinstall or on a new phone. A purchase is acknowledged straight away (Play refunds any
 * left unacknowledged for three days). Only a clean answer from Play ever takes the unlock away -
 * a refund - so playing offline keeps whatever the profile last heard.
 */
class PlayStore(private val activity: Activity) : Store, PurchasesUpdatedListener {
    override val sells = true

    private var listener: StoreListener? = null
    private var details: ProductDetails? = null

    private val client: BillingClient = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override fun connect(listener: StoreListener) {
        this.listener = listener
        // The game calls this from its own thread; the billing client is happiest on the UI one.
        activity.runOnUiThread { start() }
    }

    private fun start() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingResponseCode.OK) {
                    fetchDetails()
                    check(sayIfNone = false)
                }
            }

            // The client reconnects by itself on the next call.
            override fun onBillingServiceDisconnected() {}
        })
    }

    override fun buy() {
        val d = details
        if (d == null) {
            listener?.note("THE STORE IS NOT READY YET - TRY AGAIN IN A MOMENT")
            fetchDetails()
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .build()
        activity.runOnUiThread { client.launchBillingFlow(activity, params) }
    }

    override fun restore() = check(sayIfNone = true)

    fun close() = client.endConnection()

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> settle(purchases.orEmpty(), fromQuery = false, sayIfNone = false)
            BillingResponseCode.ITEM_ALREADY_OWNED -> check(sayIfNone = false)
            BillingResponseCode.USER_CANCELED -> {}
            else -> listener?.note("THE PURCHASE DID NOT GO THROUGH (CODE ${result.responseCode})")
        }
    }

    private fun fetchDetails() {
        val product = QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT).setProductType(ProductType.INAPP).build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        client.queryProductDetailsAsync(params) { result, found ->
            if (result.responseCode != BillingResponseCode.OK) return@queryProductDetailsAsync
            details = found.productDetailsList.firstOrNull()
            details?.oneTimePurchaseOfferDetails?.formattedPrice?.let { listener?.price(it) }
        }
    }

    private fun check(sayIfNone: Boolean) {
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingResponseCode.OK) settle(purchases, fromQuery = true, sayIfNone = sayIfNone)
            else if (sayIfNone) listener?.note("COULD NOT REACH GOOGLE PLAY - TRY AGAIN LATER")
        }
    }

    private fun settle(purchases: List<Purchase>, fromQuery: Boolean, sayIfNone: Boolean) {
        val mine = purchases.filter { PRODUCT in it.products }
        val bought = mine.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (bought != null) {
            if (!bought.isAcknowledged) {
                val ack = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(bought.purchaseToken).build()
                client.acknowledgePurchase(ack) { }
            }
            listener?.owned(true)
            return
        }
        if (mine.any { it.purchaseState == Purchase.PurchaseState.PENDING }) {
            listener?.note("PAYMENT PENDING - THE FULL GAME UNLOCKS WHEN IT CLEARS")
            return
        }
        // Only the full list of what the account owns can say "not bought" (a refund, say).
        if (fromQuery) {
            listener?.owned(false)
            if (sayIfNone) listener?.note("NO PURCHASE FOUND ON THIS GOOGLE ACCOUNT")
        }
    }

    companion object {
        /** The product's id in the Play Console. Permanent once created there. */
        const val PRODUCT = "full_game"
    }
}
