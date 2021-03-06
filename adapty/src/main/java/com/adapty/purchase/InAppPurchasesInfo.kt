package com.adapty.purchase

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.adapty.api.AdaptyError
import com.adapty.api.AdaptyErrorCode
import com.adapty.api.AdaptyPaywallsInfoCallback
import com.adapty.api.entity.paywalls.DataContainer
import com.adapty.api.entity.paywalls.ProductModel
import com.adapty.utils.LogHelper
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.SkuType.INAPP
import com.android.billingclient.api.BillingClient.SkuType.SUBS
import java.util.concurrent.Executors


class InAppPurchasesInfo(
    var context: Context,
    var purchases: ArrayList<Any>,
    var callback: AdaptyPaywallsInfoCallback
) {

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private var productIterator: MutableIterator<Any> = purchases.iterator()
    private lateinit var billingClient: BillingClient

    init {
        iterator()
    }

    private fun iterator() {

        if (!productIterator.hasNext()) {
            callback.onResult(purchases, null)
            return
        }

        setupBilling(productIterator.next())
    }

    private fun setupBilling(data: Any) {
        if (!::billingClient.isInitialized) {
            billingClient =
                BillingClient.newBuilder(context).enablePendingPurchases()
                    .setListener { billingResult, mutableList ->
                        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                            fail(AdaptyError(message = billingResult.debugMessage, adaptyErrorCode = AdaptyErrorCode.fromBilling(billingResult.responseCode)))
                        }
                    }
                    .build()
        }
        if (billingClient.isReady) {
            querySkuDetailsInApp(data)
        } else {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        querySkuDetailsInApp(data)
                    } else {
                        fail(AdaptyError(message = billingResult.debugMessage, adaptyErrorCode = AdaptyErrorCode.fromBilling(billingResult.responseCode)))
                    }
                }

                override fun onBillingServiceDisconnected() {
                    fail(AdaptyError(message = "onBillingServiceDisconnected", adaptyErrorCode = AdaptyErrorCode.BILLING_SERVICE_DISCONNECTED))
                }
            })
        }
    }

    private fun querySkuDetailsInApp(data: Any) {
        billingClient.querySkuDetailsAsync(
            getSkuList(data, INAPP).build()
        ) { result, skuDetailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {
                executor.submit {
                    fillInfo(skuDetailsList, data)
                    handler.post {
                        querySkuDetailsSubs(data)
                    }
                }
            } else
                fail(
                    AdaptyError(
                        message = "Unavailable querySkuDetailsInApp: ${result.debugMessage}",
                        adaptyErrorCode = AdaptyErrorCode.fromBilling(result.responseCode)
                    )
                )
        }
    }

    private fun querySkuDetailsSubs(data: Any) {
        billingClient.querySkuDetailsAsync(
            getSkuList(data, SUBS).build()
        ) { result, skuDetailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {
                executor.submit {
                    fillInfo(skuDetailsList, data)
                    handler.post {
                        iterator()
                    }
                }
            } else
                fail(
                    AdaptyError(
                        message = "Unavailable querySkuDetailsSubs: ${result.debugMessage}",
                        adaptyErrorCode = AdaptyErrorCode.fromBilling(result.responseCode)
                    )
                )
        }
    }

    private fun fillInfo(skuDetailsList: MutableList<SkuDetails>, data: Any) {
        for (skuDetails in skuDetailsList) {
            val sku = skuDetails.sku
            if (data is DataContainer) {
                data.attributes?.products?.forEach { p ->
                    if (sku == p.vendorProductId) {
                        p.setDetails(skuDetails)
                    }
                }
            } else if (data is ArrayList<*>) {
                (data as ArrayList<ProductModel>).forEach { p ->
                    if (sku == p.vendorProductId) {
                        p.setDetails(skuDetails)
                    }
                }
            }
        }
    }

    private fun getSkuList(data: Any, type: String): SkuDetailsParams.Builder {
        val skuList = when (data) {
            is DataContainer -> {
                data.attributes?.products?.mapNotNull { it.vendorProductId } ?: listOf()
            }
            is ArrayList<*> -> {
                data.filterIsInstance(ProductModel::class.java).mapNotNull { it.vendorProductId }
            }
            else -> {
                arrayListOf()
            }
        }

        return SkuDetailsParams.newBuilder().setSkusList(skuList).setType(type)
    }

    private fun fail(error: AdaptyError) {
        LogHelper.logError(error.message)
        callback.onResult(arrayListOf(), error)
    }
}