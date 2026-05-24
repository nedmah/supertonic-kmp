package com.nedmah.supertonic_kmp.download

import com.nedmah.supertonic_kmp.api.DownloadState
import kotlinx.coroutines.flow.Flow


/**
 * Platform-specific model file loader.
 * Android: OkHttp or HttpURLConnection
 * iOS: URLSession
 */
internal expect class ModelDownloader(baseUrl: String, storageDir: String) {

    /**
     * Download all model files sequentially.
     * Emits [DownloadState.Downloading] with the summarized progress.
     * Skips already downloaded files (resume-friendly).
     * Completes with [DownloadState.Ready] or [DownloadState.Error].
     * Cancel the coroutine using cancellation.
     */
    fun download(files: List<String>): Flow<DownloadState>

    /**
     * Check that all files have been downloaded and are not corrupted.
     */
    fun isComplete(files: List<String>): Boolean

    /**
     * Delete all downloaded model files.
     */
    fun deleteAll()
}
