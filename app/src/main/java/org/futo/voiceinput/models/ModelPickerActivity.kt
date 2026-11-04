package org.futo.voiceinput.models

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.Formatter
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.voiceinput.LynxVoiceInputApplication
import org.futo.voiceinput.R
import org.futo.voiceinput.openURI
import org.futo.voiceinput.settings.MODEL_OPTIONS
import org.futo.voiceinput.settings.getSetting
import org.futo.voiceinput.settings.setSetting
import org.futo.voiceinput.shared.ALL_MODELS
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.ggml.InvalidModelException
import org.futo.voiceinput.shared.types.ModelDownloadable
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.types.toWhisperString
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

/** Compares the chosen file with the official checksum. Null if this model has no known checksum. */
private fun verifyMessage(context: Context, model: ModelLoader): String? {
    val expected = (model as? ModelDownloadable)?.checksum ?: return null
    val actual = context.getModelUri(model)?.let { sha256Hex(context, it) }
    return when {
        actual == null -> context.getString(R.string.lynx_verify_failed)
        actual.equals(expected, ignoreCase = true) -> context.getString(R.string.lynx_verify_ok)
        else -> context.getString(R.string.lynx_verify_bad)
    }
}

/** Really loads the model once (then frees it) and reports how it went. */
private suspend fun testLoad(context: Context, model: ModelLoader): String {
    val id = model.storageId(context)
    return try {
        LynxVoiceInputApplication.getModelManager(context) // makes sure the native library is loaded
        model.withSafStorage(context).loadGGML(context).close()
        context.getString(R.string.lynx_test_ok, SafBackedModel.lastReport(id)?.summary.orEmpty())
    } catch (e: CancellationException) {
        throw e
    } catch (e: InvalidModelException) {
        context.getString(R.string.lynx_test_failed, SafBackedModel.lastReport(id)?.summary ?: e.message.orEmpty())
    } catch (e: Throwable) {
        context.getString(R.string.lynx_test_failed, e.toString())
    }
}

/**
 * Saves the user a second trip into "Model options": for every slot (multilingual, or a language that
 * has its own override) that currently points at a model with no file, point it at the one just picked
 * instead – but only if the picked model actually supports that slot.
 */
private suspend fun Context.selectIfCurrentIsMissing(picked: ModelLoader) {
    val options = getSetting(MODEL_OPTIONS).toMutableSet()
    var changed = false

    val updated = options.map { entry ->
        val (prefix, modelId) = entry.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val currentModel = modelId.takeIf { it.isNotEmpty() }?.let { org.futo.voiceinput.shared.lookupModel(it) }
        val currentIsUsable = currentModel != null && currentModel.withSafStorage(this).exists(this)
        val pickedFitsSlot = prefix == "multi" || picked.caps.supportedLanguages.any { it.toWhisperString() == prefix }

        if (!currentIsUsable && pickedFitsSlot && modelId != picked.id) {
            changed = true
            "$prefix:${picked.id}"
        } else entry
    }.toMutableSet()

    if (changed) setSetting(MODEL_OPTIONS, updated)
}

@Composable
private fun ModelPickerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refresh = ModelPickerActivity.picksCompleted.intValue
    val pending = remember { mutableStateOf<ModelLoader?>(null) }
    val notes = remember { mutableStateMapOf<String, String>() }
    val verify = remember { mutableStateMapOf<String, String>() }
    val tests = remember { mutableStateMapOf<String, String>() }

    // Mirrors the grouping in settings/pages/Models.kt (upstream no longer has separate fixed lists).
    val english = ALL_MODELS.filter { it.caps.supportedLanguages == setOf(Language.English) }
        .sortedBy { it.caps.paramCount }
    val multilingual = ALL_MODELS.filter { it.caps.supportedLanguages.size > 1 }
        .sortedBy { it.caps.paramCount }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val model = pending.value
        pending.value = null
        if (uri == null || model == null) return@rememberLauncherForActivityResult

        scope.launch(Dispatchers.IO) {
            val id = model.storageId(context)
            val previous = context.getModelUri(model)
            var permanent = true
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                permanent = false
                Log.w("ModelPicker", "Could not persist read permission: ${e.message}")
            }
            if (previous != null && previous != uri) {
                try { context.contentResolver.releasePersistableUriPermission(previous, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                catch (_: Exception) { }
            }
            SafBackedModel.deletePrivateCopy(context, id) // a different file => any old private copy is stale
            context.setModelUri(model, uri)
            verify.remove(id); tests.remove(id)

            val shown = context.displayName(uri)
            val expected = (model as? ModelDownloadable)?.let { listOf(it.ggmlFile) + it.backwardsCompatFiles }
            notes[id] = listOfNotNull(
                if (expected != null && shown != null && shown !in expected)
                    context.getString(R.string.lynx_name_mismatch, shown, expected.first()) else null,
                if (!permanent) context.getString(R.string.lynx_not_permanent) else null,
            ).joinToString("\n")

            context.selectIfCurrentIsMissing(model)
            withContext(Dispatchers.Main) { ModelPickerActivity.picksCompleted.intValue++ }

            // One automatic checksum check for a newly chosen file. Informational only: it never undoes the choice.
            if (model is ModelDownloadable) {
                verify[id] = context.getString(R.string.lynx_verify_running)
                verify[id] = verifyMessage(context, model).orEmpty()
            }
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
            ModelRow(model, refresh, notes, verify, tests, onChoose = { pending.value = model; picker.launch(arrayOf("*/*")) })
        }

        Text(stringResource(R.string.lynx_models_multilingual), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        multilingual.forEach { model ->
            ModelRow(model, refresh, notes, verify, tests, onChoose = { pending.value = model; picker.launch(arrayOf("*/*")) })
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ModelRow(
    model: ModelLoader,
    refresh: Int,
    notes: MutableMap<String, String>,
    verify: MutableMap<String, String>,
    tests: MutableMap<String, String>,
    onChoose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val id = model.storageId(context)
    val ready = remember(refresh) { model.withSafStorage(context).exists(context) }
    val copyBytes = remember(refresh) { SafBackedModel.privateCopySize(context, id) }
    val lastFailure = remember(refresh) { SafBackedModel.lastReport(id)?.takeIf { !it.ok }?.summary }
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
            verify[id]?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            (tests[id] ?: lastFailure?.let { context.getString(R.string.lynx_last_load_failed, it) })
                ?.takeIf { it.isNotBlank() }
                ?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (copyBytes > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.lynx_private_copy, Formatter.formatShortFileSize(context, copyBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            SafBackedModel.deletePrivateCopy(context, id)
                            withContext(Dispatchers.Main) { ModelPickerActivity.picksCompleted.intValue++ }
                        }
                    }) { Text(stringResource(R.string.lynx_delete_copy)) }
                }
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
                            SafBackedModel.deletePrivateCopy(context, id)
                            notes.remove(id); verify.remove(id); tests.remove(id)
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
                if (ready && model is ModelDownloadable) {
                    TextButton(onClick = {
                        verify[id] = context.getString(R.string.lynx_verify_running)
                        scope.launch(Dispatchers.IO) { verify[id] = verifyMessage(context, model).orEmpty() }
                    }) { Text(stringResource(R.string.lynx_verify)) }
                }
                if (ready) {
                    TextButton(onClick = {
                        tests[id] = context.getString(R.string.lynx_test_running)
                        scope.launch(Dispatchers.IO) {
                            val message = testLoad(context, model)
                            tests[id] = message
                            withContext(Dispatchers.Main) { ModelPickerActivity.picksCompleted.intValue++ }
                        }
                    }) { Text(stringResource(R.string.lynx_test_load)) }
                }
            }
        }
    }
}
