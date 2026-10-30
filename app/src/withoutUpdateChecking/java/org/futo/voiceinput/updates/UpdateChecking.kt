package org.futo.voiceinput.updates

import android.content.Context

// UpdateResult removed — no update infrastructure in this fork.
// All functions are no-ops retained only for call-site compatibility.

suspend fun checkForUpdateAndSaveToPreferences(context: Context): Boolean = false

fun scheduleUpdateCheckingJob(context: Context) {
    // Intentionally empty — no update checker in this fork.
}
