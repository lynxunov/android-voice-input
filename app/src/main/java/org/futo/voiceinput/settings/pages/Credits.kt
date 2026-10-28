package org.futo.voiceinput.settings.pages

import android.content.Intent
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.voiceinput.R
import org.futo.voiceinput.settings.ScreenTitle
import org.futo.voiceinput.settings.ScrollableList
import org.futo.voiceinput.theme.Typography

// OkHttp credit removed — OkHttp is no longer a dependency in this fork.

@Composable
fun CreditItem(name: String, thanksFor: String, link: String, license: String, copyright: String) {
    val uriHandler = LocalUriHandler.current
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        shape = RoundedCornerShape(4.dp)
    ) {
        ClickableText(text = buildAnnotatedString {
            val fullString = "$name - $thanksFor\n$license - $copyright"
            addStyle(
                style = SpanStyle(
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = Typography.bodyMedium.fontSize,
                    fontWeight = Typography.bodyMedium.fontWeight
                ),
                start = 0, end = fullString.length
            )
            val start = fullString.indexOf(name)
            val end = start + name.length
            addStyle(
                style = SpanStyle(
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = Typography.titleLarge.fontSize,
                    fontWeight = Typography.titleLarge.fontWeight,
                    textDecoration = TextDecoration.Underline
                ),
                start = start, end = end
            )
            addStringAnnotation(tag = "URL", annotation = link, start = start, end = end)
            append(fullString)
        }, onClick = { uriHandler.openUri(link) }, modifier = Modifier.padding(8.dp), style = Typography.bodyLarge)
    }
}

@Composable
@Preview
fun CreditsScreen(openDependencies: () -> Unit = {}, navController: NavHostController = rememberNavController()) {
    ScrollableList {
        ScreenTitle(title = stringResource(id = R.string.credits_title), showBack = true, navController = navController)

        CreditItem("OpenAI Whisper", stringResource(R.string.thanks_for_the_voice_recognition_model),
            "https://github.com/openai/whisper", "MIT", "Copyright (c) 2022 OpenAI")
        CreditItem("whisper.cpp & ggml", stringResource(R.string.thanks_for_the_machine_learning_inference_library),
            "https://github.com/ggerganov/whisper.cpp", "MIT", "Copyright (c) 2023 Georgi Gerganov")
        CreditItem("TensorFlow Lite", stringResource(R.string.thanks_for_the_machine_learning_inference_library),
            "https://mvnrepository.com/artifact/org.tensorflow/tensorflow-lite", "Apache-2.0", "Copyright (c) 2023 TensorFlow Authors")
        CreditItem("PocketFFT", stringResource(R.string.thanks_for_the_fft_library_used_to_convert_audio_to_model_input),
            "https://gitlab.mpcdf.mpg.de/mtr/pocketfft/-/blob/master/LICENSE.md", "BSD-3-Clause", "Copyright (c) 2010-2019 Max-Planck-Society")
        CreditItem("WebRTC", stringResource(R.string.thanks_for_the_voice_activity_detection_to_stop_recognition_on_silence),
            "https://webrtc.org/", "BSD-3-Clause", "Copyright (c) 2011, The WebRTC project authors")
        CreditItem("android-vad", stringResource(R.string.thanks_for_the_android_bindings_to_webrtc_voice_activity_detection),
            "https://github.com/gkonovalov/android-vad", "MIT", "Copyright (c) 2023 Georgiy Konovalov")
        // OkHttp removed — no longer a dependency in this fork
        CreditItem("Feather Icons", stringResource(R.string.thanks_for_icon),
            "https://github.com/feathericons/feather", "MIT", "Copyright (c) 2013-2017 Cole Bemis")

        Button(onClick = openDependencies, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Text(stringResource(R.string.view_other_dependencies))
        }
        Text(stringResource(R.string.name_legal), style = Typography.bodyMedium, modifier = Modifier.padding(8.dp))
    }
}

@Composable
fun DependenciesScreen(navController: NavHostController = rememberNavController()) {
    androidx.compose.foundation.layout.Column {
        ScreenTitle(title = stringResource(id = R.string.dependencies), showBack = true, navController = navController)
        AndroidView(factory = {
            WebView(it).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val intent = Intent(Intent.ACTION_VIEW, request!!.url)
                        view!!.context.startActivity(intent)
                        return true
                    }
                }
            }
        }, update = { it.loadUrl("file:///android_asset/license-list.html") })
    }
}
