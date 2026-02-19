package com.arm.translator

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * Handles audio playback of synthesized speech via AudioTrack API.
 * Plays raw PCM float audio from the TTS engine.
 */
class AudioPlaybackManager {

    companion object {
        private const val TAG = "AudioPlayback"
        const val SAMPLE_RATE = 22050  // VITS typically outputs at 22050Hz
    }

    private var audioTrack: AudioTrack? = null

    fun play(audioData: FloatArray) {
        if (audioData.isEmpty()) {
            Log.w(TAG, "Empty audio data, skipping playback")
            return
        }

        val bufferSize = audioData.size * 4  // 4 bytes per float

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack?.write(audioData, 0, audioData.size, AudioTrack.WRITE_BLOCKING)
        audioTrack?.play()

        // Wait for playback to complete
        val durationMs = (audioData.size * 1000L) / SAMPLE_RATE
        Thread.sleep(durationMs + 100)

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null

        Log.i(TAG, "Played ${audioData.size} samples (${durationMs}ms)")
    }

    fun release() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}
