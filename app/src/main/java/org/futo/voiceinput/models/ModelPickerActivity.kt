package org.futo.voiceinput.models

import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.voiceinput.R
import org.futo.voiceinput.openURI
import org.futo.voiceinput.theme.UixThemeAuto

/**
 * Replaces DownloadActivity — no network access.
 * Models are opened in-place via SAF; a persisted URI permission survives reboots.
 */
class ModelPickerActivity : ComponentActivity() {
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

@Composable
private fun ModelPickerScreen() {
    val scope = rememberCoroutineScope()
    var slotStatus by remember { mutableStateOf<Map<ModelSlot, Boolean>>(emptyMap()) }
    var pendingSlot by remember { mutableStateOf<ModelSlot?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current

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
        Text("Model Manager", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("About models", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "This app has no internet access. Download models externally and pick them here. " +
                    "Files are read in-place — nothing is copied to app storage.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = { context.openURI(MODEL_DOWNLOAD_URL, newTask = true) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open model download page ↗")
                }
                Text(
                    "Download the .bin files, then use the buttons below to assign each one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionHeader("English Models")
        ModelSlot.values().filter { it.isEnglish }.forEach { slot ->
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

        SectionHeader("Multilingual Models")
        ModelSlot.values().filter { !it.isEnglish }.forEach { slot ->
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

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Expected file names", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "English tiny   (built-in): tiny_en_acft_q8_0.bin.not.tflite\n" +
                    "English base:              base_en_acft_q8_0.bin\n" +
                    "English small:             small_en_acft_q8_0.bin\n" +
                    "Multi tiny:                tiny_acft_q8_0.bin\n" +
                    "Multi base:                base_acft_q8_0.bin\n" +
                    "Multi small:               small_acft_q8_0.bin",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                painter = painterResource(
                    if (isAvailable) R.drawable.check_circle else R.drawable.alert_circle
                ),
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
                Icon(
                    painter = painterResource(R.drawable.cpu), // folder icon substitute
                    contentDescription = "Pick file",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

const val MODEL_DOWNLOAD_URL = "https://github.com/futo-org/voice-input-models/blob/main/README.md"
