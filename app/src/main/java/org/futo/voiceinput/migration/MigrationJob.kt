package org.futo.voiceinput.migration

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// OkHttp removed — no network downloads in this fork.
// isUsingTfliteLegacy check simplified: always returns false since we never had the old TFLite
// models installed from a download (models are user-picked via SAF in this fork).

class ModelMigrationJob : JobService() {
    private var job: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d("MigrationJob", "Starting migration job")
        job = CoroutineScope(Dispatchers.IO).launch {
            // In this fork there are no legacy TFLite models to migrate from —
            // the original download path is replaced by SAF-based model picking.
            // This job is a no-op; it exists only so legacy JobScheduler entries
            // (if any) complete gracefully.
            Log.d("MigrationJob", "No legacy models to migrate in this fork — finishing immediately")
            deleteLegacyModels(applicationContext)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        job?.cancel()
        return false
    }
}

const val MIGRATION_JOB_ID: Int = 15782788

fun scheduleModelMigrationJob(context: Context) {
    if (!isUsingTfliteLegacy(context)) {
        Log.d("MigrationJob", "Avoiding scheduling: no legacy models present")
        return
    }

    val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

    if (jobScheduler.getPendingJob(MIGRATION_JOB_ID) != null) {
        Log.d("MigrationJob", "Avoiding scheduling: already scheduled")
        return
    }

    var jobInfoBuilder = JobInfo
        .Builder(MIGRATION_JOB_ID, ComponentName(context, ModelMigrationJob::class.java))
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE) // no network needed in this fork
        .setPersisted(false)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        jobInfoBuilder = jobInfoBuilder.setPriority(JobInfo.PRIORITY_DEFAULT)
    }

    jobScheduler.schedule(jobInfoBuilder.build())
    Log.d("MigrationJob", "Scheduled migration job")
}
