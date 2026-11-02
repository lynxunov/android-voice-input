package org.futo.voiceinput.models

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.voiceinput.R
import org.futo.voiceinput.openURI
import org.futo.voiceinput.settings.ENGLISH_MODEL_INDEX
import org.futo.voiceinput.settings.MULTILINGUAL_MODEL_INDEX
import org.futo.voiceinput.settings.getSetting
import org.futo.voiceinput.settings.setSetting
import org.futo.voiceinput.shared.ENGLISH_MODELS
import org.futo.voiceinput.shared.MULTILINGUAL_MODELS
import org.futo.voiceinput.shared.types.ModelDownloadable
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.theme.UixThemeAuto
import java.security.MessageDigest

private const val MODEL_INFO_URL = "https://github.com/futo-org/whisper-acft"
private const val MODEL_DOWNLOAD_BASE = "https://dl.voiceinput.futo.org/VoiceInput/"

/**
 * Replaces upstream's DownloadActivity. The app has no INTERNET permission, so the user downloads model
 * files with a browser and selects them here with the system file picker. A persistable permission is
 * taken so the file stays readable after a reboot; the file is used in place and never copied.
 */
class ModelPickerActivity : ComponentActivity() {
    companion object {
        /** Incremented whenever a file is chosen or removed, so other screens can refresh. */
        val picksCompleted = mutableIntStateOf(0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UixThemeAuto {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ModelPickerScreen()
                }
            }
        }
    }
}

private fun Context.displayName(uri: Uri): String? = try {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }
} catch (e: Exception) { null }

private fun sha256Hex(context: Context, uri: Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
} catch (e: Exception) { null }

/**
 * Saves the user a second trip into "Model options": if the model that is currently selected has no
 * file but the one just picked could be used instead, select the picked one.
 */
private suspend fun Context.selectIfCurrentIsMissing(picked: ModelLoader) {
    val pickedKey = picked.key(this)

    ENGLISH_MODELS.indexOfFirst { it.key(this) == pickedKey }.takeIf { it >= 0 }?.let { idx ->
        val current = ENGLISH_MODELS.getOrNull(getSetting(ENGLISH_MODEL_INDEX))
        if (current == null || !current.withSafStorage(this).exists(this)) setSetting(ENGLISH_MODEL_INDEX, idx)
    }
    MULTILINGUAL_MODELS.indexOfFirst { it.key(this) == pickedKey }.takeIf { it >= 0 }?.let { idx ->
        val current = MULTILINGUAL_MODELS.getOrNull(getSetting(MULTILINGUAL_MODEL_INDEX))
        if (current == null || !current.withSafStorage(this).exists(this)) setSetting(MULTILINGUAL_MODEL_INDEX, idx)
    }
}

@Composable
private fun ModelPickerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refresh = ModelPickerActivity.picksCompleted.intValue
    val pending = remember { mutableStateOf<ModelLoader?>(null) }
    val notes = remember { mutableStateMapOf<String, String>() }

    // The same model can be listed for both English and multilingual use; show it once.
    val english = ENGLISH_MODELS
    val multilingual = MULTILINGUAL_MODELS.filter { m -> english.none { it.key(context) == m.key(context) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val model = pending.value
        pending.value = null
        if (uri == null || model == null) return@rememberLauncherForActivityResult

        scope.launch(Dispatchers.IO) {
            val id = model.storageId(context)
            val previous = context.getModelUri(model)
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                Log.w("ModelPicker", "Could not persist read permission: ${e.message}")
            }
            if (previous != null && previous != uri) {
                try { context.contentResolver.releasePersistableUriPermission(previous, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                catch (_: Exception) { }
            }
            context.setModelUri(model, uri)

            val shown = context.displayName(uri)
            val expected = (model as? ModelDownloadable)?.let { listOf(it.ggmlFile) + it.backwardsCompatFiles }
            notes[id] = if (expected != null && shown != null && shown !in expected) {
                context.getString(R.string.lynx_name_mismatch, shown, expected.first())
            } else ""

            context.selectIfCurrentIsMissing(model)
            withContext(Dispatchers.Main) { ModelPickerActivity.picksCompleted.intValue++ }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.lynx_model_manager), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.lynx_models_intro), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { context.openURI(MODEL_INFO_URL, newTask = true) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.lynx_open_model_info))
                }
            }
        }

        Text(stringResource(R.string.lynx_models_english), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        english.forEach { model ->
            ModelRow(model, refresh, notes, onChoose = { pending.value = model; picker.launch(arrayOf("*/*")) })
        }

        Text(stringResource(R.string.lynx_models_multilingual), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        multilingual.forEach { model ->
            ModelRow(model, refresh, notes, onChoose = { pending.value = model; picker.launch(arrayOf("*/*")) })
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ModelRow(
    model: ModelLoader,
    refresh: Int,
    notes: MutableMap<String, String>,
    onChoose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val id = model.storageId(context)
    val ready = remember(refresh) { model.withSafStorage(context).exists(context) }
    val verifyText = remember { mutableStateOf("") }
    val fileName = (model as? ModelDownloadable)?.ggmlFile

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(model.name), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(if (ready) R.string.lynx_status_ready else R.string.lynx_status_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            if (fileName != null) {
                Text(stringResource(R.string.lynx_expected_file, fileName), style = MaterialTheme.typography.bodySmall)
            }
            notes[id]?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (verifyText.value.isNotBlank()) {
                Text(verifyText.value, style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onChoose) {
                    Text(stringResource(if (ready) R.string.lynx_replace_file else R.string.lynx_choose_file))
                }
                if (ready) {
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            val old = context.getModelUri(model)
                            context.clearModelUri(model)
                            if (old != null) {
                                try { context.contentResolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                                catch (_: Exception) { }
                            }
                            notes.remove(id)
                            withContext(Dispatchers.Main) { ModelPickerActivity.picksCompleted.intValue++ }
                        }
                    }) { Text(stringResource(R.string.lynx_remove)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (fileName != null) {
                    TextButton(onClick = { context.openURI(MODEL_DOWNLOAD_BASE + fileName, newTask = true) }) {
                        Text(stringResource(R.string.lynx_download_link))
                    }
                }
                val expectedChecksum = (model as? ModelDownloadable)?.checksum
                if (ready && expectedChecksum != null) {
                    TextButton(onClick = {
                        verifyText.value = context.getString(R.string.lynx_verify_running)
                        scope.launch(Dispatchers.IO) {
                            val uri = context.getModelUri(model)
                            val actual = uri?.let { sha256Hex(context, it) }
                            val message = when {
                                actual == null -> context.getString(R.string.lynx_verify_failed)
                                actual.equals(expectedChecksum, ignoreCase = true) -> context.getString(R.string.lynx_verify_ok)
                                else -> context.getString(R.string.lynx_verify_bad)
                            }
                            withContext(Dispatchers.Main) { verifyText.value = message }
                        }
                    }) { Text(stringResource(R.string.lynx_verify)) }
                }
            }
        }
    }
}
