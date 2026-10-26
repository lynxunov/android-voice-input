package org.futo.voiceinput

import android.app.Application

/**
 * Application class for the Lynx Voice Input fork.
 * ACRA crash reporting has been fully removed — no telemetry, no remote logging.
 * On-device logcat logs remain available for local debugging only.
 */
class LynxVoiceInputApplication : Application()
