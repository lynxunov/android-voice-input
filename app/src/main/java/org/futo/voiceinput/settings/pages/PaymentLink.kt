package org.futo.voiceinput.settings.pages

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.R
import org.futo.voiceinput.openURI
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.SettingsRootList
import org.futo.voiceinput.theme.Typography

/** FUTO's payment page (same address upstream's PayPalBilling opens). */
const val FUTO_PAYMENT_URL =
    "https://pay2.futo.org/checkout/polar/futo-keyboard/futo-keyboard-voiceinput/checkout-ready?success=redirect-to-organization-page"

/**
 * Fork replacement for upstream's payment screens. The FUTO Source First License requires that
 * payment-related functionality is not removed, so this keeps a payment entry point – reduced to a
 * link that opens in the user's web browser (the app itself has no network access).
 */
@Composable
fun SupportFutoScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current

    SettingsRootList {
        ScreenTitle(stringResource(R.string.lynx_support_futo), showBack = true, navController = navController)

        Text(
            stringResource(R.string.lynx_support_futo_text),
            style = Typography.Body.Regular,
            modifier = Modifier.padding(16.dp, 8.dp)
        )

        Button(
            onClick = { context.openURI(FUTO_PAYMENT_URL, newTask = true) },
            modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp)
        ) {
            Text(stringResource(R.string.lynx_support_futo_button))
        }
    }
}
