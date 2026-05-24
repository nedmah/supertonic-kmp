package com.nedmah.supertonic_kmp.download

import com.nedmah.supertonic_kmp.api.DownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.net.HttpURLConnection
import java.net.URL

private const val BUFFER_SIZE = 8 * 1024          // 8 KB
private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000
private const val COMPLETE_MARKER = ".complete"

internal actual class ModelDownloader actual constructor(
    private val baseUrl: String,
    private val storageDir: String
) {
    /**
     * Downloads all [files] sequentially, resuming partial downloads.
     *
     * Progress is aggregated across all files:
     * - Already completed files count their full size toward [DownloadState.Downloading.bytesDownloaded]
     * - Partial files resume from their current on-disk size
     *
     * Emits [DownloadState.Ready] on success, [DownloadState.Error] on failure.
     * Cancellation is cooperative — checked between each buffer read.
     */
    actual fun download(files: List<String>): Flow<DownloadState> = flow {
        val dir = File(storageDir).also { it.mkdirs() }
        dir.resolve(COMPLETE_MARKER).delete()

        // HEAD all files to calculate totalBytes
        val fileSizes = try {
            files.associateWith { path -> headContentLength(fileUrl(path)) }
        } catch (e: Exception) {
            emit(DownloadState.Error(e))
            return@flow
        }

        val totalBytes = fileSizes.values.sumOf { it.coerceAtLeast(0L) }

        // download each file, track aggregate progress
        var globalDownloaded = 0L

        for (path in files) {
            val file = dir.resolve(path.substringAfterLast('/'))
            val expectedSize = fileSizes[path] ?: 0L
            val existingSize = if (file.exists()) file.length() else 0L

            if (expectedSize > 0 && existingSize == expectedSize) {
                // Already fully downloaded - count toward progress and skip
                globalDownloaded += expectedSize
                emit(DownloadState.Downloading(globalDownloaded, totalBytes))
                continue
            }

            if (expectedSize == -1L && existingSize > 0) {
                // Content-Length unavailable (e.g. chunked response) — trust existing file
                globalDownloaded += existingSize
                emit(DownloadState.Downloading(globalDownloaded, totalBytes))
                continue
            }

            // Resume from existing bytes if server supports Range
            val resumeFrom = if (file.exists() && existingSize > 0) existingSize else 0L
            globalDownloaded += resumeFrom

            val conn = openConnection(fileUrl(path), resumeFrom)
            try {
                val responseCode = conn.responseCode
                val isResume = responseCode == HttpURLConnection.HTTP_PARTIAL

                if (responseCode != HttpURLConnection.HTTP_OK && !isResume) {
                    emit(DownloadState.Error(
                        IllegalStateException("HTTP $responseCode for $path")
                    ))
                    return@flow
                }

                val outStream = if (isResume && existingSize > 0) {
                    file.appendingOutputStream()
                } else {
                    file.outputStream()
                }

                outStream.use { output ->
                    conn.inputStream.use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (currentCoroutineContext().isActive) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            globalDownloaded += read
                            emit(DownloadState.Downloading(globalDownloaded, totalBytes))
                        }
                    }
                }

                if (!currentCoroutineContext().isActive) return@flow

            } catch (e: Exception) {
                emit(DownloadState.Error(e))
                return@flow
            } finally {
                conn.disconnect()
            }
        }

        dir.resolve(COMPLETE_MARKER).createNewFile()
        emit(DownloadState.Ready)
    }.flowOn(Dispatchers.IO)

    /**
     * Returns `true` only if all files were fully downloaded in a previous session.
     * Uses a `.complete` marker written at the end of a successful [download].
     */
    actual fun isComplete(files: List<String>): Boolean {
        val markerPath = File(storageDir).resolve(COMPLETE_MARKER)
        return markerPath.exists()
    }

    /**
     * Deletes all downloaded files and the completion marker.
     */
    actual fun deleteAll() {
        val dir = File(storageDir)
        if (dir.exists()) dir.deleteRecursively()
    }

    // Private

    private fun fileUrl(path: String) = "$baseUrl/$path"

    private fun headContentLength(url: String): Long {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
        }
        return try {
            conn.connect()
            conn.contentLengthLong
        } finally {
            conn.disconnect()
        }
    }

    private fun openConnection(url: String, resumeFrom: Long): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            if (resumeFrom > 0) setRequestProperty("Range", "bytes=$resumeFrom-")
        }.also { it.connect() }

    private fun File.appendingOutputStream() = java.io.FileOutputStream(this, true)
}