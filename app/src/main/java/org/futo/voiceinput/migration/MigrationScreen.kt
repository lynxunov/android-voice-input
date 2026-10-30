package org.futo.voiceinput.migration

import android.app.Activity
import android.app.job.JobScheduler
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.futo.voiceinput.R
import org.futo.voiceinput.settings.MODELS_MIGRATED
import org.futo.voiceinput.settings.NavigationItem
import org.futo.voiceinput.settings.NavigationItemStyle
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.ScrollableList
import org.futo.voiceinput.settings.useDataStore
import org.futo.voiceinput.theme.Typography
import org.futo.voiceinput.theme.UixThemeAuto

// OkHttp, DownloadScreen, ModelInfo, ModelItem, IS_ALREADY_PAID,
// support_thank_you_notice, model_update_network_error_*, check_permissions removed.
// Network download replaced by SAF-based model picker in this fork.

@Composable
fun NeedsMigration(): Boolean {
    val needsUpdate = isUsingTfliteLegacy(LocalContext.current)
    val wasMigrated = useDataStore(setting = MODELS_MIGRATED)
    return needsUpdate && !wasMigrated.value
}

@Composable
fun ConditionalModelUpdate() {
    val context = LocalContext.current
    if (NeedsMigration()) {
        NavigationItem(
            title = stringResource(R.string.model_update),
            style = NavigationItemStyle.Misc,
            subtitle = stringResource(R.string.update_requires_your_attention),
            navigate = {
                val intent = Intent(context, MigrationActivity::class.java)
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        )
    }
}

@Composable
fun MigrateModelScreen(proceed: () -> Unit, cancel: () -> Unit) {
    ScrollableList {
        ScreenTitle(stringResource(R.string.model_update))
        Text(
            stringResource(R.string.model_update_notice),
            modifier = Modifier.padding(16.dp, 6.dp),
            style = Typography.bodyMedium
        )
        Text(
            stringResource(R.string.model_update_features_1),
            modifier = Modifier.padding(16.dp, 6.dp),
            style = Typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.model_update_question),
            modifier = Modifier.padding(16.dp, 6.dp),
            style = Typography.bodyMedium
        )
        androidx.compose.foundation.layout.Row {
            Button(
                onClick = cancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                ),
                modifier = Modifier.padding(8.dp).weight(1.0f)
            ) { Text(stringResource(R.string.update_later)) }
            Button(
                onClick = proceed,
                modifier = Modifier.padding(8.dp).weight(1.5f)
            ) { Text(stringResource(R.string.update_now)) }
        }
    }
}

@Composable
fun MigrateModelCompleted(onFinish: () -> Unit) {
    ScrollableList {
        ScreenTitle(stringResource(R.string.model_update))
        Text(
            stringResource(R.string.model_update_finish),
            modifier = Modifier.padding(16.dp, 6.dp),
            style = Typography.bodyMedium
        )
        // Payment thank-you notice removed
        Button(
            onClick = onFinish,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ),
            modifier = Modifier.padding(8.dp).fillMaxWidth()
        ) { Text(stringResource(R.string.continue_)) }
    }
}

class MigrationActivity : ComponentActivity() {

    private fun updateContent(done: Boolean) {
        setContent {
            UixThemeAuto {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (done) {
                        MigrateModelCompleted(onFinish = { finishSuccessfully() })
                    } else {
                        MigrateModelScreen(proceed = { runMigration() }, cancel = { cancel() })
                    }
                }
            }
        }
    }

    private fun runMigration() {
        // No network downloads — just delete any legacy TFLite files that exist.
        deleteLegacyModels(applicationContext)
        updateContent(done = true)
    }

    private fun cancel() {
        setResult(RESULT_CANCELED, Intent())
        finish()
    }

    private fun finishSuccessfully() {
        setResult(RESULT_OK, Intent())
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val jobScheduler = applicationContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        jobScheduler.cancel(MIGRATION_JOB_ID)

        if (!isUsingTfliteLegacy(applicationContext)) {
            // Nothing to migrate
            deleteLegacyModels(applicationContext)
            finishSuccessfully()
            return
        }

        updateContent(done = false)
    }
}
