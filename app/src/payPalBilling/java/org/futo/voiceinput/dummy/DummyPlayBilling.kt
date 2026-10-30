package org.futo.voiceinput.payments

import android.content.Context
import kotlinx.coroutines.CoroutineScope

/**
 * Play billing stub for the Lynx fork — always disabled.
 * isAllowed() returns false so this class is never instantiated.
 */
class PlayBilling(private val context: Context, private val coroutine: CoroutineScope) : BillingImpl {
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
