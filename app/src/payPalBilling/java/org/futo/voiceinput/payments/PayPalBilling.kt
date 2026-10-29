package org.futo.voiceinput.payments

import android.content.Context
import kotlinx.coroutines.CoroutineScope

/**
 * PayPal billing stub for the Lynx fork — disabled entirely.
 * isAllowed() returns false so BillingManager never instantiates this class.
 */
class PayPalBilling(val context: Context, private val coroutineScope: CoroutineScope) : BillingImpl {
    companion object {
        fun isAllowed(): Boolean = false  // Disabled in this fork
    }

    override fun checkAlreadyOwnsProduct() {}
    override fun startConnection(onReady: () -> Unit) {}
    override fun onResume() {}
    override fun launchBillingFlow() {}
    override fun supportsCheckingIfAlreadyOwnsProduct(): Boolean = false
    override fun getName(): String = ""
}
