package org.futo.voiceinput.payments

import android.content.Context
import kotlinx.coroutines.CoroutineScope

/**
 * No-op BillingManager for the Lynx fork.
 * Both PlayBilling.isAllowed() and PayPalBilling.isAllowed() return false,
 * so no billing instance is ever created and no billing logic executes.
 */
class BillingManager(val context: Context, val coroutineScope: CoroutineScope) {
    private val billings: List<BillingImpl> = emptyList()

    fun checkAlreadyOwnsProduct() {}
    fun startConnection(onReady: (BillingImpl) -> Unit) {}
    fun onResume() {}
    fun getBillings(): List<BillingImpl> = emptyList()
}
