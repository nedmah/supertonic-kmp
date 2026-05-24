package com.nedmah.supertonic_kmp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nedmah.supertonic_kmp.api.DownloadState
import com.nedmah.supertonic_kmp.api.GenerateResult
import com.nedmah.supertonic_kmp.api.PlatformPlayer
import com.nedmah.supertonic_kmp.api.SupertonicConfig
import com.nedmah.supertonic_kmp.api.SupertonicTts
import com.nedmah.supertonic_kmp.api.SupertonicVoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    private val tts by lazy {
        SupertonicTts(
            SupertonicConfig(
                storageDir = filesDir.path + "/supertonic",
                defaultLang = "ru",
                defaultVoice = SupertonicVoice.M1,
                inferenceSteps = 8
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface {
                    TestScreen(tts)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.close()
    }
}

@Composable
fun TestScreen(tts: SupertonicTts) {
    val scope = rememberCoroutineScope()

    var statusText by remember {
        mutableStateOf(
            if (tts.downloadState.value is DownloadState.Ready) "Models ready!"
            else "Press Download to get started"
        )
    }
    var progress by remember { mutableFloatStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }
    var isReady by remember { mutableStateOf(tts.downloadState.value is DownloadState.Ready) }
    var isGenerating by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("Привет! Это тест синтеза речи.") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {

        Text(text = "Supertonic TTS Test", style = MaterialTheme.typography.headlineSmall)

        Spacer(Modifier.height(24.dp))

        // Status
        Text(text = statusText, style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(12.dp))

        // Progress bar (visible during download)
        if (isDownloading) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }

        // Download button
        Button(
            onClick = {
                scope.launch {
                    isDownloading = true
                    tts.download().collect { state ->
                        when (state) {
                            is DownloadState.Downloading -> {
                                progress = state.fraction
                                statusText = "Downloading... ${state.progressFormatted}"
                            }
                            is DownloadState.Ready -> {
                                isDownloading = false
                                isReady = true
                                statusText = "Models ready!"
                            }
                            is DownloadState.Error -> {
                                isDownloading = false
                                statusText = "Error: ${state.cause.message}"
                            }
                            else -> {}
                        }
                    }
                }
            },
            enabled = !isDownloading && !isReady,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isReady) "Downloaded ✓" else "Download Models (~265MB)")
        }

        Spacer(Modifier.height(16.dp))

        // Text input
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Text to synthesize") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isGenerating,
        )

        Spacer(Modifier.height(12.dp))

        // Speak button
        Button(
            onClick = {
                scope.launch {
                    isGenerating = true
                    statusText = "Generating..."
                    val result = tts.generate(inputText)
                    when (result) {
                        is GenerateResult.Success -> {
                            statusText = "Done! Duration: ${result.durationMs}ms"
                            withContext(Dispatchers.IO) { PlatformPlayer.play(result.wav) }
                            isGenerating = false
                        }
                        is GenerateResult.Error -> {
                            statusText = "Error: ${result.cause.message}"
                        }
                    }
                    isGenerating = false
                }
            },
            enabled = isReady && !isGenerating && inputText.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isGenerating) "Generating..." else "Speak")
        }

        // Stop button
        if (isGenerating) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { tts.stop(); isGenerating = false },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Stop")
            }
        }
    }
}