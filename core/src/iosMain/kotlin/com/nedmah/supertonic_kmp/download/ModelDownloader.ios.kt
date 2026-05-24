package com.nedmah.supertonic_kmp.download

import com.nedmah.supertonic_kmp.api.DownloadState
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.HTTPMethod
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.downloadTaskWithURL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val COMPLETE_MARKER = ".complete"

@OptIn(ExperimentalForeignApi::class)
internal actual class ModelDownloader actual constructor(
    private val baseUrl: String,
    private val storageDir: String,
) {

    private val fm = NSFileManager.defaultManager

    /**
     * Downloads all [files] sequentially using [NSURLSession.downloadTaskWithURL].
     *
     * Progress is reported per file based on cumulative byte counts from HEAD responses.
     * For smooth byte-level progress within large ONNX files, use the Swift bridge
     * in [SupertonicBridge.swift] — this will be wired up in Stage 5.
     *
     * Already downloaded files are skipped (resume-friendly via .complete marker logic).
     * Cancellation propagates to the active [NSURLSessionDownloadTask].
     */
    actual fun download(files: List<String>): Flow<DownloadState> = flow {
        val dirUrl = storageDirUrl()
        fm.createDirectoryAtURL(dirUrl, withIntermediateDirectories = true, attributes = null, error = null)
        fm.removeItemAtPath("$storageDir/$COMPLETE_MARKER", error = null)

        // HEAD all files to calculate totalBytes for progress
        val fileSizes = try {
            files.associateWith { path -> headContentLength(fileUrl(path)) }
        } catch (e: Exception) {
            emit(DownloadState.Error(e))
            return@flow
        }

        val totalBytes = fileSizes.values.sumOf { it.coerceAtLeast(0L) }
        var globalDownloaded = 0L

        for (path in files) {
            val fileName = path.substringAfterLast('/')
            val destUrl = dirUrl.URLByAppendingPathComponent(fileName)!!
            val expectedSize = fileSizes[path] ?: 0L
            val existingSize = fileSize(destUrl.path ?: "")

            if (expectedSize > 0L && existingSize == expectedSize) {
                globalDownloaded += expectedSize
                emit(DownloadState.Downloading(globalDownloaded, totalBytes))
                continue
            }

            if (expectedSize == -1L && existingSize > 0L) {
                globalDownloaded += existingSize
                emit(DownloadState.Downloading(globalDownloaded, totalBytes))
                continue
            }

            try {
                downloadFile(urlString = fileUrl(path), destUrl = destUrl)
                globalDownloaded += expectedSize.coerceAtLeast(fileSize(destUrl.path ?: ""))
                emit(DownloadState.Downloading(globalDownloaded, totalBytes))
            } catch (e: Exception) {
                emit(DownloadState.Error(e))
                return@flow
            }
        }

        fm.createFileAtPath("$storageDir/$COMPLETE_MARKER", contents = null, attributes = null)
        emit(DownloadState.Ready)
    }

    /**
     * Returns `true` only if all files were fully downloaded in a previous session.
     * Uses a [COMPLETE_MARKER] file written at the end of a successful [download].
     */
    actual fun isComplete(files: List<String>): Boolean =
        fm.fileExistsAtPath("$storageDir/$COMPLETE_MARKER")

    /**
     * Deletes all downloaded model files and the completion marker.
     */
    actual fun deleteAll() {
        fm.removeItemAtPath(storageDir, error = null)
    }

    // --- Private ---

    private fun fileUrl(path: String) = "$baseUrl/$path"

    private fun storageDirUrl(): NSURL =
        NSURL.fileURLWithPath(storageDir, isDirectory = true)

    private fun fileSize(path: String): Long {
        if (path.isEmpty()) return 0L
        val attrs = fm.attributesOfItemAtPath(path, error = null) ?: return 0L
        return (attrs["NSFileSize"] as? Long) ?: 0L
    }

    /** HEAD request to get [Content-Length] without downloading the file body. */
    private suspend fun headContentLength(urlString: String): Long =
        suspendCancellableCoroutine { cont ->
            val url = NSURL.URLWithString(urlString) ?: run {
                cont.resumeWithException(IllegalArgumentException("Invalid URL: $urlString"))
                return@suspendCancellableCoroutine
            }
            val request = NSMutableURLRequest.requestWithURL(url).apply {
                HTTPMethod = "HEAD"
            }
            val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { _, response, error ->
                when {
                    error != null -> cont.resumeWithException(Exception(error.localizedDescription))
                    else -> {
                        val length = (response as? NSHTTPURLResponse)
                            ?.expectedContentLength ?: -1L
                        cont.resume(length)
                    }
                }
            }
            task.resume()
            cont.invokeOnCancellation { task.cancel() }
        }

    /**
     * Downloads [urlString] to [destUrl] using [NSURLSession.downloadTaskWithURL].
     * The session downloads to a system temp file and we move it to [destUrl].
     */
    private suspend fun downloadFile(
        urlString: String,
        destUrl: NSURL,
    ): Unit = suspendCancellableCoroutine { cont ->
        val url = NSURL.URLWithString(urlString) ?: run {
            cont.resumeWithException(IllegalArgumentException("Invalid URL: $urlString"))
            return@suspendCancellableCoroutine
        }

        val task = NSURLSession.sharedSession.downloadTaskWithURL(url) { tempUrl, _, error ->
            when {
                error != null -> cont.resumeWithException(Exception(error.localizedDescription))
                tempUrl == null -> cont.resumeWithException(Exception("No temp file for $urlString"))
                else -> {
                    fm.moveItemAtURL(tempUrl, toURL = destUrl, error = null)
                    cont.resume(Unit)
                }
            }
        }
        task.resume()
        cont.invokeOnCancellation { task.cancel() }
    }
}