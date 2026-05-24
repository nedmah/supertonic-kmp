package com.nedmah.supertonic_kmp.api


import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.NSObject
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import kotlin.concurrent.Volatile

private const val WAV_HEADER_SIZE = 44

/**
 * iOS [PlatformPlayer] backed by [AVAudioPlayer].
 *
 * Used only by [com.nedmah.supertonic_kmp.api.SupertonicTts.speak].
 * If you call [com.nedmah.supertonic_kmp.api.SupertonicTts.generate] directly,
 * this object is never touched.
 *
 * [play] blocks synchronously via [dispatch_semaphore_wait] until the
 * audio finishes or [stop] is called.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual object PlatformPlayer {

    @Volatile
    private var activePlayer: AVAudioPlayer? = null

    /**
     * Plays a WAV [ByteArray] synchronously, blocking until playback completes.
     *
     * Skips the 44-byte WAV header and passes raw PCM bytes to [AVAudioPlayer].
     * Blocks the calling thread via a [dispatch_semaphore_wait].
     */
    actual fun play(wav: ByteArray) {
        if (wav.size <= WAV_HEADER_SIZE) return

        val semaphore = dispatch_semaphore_create(0)

        var delegate: CompletionDelegate? = null

        wav.usePinned { pinned ->
            val data = NSData.dataWithBytes(
                bytes = pinned.addressOf(0),
                length = wav.size.toULong(),
            )
            val player = AVAudioPlayer(data, error = null) ?: return@usePinned

            delegate = CompletionDelegate {
                dispatch_semaphore_signal(semaphore)
            }

            player.delegate = delegate
            activePlayer = player
            player.prepareToPlay()
            player.play()
        }

        dispatch_semaphore_wait(semaphore, DISPATCH_TIME_FOREVER)
        delegate = null
        activePlayer = null
    }

    /** Stops the current playback immediately. Safe to call from any thread. */
    actual fun stop() {
        activePlayer?.stop()
        activePlayer = null
    }
}

/**
 * [AVAudioPlayerDelegateProtocol] that fires a callback when playback ends.
 */
private class CompletionDelegate(
    private val onFinished: () -> Unit,
) : NSObject(), AVAudioPlayerDelegateProtocol {

    override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
        onFinished()
    }
}