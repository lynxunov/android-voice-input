package org.futo.voiceinput.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.futo.voiceinput.R
import org.futo.voiceinput.models.ModelPickerActivity
import org.futo.voiceinput.loadCurrentModelList
import org.futo.voiceinput.shared.types.ModelPresence
import org.futo.voiceinput.startModelDownloadActivity

@Composable
fun ConditionalModelUpdate() {
    val context = LocalContext.current

    val models = remember(ModelPickerActivity.picksCompleted.intValue) {
        context.loadCurrentModelList()
            .map { it to it.getPresence(context) }
            .filter { it.second != ModelPresence.Exists }
    }
    if(models.isNotEmpty()) {
        // Fork: nothing is downloaded; the only fix is importing a file in the Model Manager.
        val missing = models.filter { it.second == ModelPresence.Missing }.map { it.first }
        val anyPresent = remember(ModelPickerActivity.picksCompleted.intValue) {
            context.loadCurrentModelList().any { it.exists(context) }
        }
        NavigationItem(
            title = stringResource(R.string.lynx_no_model_banner),
            subtitle = if (anyPresent) missing.joinToString(", ") { context.getString(it.name) } else null,
            style = NavigationItemStyle.Misc,
            navigate = { context.startActivity(android.content.Intent(context, ModelPickerActivity::class.java)) }
        )
    }
}
