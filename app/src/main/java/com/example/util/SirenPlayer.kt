package com.example.util

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlin.concurrent.thread
import kotlin.math.sin

class SirenPlayer(private val context: Context) {
    private var audioTrack: AudioTrack? = null
    @Volatile
    private var isPlaying = false
    private var playbackThread: Thread? = null

    fun start() {
        if (isPlaying) return
        isPlaying = true
        Log.d("SirenPlayer", "Synthesized Siren playback starting.")

        playbackThread = thread(start = true) {
            val sampleRate = 22050
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            val bufferSize = 1024
            val writeSize = minBufferSize.coerceAtLeast(bufferSize * 2)

            val track = try {
                AudioTrack(
                    AudioManager.STREAM_ALARM,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    writeSize,
                    AudioTrack.MODE_STREAM
                ).apply {
                    play()
                }
            } catch (e: Exception) {
                Log.e("SirenPlayer", "Failed to initialize AudioTrack: ${e.message}")
                isPlaying = false
                return@thread
            }

            audioTrack = track
            val buffer = ShortArray(bufferSize)
            var phase = 0.0
            
            // Loop duration for the siren wail frequency sweep cycle (1.6 seconds total frequency cycle)
            val sirenPeriodSeconds = 1.6
            var sirenTime = 0.0

            try {
                while (isPlaying) {
                    for (i in 0 until bufferSize) {
                        val time = sirenTime + (i.toDouble() / sampleRate)
                        // Modulate sweep phase between 0 and 1
                        val sweepPhase = (time % sirenPeriodSeconds) / sirenPeriodSeconds
                        
                        // Sweeps back and forth smoothly between 500 Hz (low pitch) and 1250 Hz (high pitch)
                        val currentFrequency = if (sweepPhase < 0.5) {
                            500.0 + (1250.0 - 500.0) * (sweepPhase * 2.0)
                        } else {
                            1250.0 - (1250.0 - 500.0) * ((sweepPhase - 0.5) * 2.0)
                        }

                        phase += 2.0 * Math.PI * currentFrequency / sampleRate
                        if (phase > 2.0 * Math.PI) {
                            phase -= 2.0 * Math.PI
                        }
                        
                        // Modulate sine wave to produce a loud premium alarm buzzer tone
                        buffer[i] = (sin(phase) * 20000.0).toInt().toShort()
                    }
                    
                    sirenTime += (bufferSize.toDouble() / sampleRate)
                    if (sirenTime > 1000.0) sirenTime = 0.0 // avoid floating precision drift

                    if (isPlaying) {
                        track.write(buffer, 0, bufferSize)
                    }
                }
            } catch (e: Exception) {
                Log.e("SirenPlayer", "Error during sound synthesizer wave write: ${e.message}")
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (e: Exception) {
                    Log.e("SirenPlayer", "Error cleaning up synthesized Track: ${e.message}")
                }
                if (audioTrack == track) {
                    audioTrack = null
                }
            }
        }
    }

    fun stop() {
        isPlaying = false
        playbackThread?.interrupt()
        playbackThread = null
        try {
            audioTrack?.let {
                it.stop()
                it.release()
            }
        } catch (e: Exception) {
            // ignored
        }
        audioTrack = null
        Log.d("SirenPlayer", "Synthesized Siren playback stopped successfully.")
    }

    fun isPlaying(): Boolean {
        return isPlaying
    }
}
