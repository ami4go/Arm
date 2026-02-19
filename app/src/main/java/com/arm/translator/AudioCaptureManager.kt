package com.arm.translator

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Handles microphone audio capture at 16kHz mono (required by Whisper).
 * Uses Android AudioRecord API for low-latency PCM capture.
 */
class AudioCaptureManager {

    companion object {
        private const val TAG = "AudioCapture"
        const val SAMPLE_RATE = 16000  // 16kHz for Whisper
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private val audioBuffer = ByteArrayOutputStream()

    fun startRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize * 2
        )

        audioBuffer.reset()
        isRecording = true
        audioRecord?.startRecording()

        recordingThread = Thread {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    synchronized(audioBuffer) {
                        audioBuffer.write(buffer, 0, read)
                    }
                }
            }
        }
        recordingThread?.start()

        Log.i(TAG, "Recording started at ${SAMPLE_RATE}Hz")
    }

    fun stopRecording(): FloatArray? {
        isRecording = false
        recordingThread?.join(1000)
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        val rawBytes: ByteArray
        synchronized(audioBuffer) {
            rawBytes = audioBuffer.toByteArray()
        }

        if (rawBytes.isEmpty()) return null

        // Convert PCM 16-bit bytes to float array (normalized to [-1, 1])
        val shortBuffer = ByteBuffer.wrap(rawBytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()
        val samples = FloatArray(shortBuffer.remaining())
        for (i in samples.indices) {
            samples[i] = shortBuffer.get(i) / 32768.0f
        }

        Log.i(TAG, "Recording stopped. Captured ${samples.size} samples (${samples.size / SAMPLE_RATE.toFloat()}s)")
        return samples
    }

    fun release() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
