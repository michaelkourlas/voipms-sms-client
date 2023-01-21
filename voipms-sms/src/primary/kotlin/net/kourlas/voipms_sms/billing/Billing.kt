/*
 * VoIP.ms SMS
 * Copyright (C) 2021 Michael Kourlas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.kourlas.voipms_sms.billing

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.fragment.app.FragmentActivity
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.Purchase.PurchaseState
import kotlinx.coroutines.*
import net.kourlas.voipms_sms.R
import net.kourlas.voipms_sms.utils.logException
import net.kourlas.voipms_sms.utils.showSnackbar
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class Billing(private val context: Context) : PurchasesUpdatedListener,
    BillingClientStateListener {
    private val client =
        BillingClient.newBuilder(context)
            .enablePendingPurchases()
            .setListener(this)
            .build()
    private var connected = false

    init {
        // Initialize the billing client when the singleton is first
        // initialized.
        try {
            client.startConnection(this)
        } catch (e: Exception) {
            logException(e)
        }
    }

    suspend fun askForCoffee(activity: FragmentActivity) {
        try {
            // If the client is not connected, we can't do anything.
            if (!connected) {
                showSnackbar(
                    activity, R.id.coordinator_layout,
                    activity.getString(
                        R.string.coffee_fail_google_play
                    )
                )
                return
            }

            // Consume the purchase if it hasn't been consumed yet.
            val purchasesList = suspendCoroutine<List<Purchase>> {
                client.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder().setProductType(
                        BillingClient.ProductType.INAPP
                    ).build()
                ) { result, purchases ->
                    if (result.responseCode == BillingResponseCode.OK) {
                        it.resume(purchases)
                    } else {
                        it.resume(emptyList())
                    }
                }
            }
            consumeCoffeePurchases(purchasesList)

            // Get the SKU.
            val productDetails = getProductDetails()
            if (productDetails == null) {
                showSnackbar(
                    activity, R.id.coordinator_layout,
                    activity.getString(
                        R.string.coffee_fail_unknown
                    )
                )
                return
            }

            // Open the purchase flow for that SKU.
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(
                                productDetails
                            ).build()
                    )
                )
                .build()
            client.launchBillingFlow(activity, flowParams)
        } catch (e: Exception) {
            logException(e)
            showSnackbar(
                activity, R.id.coordinator_layout,
                activity.getString(
                    R.string.coffee_fail_unknown
                )
            )
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onPurchasesUpdated(
        result: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        if (result.responseCode == BillingResponseCode.OK
            && purchases != null
        ) {
            GlobalScope.launch {
                consumeCoffeePurchases(purchases)
            }
        }
    }

    override fun onBillingSetupFinished(result: BillingResult) {
        connected = result.responseCode == BillingResponseCode.OK
    }

    override fun onBillingServiceDisconnected() {
        connected = false
    }

    private suspend fun consumeCoffeePurchases(purchases: List<Purchase>) =
        withContext(Dispatchers.IO) {
            for (purchase in purchases) {
                if ((purchase.products.contains(PRODUCT_ID) || LEGACY_PRODUCT_IDS.any {
                        purchase.products.contains(
                            it
                        )
                    })
                    && purchase.purchaseState == PurchaseState.PURCHASED
                ) {
                    val coffeeCompleteBroadcastIntent = Intent(
                        context.getString(
                            R.string.coffee_complete_action
                        )
                    )
                    context.sendBroadcast(coffeeCompleteBroadcastIntent)

                    val consumeParams = ConsumeParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken).build()
                    suspendCoroutine<Unit> {
                        client.consumeAsync(consumeParams) { _, _ ->
                            it.resume(Unit)
                        }
                    }
                }

            }
        }

    private suspend fun getProductDetails() = withContext(Dispatchers.IO) {
        val queryProductDetailsParams =
            QueryProductDetailsParams.newBuilder().setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder().setProductId(
                        PRODUCT_ID
                    ).setProductType(BillingClient.ProductType.INAPP).build()
                )
            ).build()
        val productDetailsList = suspendCoroutine<List<ProductDetails>> {
            client.queryProductDetailsAsync(
                queryProductDetailsParams
            ) { result, details ->
                if (result.responseCode == BillingResponseCode.OK) {
                    it.resume(details)
                } else {
                    it.resume(emptyList())
                }
            }
        }
        if (productDetailsList.size == 1 && productDetailsList[0].productId == PRODUCT_ID) {
            productDetailsList[0]
        } else {
            null
        }
    }

    companion object {
        // It is not a leak to store an instance to the application context,
        // since it has the same lifetime as the application itself.
        @SuppressLint("StaticFieldLeak")
        private var instance: Billing? = null

        // The "buy me a coffee" product.
        private const val PRODUCT_ID = "coffee"

        // Previous versions of the "buy me a coffee" product.
        private val LEGACY_PRODUCT_IDS = listOf("donation", "donation2")

        /**
         * Gets the sole instance of the Billing class. Initializes the
         * instance if it does not already exist.
         */
        fun getInstance(context: Context): Billing =
            instance ?: synchronized(this) {
                instance ?: Billing(
                    context.applicationContext
                ).also { instance = it }
            }
    }
}