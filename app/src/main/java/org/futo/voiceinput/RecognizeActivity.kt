package org.futo.voiceinput

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import org.futo.voiceinput.migration.scheduleModelMigrationJob
import org.futo.voiceinput.theme.UixThemeAuto

/**
 * Wraps the shared RecognizerView's Content() in the app's own themed bubble chrome.
 * Rendering of the recording bubble/loading state itself is handled entirely by
 * shared.RecognizerView — this just supplies the outer card, close button, and background logo.
 */
@Composable
fun RecognizeWindow(
    allowClick: Boolean = false,
    onClose: (() -> Unit)?,
    onPauseVAD: (Boolean) -> Unit = { },
    onFinish: () -> Unit = { },
    content: @Composable () -> Unit
) {
    UixThemeAuto {
        Surface(
            modifier = Modifier
                .recognizerSurfaceClickable(disabled = !allowClick, onPauseVAD = onPauseVAD, onFinish = onFinish)
                .width(280.dp)
                .wrapContentHeight(),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp)
        ) {
            val icon = painterResource(id = R.drawable.futo_o)
            val bgIconTint = MaterialTheme.colorScheme.outline

            Column(modifier = Modifier.drawBehind {
                with(icon) {
                    translate(left = -icon.intrinsicSize.width / 2, top = -icon.intrinsicSize.height / 2) {
                        translate(left = size.width / 4, top = size.height / 3) {
                            draw(icon.intrinsicSize, colorFilter = ColorFilter.tint(bgIconTint))
                        }
                    }
                }
            }) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                        if (onClose != null) {
                            IconButton(onClick = onClose) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        } else {
                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
                Column(modifier = Modifier.fillMaxWidth().padding(0.dp, 0.dp, 0.dp, 40.dp)) {
                    content()
                }
            }
        }
    }
}

class RecognizeActivity : ComponentActivity() {
    private val recognizer = object : RecognizerView() {
        override val context: Context get() = this@RecognizeActivity
        override val lifecycleScope: LifecycleCoroutineScope get() = this@RecognizeActivity.lifecycleScope

        override fun setContent(content: @Composable () -> Unit) {
            // No-op: this app wraps Content() via renderWithChrome() below instead.
        }

        override fun onCancel() { this@RecognizeActivity.onCancel() }
        override fun sendResult(result: String) { this@RecognizeActivity.sendResult(result) }
        override fun sendPartialResult(result: String): Boolean = false

        override fun requestPermission(onGranted: () -> Unit, onRejected: () -> Unit) {
            this@RecognizeActivity.requestPermission(onGranted, onRejected)
        }

        override fun decodingStarted() {}

        override fun modelNotFoundOpenPicker() {
            this@RecognizeActivity.startModelPickerActivity()
            onCancel()
        }
    }

    private fun onCancel() {
        setResult(RESULT_CANCELED, null)
        finish()
    }

    private fun renderWithChrome() {
        setContent {
            val allowClick by remember { mutableStateOf(true) }
            RecognizeWindow(
                allowClick = allowClick,
                onClose = { recognizer.cancel() },
                onPauseVAD = { recognizer.inhibitAutoStop(it) },
                onFinish = { recognizer.finish() }
            ) {
                recognizer.Content()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recognizer.reset()
        recognizer.init()
        renderWithChrome()
        scheduleModelMigrationJob(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer.reset()
    }

    private var pendingPermissionCallback: ((Boolean) -> Unit)? = null
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingPermissionCallback?.invoke(granted)
        pendingPermissionCallback = null
    }

    private fun requestPermission(onGranted: () -> Unit, onRejected: () -> Unit) {
        pendingPermissionCallback = { granted -> if (granted) onGranted() else onRejected() }
        permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun sendResult(result: String) {
        val returnIntent = Intent()
        returnIntent.putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, ArrayList(listOf(result)))
        returnIntent.putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, floatArrayOf(1.0f))
        setResult(RESULT_OK, returnIntent)
        finish()
    }
}
