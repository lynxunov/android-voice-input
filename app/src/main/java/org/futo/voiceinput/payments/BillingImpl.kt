package org.futo.voiceinput.payments

import android.app.Activity

/**
 * No-op billing interface for the Lynx fork.
 * Declares all methods that the payPalBilling source-set stubs override,
 * so the fDroid flavor compiles without any payment logic executing at runtime.
 */
interface BillingImpl {
    fun checkAlreadyOwnsProduct() {}
    fun startConnection(onReady: (BillingImpl) -> Unit) {}
    fun onResume() {}
    fun launchBillingFlow(activity: Activity) {}
    fun supportsCheckingIfAlreadyOwnsProduct(): Boolean = false
    fun getName(): String = ""
}
