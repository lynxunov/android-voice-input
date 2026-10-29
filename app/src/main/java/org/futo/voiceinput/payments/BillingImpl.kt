package org.futo.voiceinput.payments

/**
 * No-op billing interface for the Lynx fork.
 * Signatures match exactly what DummyPlayBilling and PayPalBilling override.
 * No billing logic executes at runtime — PayPalBilling.isAllowed() is overridden
 * to return false in our BillingManager, so startConnection/launchBillingFlow
 * are never called.
 */
interface BillingImpl {
    fun checkAlreadyOwnsProduct()
    fun startConnection(onReady: () -> Unit)
    fun onResume()
    fun launchBillingFlow()
    fun supportsCheckingIfAlreadyOwnsProduct(): Boolean
    fun getName(): String
}
