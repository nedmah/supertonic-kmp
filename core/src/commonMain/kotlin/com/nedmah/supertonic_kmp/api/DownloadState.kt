package com.nedmah.supertonic_kmp.api

/**
 * Download state of ONNX models (~265MB).
 */
sealed class DownloadState {

    data object NotDownloaded : DownloadState()

    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : DownloadState() {
        val fraction: Float
            get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f

        val progressFormatted: String
            get() = "${bytesDownloaded.toMb()} MB / ${totalBytes.toMb()} MB"

        private fun Long.toMb(): Long = this / (1024 * 1024)
    }

    data object Ready : DownloadState()

    data class Error(val cause: Throwable) : DownloadState()
}
