package com.arm.translator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ArmTranslator"
        private const val REQUEST_AUDIO_PERMISSION = 100

        init {
            System.loadLibrary("arm_translator")
        }
    }

    // Pipeline components
    private lateinit var audioCaptureManager: AudioCaptureManager
    private lateinit var sttEngine: SttEngine
    private lateinit var translationEngine: TranslationEngine
    private lateinit var ttsEngine: TtsEngine
    private lateinit var audioPlaybackManager: AudioPlaybackManager
    private lateinit var pipelineOrchestrator: PipelineOrchestrator

    // UI elements
    private lateinit var btnMic: ImageButton
    private lateinit var btnSwapLangs: ImageButton
    private lateinit var spinnerSource: Spinner
    private lateinit var spinnerTarget: Spinner
    private lateinit var tvSourceText: TextView
    private lateinit var tvTranslatedText: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvLatency: TextView
    private lateinit var cardSource: View
    private lateinit var cardTarget: View

    private var isRecording = false
    private var isInitialized = false
    private var sourceLanguage = "Hindi"
    private var targetLanguage = "English"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initUI()
        checkPermissions()
    }

    private fun initUI() {
        btnMic = findViewById(R.id.btn_mic)
        btnSwapLangs = findViewById(R.id.btn_swap_langs)
        spinnerSource = findViewById(R.id.spinner_source_lang)
        spinnerTarget = findViewById(R.id.spinner_target_lang)
        tvSourceText = findViewById(R.id.tv_source_text)
        tvTranslatedText = findViewById(R.id.tv_translated_text)
        tvStatus = findViewById(R.id.tv_status)
        progressBar = findViewById(R.id.progress_bar)
        tvLatency = findViewById(R.id.tv_latency)
        cardSource = findViewById(R.id.card_source)
        cardTarget = findViewById(R.id.card_target)

        // Language spinner setup
        val languages = arrayOf("Hindi", "English")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languages)
        spinnerSource.adapter = adapter
        spinnerTarget.adapter = adapter
        spinnerSource.setSelection(0) // Hindi
        spinnerTarget.setSelection(1) // English

        spinnerSource.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                sourceLanguage = languages[pos]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerTarget.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                targetLanguage = languages[pos]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Swap languages button
        btnSwapLangs.setOnClickListener {
            val srcPos = spinnerSource.selectedItemPosition
            val tgtPos = spinnerTarget.selectedItemPosition
            spinnerSource.setSelection(tgtPos)
            spinnerTarget.setSelection(srcPos)
        }

        // Mic button — toggle recording
        btnMic.setOnClickListener {
            if (!isInitialized) {
                updateStatus("Models are still loading, please wait...")
                return@setOnClickListener
            }

            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        // Initially disable mic
        btnMic.isEnabled = false
        updateStatus("Loading models...")
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_AUDIO_PERMISSION
            )
        } else {
            initializePipeline()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializePipeline()
            } else {
                updateStatus("Audio permission denied. Cannot proceed.")
                Toast.makeText(this, "Microphone permission is required!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initializePipeline() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.VISIBLE
                    updateStatus("Extracting models to storage...")
                }

                // Copy model files from assets to internal storage
                val modelsDir = File(filesDir, "models")
                modelsDir.mkdirs()

                withContext(Dispatchers.Main) { updateStatus("Initializing Speech-to-Text (Whisper)...") }
                sttEngine = SttEngine(this@MainActivity, modelsDir)

                withContext(Dispatchers.Main) { updateStatus("Initializing Translation Engine (Gemma)...") }
                translationEngine = TranslationEngine(this@MainActivity, modelsDir)

                withContext(Dispatchers.Main) { updateStatus("Initializing Text-to-Speech (VITS)...") }
                ttsEngine = TtsEngine(this@MainActivity, modelsDir)

                withContext(Dispatchers.Main) { updateStatus("Initializing audio systems...") }
                audioCaptureManager = AudioCaptureManager()
                audioPlaybackManager = AudioPlaybackManager()

                pipelineOrchestrator = PipelineOrchestrator(
                    sttEngine, translationEngine, ttsEngine,
                    audioCaptureManager, audioPlaybackManager
                )

                isInitialized = true

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnMic.isEnabled = true
                    btnMic.alpha = 1.0f
                    updateStatus("Ready! Tap the mic to start speaking.")
                }

                Log.i(TAG, "Pipeline initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize pipeline", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    updateStatus("Error: ${e.message}")
                }
            }
        }
    }

    private fun startRecording() {
        isRecording = true
        btnMic.setImageResource(R.drawable.ic_mic_active)
        tvSourceText.text = ""
        tvTranslatedText.text = ""
        updateStatus("🎤 Listening... Speak in $sourceLanguage")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()

                // Step 1: Record audio
                withContext(Dispatchers.Main) { updateStatus("🎤 Recording...") }
                audioCaptureManager.startRecording()

                // Wait for user to stop or auto-stop after silence
                delay(5000) // Record for 5 seconds max
                val audioData = audioCaptureManager.stopRecording()

                if (audioData == null || audioData.isEmpty()) {
                    withContext(Dispatchers.Main) { updateStatus("No audio captured. Try again.") }
                    return@launch
                }

                // Step 2: Speech-to-Text
                val sttStart = System.currentTimeMillis()
                withContext(Dispatchers.Main) { updateStatus("📝 Transcribing speech...") }
                val transcribedText = sttEngine.transcribe(audioData)
                val sttTime = System.currentTimeMillis() - sttStart

                withContext(Dispatchers.Main) {
                    tvSourceText.text = transcribedText
                    updateStatus("📝 Transcribed in ${sttTime}ms")
                }

                if (transcribedText.isBlank()) {
                    withContext(Dispatchers.Main) { updateStatus("Could not understand speech. Try again.") }
                    return@launch
                }

                // Step 3: Translation
                val translateStart = System.currentTimeMillis()
                withContext(Dispatchers.Main) { updateStatus("🌐 Translating to $targetLanguage...") }
                val translatedText = translationEngine.translate(
                    transcribedText, sourceLanguage, targetLanguage
                )
                val translateTime = System.currentTimeMillis() - translateStart

                withContext(Dispatchers.Main) {
                    tvTranslatedText.text = translatedText
                    updateStatus("🌐 Translated in ${translateTime}ms")
                }

                // Step 4: Text-to-Speech
                val ttsStart = System.currentTimeMillis()
                withContext(Dispatchers.Main) { updateStatus("🔊 Synthesizing speech...") }
                val audioOutput = ttsEngine.synthesize(translatedText)
                val ttsTime = System.currentTimeMillis() - ttsStart

                // Step 5: Play audio
                withContext(Dispatchers.Main) { updateStatus("🔊 Playing translation...") }
                audioPlaybackManager.play(audioOutput)

                val totalTime = System.currentTimeMillis() - startTime

                withContext(Dispatchers.Main) {
                    tvLatency.text = "STT: ${sttTime}ms | LLM: ${translateTime}ms | TTS: ${ttsTime}ms | Total: ${totalTime}ms"
                    tvLatency.visibility = View.VISIBLE
                    updateStatus("✅ Done! Tap mic to translate again.")
                }

                Log.i(TAG, "Pipeline completed: STT=${sttTime}ms, Translate=${translateTime}ms, TTS=${ttsTime}ms, Total=${totalTime}ms")

            } catch (e: Exception) {
                Log.e(TAG, "Pipeline error", e)
                withContext(Dispatchers.Main) {
                    updateStatus("Error: ${e.message}")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isRecording = false
                    btnMic.setImageResource(R.drawable.ic_mic)
                }
            }
        }
    }

    private fun stopRecording() {
        isRecording = false
        audioCaptureManager.stopRecording()
        btnMic.setImageResource(R.drawable.ic_mic)
        updateStatus("Processing...")
    }

    private fun updateStatus(status: String) {
        tvStatus.text = status
        Log.d(TAG, "Status: $status")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isInitialized) {
            sttEngine.release()
            translationEngine.release()
            ttsEngine.release()
            audioCaptureManager.release()
            audioPlaybackManager.release()
        }
    }
}
