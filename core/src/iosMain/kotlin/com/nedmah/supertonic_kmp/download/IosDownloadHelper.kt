package com.nedmah.supertonic_kmp.download

import com.nedmah.supertonic_kmp.api.DownloadState
import com.nedmah.supertonic_kmp.api.SupertonicTts
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// iosMain/IosDownloadHelper.kt
fun SupertonicTts.startDownload(
    onProgress: (Float, String) -> Unit,
    onReady: () -> Unit,
    onError: (String) -> Unit,
) {
    val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob()
    )
    scope.launch {
        download().collect { state ->
            when (state) {
                is DownloadState.Downloading ->
                    onProgress(state.fraction, state.progressFormatted)
                is DownloadState.Ready -> {
                    onReady()
                    scope.cancel()
                }
                is DownloadState.Error -> {
                    onError(state.cause.message ?: "Unknown error")
                    scope.cancel()
                }
                else -> {}
            }
        }
    }
}