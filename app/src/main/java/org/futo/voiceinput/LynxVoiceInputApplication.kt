package org.futo.voiceinput

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.futo.voiceinput.settings.DataStoreHelper
import org.futo.voiceinput.shared.whisper.ModelManager

/**
 * Fork replacement for upstream's CrashLoggingApplication.
 *
 * Everything crash-reporting related (ACRA) is gone: no telemetry, no remote logging.
 * It still has to do the two jobs upstream's class did, otherwise nothing can run:
 *  1. load the native inference library (`libvoiceinput.so`) before the first model is opened
 *  2. own the single [ModelManager] shared by the IME and the recognize activity
 *  3. initialise [DataStoreHelper]: upstream's blocking getSetting() reads an in-memory snapshot that is
 *     only filled by DataStoreHelper.init(); without it every setting silently returns its default
 */
class LynxVoiceInputApplication : Application(), ComponentCallbacks2 {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val modelManager = ModelManager(this)

    companion object {
        private var libraryInitialized = false

        @JvmStatic
        @Synchronized
        fun getModelManager(context: Context): ModelManager? {
            if (!libraryInitialized) {
                System.loadLibrary("voiceinput")
                libraryInitialized = true
            }
            return (context.applicationContext as? LynxVoiceInputApplication)?.modelManager
        }
    }

    override fun onCreate() {
        super.onCreate()
        DataStoreHelper.init(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == TRIM_MEMORY_BACKGROUND) {
            // Free the loaded models when the app has been in the background for a while.
            applicationScope.launch { modelManager.cleanUp() }
        }
    }
}
