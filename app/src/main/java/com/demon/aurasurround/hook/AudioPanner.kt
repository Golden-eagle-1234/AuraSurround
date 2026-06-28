package com.demon.aurasurround.hook

import android.media.AudioTrack
import de.robv.android.xposed.XposedBridge
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * AudioPanner creates the signature 8D "rotation" effect by
 * continuously modulating left/right stereo gain on the AudioTrack
 * in a sine/cosine pattern — giving the illusion of sound rotating
 * around your head when using earphones/headphones.
 */
class AudioPanner(
    private val sessionId: Int,
    @Volatile var speed: Float = 0.5f  // rotations per second
) {
    private var thread: Thread? = null
    @Volatile private var running = false
    private var angle = 0.0

    // We use reflection to call AudioTrack.setStereoVolume on the track
    // that owns this sessionId.
    // The hook keeps a weak reference to the AudioTrack.
    private var audioTrackRef: java.lang.ref.WeakReference<AudioTrack>? = null

    companion object {
        const val TAG = "AuraSurround/Panner"
        const val UPDATE_INTERVAL_MS = 50L  // 20 fps panning update
    }

    fun attachTrack(track: AudioTrack) {
        audioTrackRef = java.lang.ref.WeakReference(track)
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread({
            XposedBridge.log("$TAG: Panner thread started for session $sessionId")
            while (running) {
                try {
                    val track = audioTrackRef?.get()
                    if (track != null && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        // Compute left/right gains using circular panning law
                        // angle goes 0 -> 2π continuously
                        val leftGain  = ((cos(angle) + 1.0) / 2.0).toFloat().coerceIn(0.1f, 1.0f)
                        val rightGain = ((sin(angle) + 1.0) / 2.0).toFloat().coerceIn(0.1f, 1.0f)

                        try {
                            track.setStereoVolume(leftGain, rightGain)
                        } catch (_: Exception) {}

                        // Advance angle based on speed
                        angle += (2.0 * PI * speed * UPDATE_INTERVAL_MS / 1000.0)
                        if (angle > 2.0 * PI) angle -= 2.0 * PI
                    }
                    Thread.sleep(UPDATE_INTERVAL_MS)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Throwable) {
                    XposedBridge.log("$TAG: Panner error: ${e.message}")
                }
            }
            XposedBridge.log("$TAG: Panner thread stopped for session $sessionId")
        }, "AuraPanner-$sessionId")
        thread?.isDaemon = true
        thread?.start()
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        // Reset to center
        try { audioTrackRef?.get()?.setStereoVolume(1f, 1f) } catch (_: Exception) {}
    }
}
