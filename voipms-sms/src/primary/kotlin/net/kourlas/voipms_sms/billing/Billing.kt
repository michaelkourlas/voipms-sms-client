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
                client.queryPurchasesAsync(SKU) { result, purchases ->
                    if (result.responseCode == BillingResponseCode.OK) {
                        it.resume(purchases)
                    } else {
                        it.resume(emptyList())
                    }
                }
            }
            consumeDonationPurchases(purchasesList)

            // Get the SKU.
            val skuDetails = getCoffeeSkuDetails()
            if (skuDetails == null) {
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
                .setSkuDetails(skuDetails)
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
                consumeDonationPurchases(purchases)
            }
        }
    }

    override fun onBillingSetupFinished(result: BillingResult) {
        connected = result.responseCode == BillingResponseCode.OK
    }

    override fun onBillingServiceDisconnected() {
        connected = false
    }

    private suspend fun consumeDonationPurchases(purchases: List<Purchase>) =
        withContext(Dispatchers.IO) {
            for (purchase in purchases) {
                if (purchase.skus.contains(SKU)
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

    private suspend fun getCoffeeSkuDetails() = withContext(Dispatchers.IO) {
        val skuDetailsParams = SkuDetailsParams.newBuilder()
        skuDetailsParams
            .setSkusList(listOf(SKU))
            .setType(BillingClient.SkuType.INAPP)
        val skuDetailsList = suspendCoroutine<List<SkuDetails>> {
            client.querySkuDetailsAsync(
                skuDetailsParams.build()
            ) { result, details ->
                if (result.responseCode == BillingResponseCode.OK
                    && details != null
                ) {
                    it.resume(details)
                } else {
                    it.resume(emptyList())
                }
            }
        }
        if (skuDetailsList.size == 1 && skuDetailsList[0].sku == SKU) {
            skuDetailsList[0]
        } else {
            null
        }
    }

    companion object {
        // It is not a leak to store an instance to the application context,
        // since it has the same lifetime as the application itself.
        @SuppressLint("StaticFieldLeak")
        private var instance: Billing? = null

        // The SKU for buying me a coffee.
        private const val SKU = "coffee"

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