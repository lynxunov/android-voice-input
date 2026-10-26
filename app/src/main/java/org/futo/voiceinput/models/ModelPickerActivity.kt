package org.futo.voiceinput.models

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.voiceinput.openURI
import org.futo.voiceinput.theme.WhisperVoiceInputTheme

/**
 * Replaces DownloadActivity.
 *
 * Models are loaded directly from the URI the user picks — nothing is copied
 * into app-internal storage. A persisted SAF permission is taken so the path
 * survives reboots without the user having to re-pick.
 */
class ModelPickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WhisperVoiceInputTheme {
                ModelPickerScreen()
            }
        }
    }
}

@Composable
private fun ModelPickerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Track availability for all slots — refreshed after each pick
    var slotStatus by remember { mutableStateOf<Map<ModelSlot, Boolean>>(emptyMap()) }

    // Pending slot waiting for a file-picker result
    var pendingSlot by remember { mutableStateOf<ModelSlot?>(null) }

    // Load initial status
    LaunchedEffect(Unit) {
        slotStatus = withContext(Dispatchers.IO) { context.allModelSlotStatus() }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val slot = pendingSlot ?: return@rememberLauncherForActivityResult
        pendingSlot = null
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            withContext(Dispatchers.IO) {
                // Take a persistable read permission so the URI survives reboots
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    android.util.Log.w("ModelPicker", "Could not persist URI permission: ${e.message}")
                }
                context.setModelUri(slot, uri)
                slotStatus = context.allModelSlotStatus()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Model Manager",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // ── Info card ────────────────────────────────────────────────────────
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("About models", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "This app has no internet access. Models must be downloaded externally and picked from your device storage. " +
                    "Files are read in-place — nothing is copied.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { context.openURI(MODEL_DOWNLOAD_URL, newTask = true) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open model download page ↗")
                }
                Text(
                    "Download the .bin files for the models you want, then use the buttons below to select each one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── English models ────────────────────────────────────────────────────
        SectionHeader("English Models")
        ModelSlot.values()
            .filter { it.isEnglish }
            .forEach { slot ->
                ModelSlotRow(
                    slot = slot,
                    isAvailable = slotStatus[slot] ?: false,
                    onPickClicked = {
                        pendingSlot = slot
                        filePicker.launch(arrayOf("application/octet-stream", "*/*"))
                    },
                    onClearClicked = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                context.clearModelUri(slot)
                                slotStatus = context.allModelSlotStatus()
                            }
                        }
                    }
                )
            }

        // ── Multilingual models ───────────────────────────────────────────────
        SectionHeader("Multilingual Models")
        ModelSlot.values()
            .filter { !it.isEnglish }
            .forEach { slot ->
                ModelSlotRow(
                    slot = slot,
                    isAvailable = slotStatus[slot] ?: false,
                    onPickClicked = {
                        pendingSlot = slot
                        filePicker.launch(arrayOf("application/octet-stream", "*/*"))
                    },
                    onClearClicked = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                context.clearModelUri(slot)
                                slotStatus = context.allModelSlotStatus()
                            }
                        }
                    }
                )
            }

        // ── Model file format notes ───────────────────────────────────────────
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Expected file names", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "English tiny:      tiny_en_acft_q8_0.bin.not.tflite (built-in)\n" +
                    "English base:      base_en_acft_q8_0.bin\n" +
                    "English small:     small_en_acft_q8_0.bin\n" +
                    "Multi tiny:        tiny_acft_q8_0.bin\n" +
                    "Multi base:        base_acft_q8_0.bin\n" +
                    "Multi small:       small_acft_q8_0.bin",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun ModelSlotRow(
    slot: ModelSlot,
    isAvailable: Boolean,
    onPickClicked: () -> Unit,
    onClearClicked: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (isAvailable) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(slot.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    if (isAvailable) "Ready" else "Not configured",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isAvailable) {
                TextButton(onClick = onClearClicked) { Text("Clear") }
            }
            FilledTonalIconButton(onClick = onPickClicked) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Pick file")
            }
        }
    }
}

/** Public URL for model downloads — opened externally, no in-app network access. */
const val MODEL_DOWNLOAD_URL =
    "https://github.com/futo-org/voice-input-models/blob/main/README.md"
