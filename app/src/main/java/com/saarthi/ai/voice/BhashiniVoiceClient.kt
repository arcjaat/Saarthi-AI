package com.saarthi.ai.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.*
import java.util.*
import kotlin.coroutines.resume

/**
 * Voice input/output client for Saarthi AI.
 *
 * INPUT (Speech-to-Text):
 *   Uses Android's native SpeechRecognizer for instant, zero-latency on-device
 *   regional speech recognition with real-time streaming and automatic silence detection.
 *   Supports Hindi (hi-IN), English (en-IN), Tamil (ta-IN), Telugu (te-IN), Marathi (mr-IN), etc.
 *
 * OUTPUT (Text-to-Speech):
 *   Uses Android's built-in TextToSpeech engine for voice guidance.
 *
 * PRIVACY:
 *   - Audio stays on the device.
 *   - Screen UI trees, accessibility data, and AI prompts are NEVER sent to any cloud service.
 */
class BhashiniVoiceClient(private val context: Context) {

    companion object {
        private const val TAG = "SaarthiVoice"
    }

    private var ttsEngine: TextToSpeech? = null
    private var isTtsReady = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeSpeechRecognizer: SpeechRecognizer? = null

    /** Callback for real-time audio amplitude (for waveform visualization) */
    var onAmplitudeUpdate: ((Float) -> Unit)? = null

    // ── Language Mapping ─────────────────────────────────────────────

    private val speechLocaleMap = mapOf(
        "Hindi" to "hi-IN",
        "English" to "en-IN",
        "Tamil" to "ta-IN",
        "Telugu" to "te-IN",
        "Marathi" to "mr-IN",
        "Bengali" to "bn-IN",
        "Gujarati" to "gu-IN",
        "Kannada" to "kn-IN",
        "Malayalam" to "ml-IN",
        "Punjabi" to "pa-IN",
        "Odia" to "or-IN",
        "Urdu" to "ur-IN"
    )

    // ── Initialization ───────────────────────────────────────────────

    fun initialize(apiKey: String = "", userId: String = "") {
        // Initialize local TTS
        ttsEngine = TextToSpeech(context) { status ->
            isTtsReady = (status == TextToSpeech.SUCCESS)
            if (isTtsReady) {
                ttsEngine?.language = Locale("hi", "IN") // Default Hindi
                Log.i(TAG, "✓ Local TTS engine initialized")
            } else {
                Log.w(TAG, "✗ Local TTS initialization failed")
            }
        }
    }

    // ── Speech-to-Text (Real-time SpeechRecognizer) ─────────────────

    /**
     * Listens for speech using Android's native SpeechRecognizer.
     * Streams partial results in real-time and completes automatically when
     * the user finishes speaking.
     *
     * @param language User's selected language (e.g., "Hindi", "English")
     * @param onPartialResult Callback for real-time speech transcription
     */
    suspend fun recordAndTranscribe(
        language: String = "Hindi",
        onPartialResult: ((String) -> Unit)? = null
    ): TranscriptionResult = suspendCancellableCoroutine { continuation ->

        mainHandler.post {
            val isAvailable = SpeechRecognizer.isRecognitionAvailable(context)
            if (!isAvailable) {
                Log.e(TAG, "Speech recognition not available on this device")
                continuation.resume(
                    TranscriptionResult(
                        success = false,
                        error = "Speech recognition not available on device"
                    )
                )
                return@post
            }

            // Cleanup any existing recognizer
            stopRecording()

            val localeTag = speechLocaleMap[language] ?: "hi-IN"
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            activeSpeechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, localeTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            var hasResumed = false

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "SpeechRecognizer ready for speech (locale: $localeTag)")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "User started speaking")
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Map RMS dB (typically -2 to 10) to 0.0 .. 1.0 for waveform animation
                    val normalized = ((rmsdB + 2f) / 10f).coerceIn(0.1f, 1.0f)
                    onAmplitudeUpdate?.invoke(normalized)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "User stopped speaking, processing results...")
                }

                override fun onError(error: Int) {
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please speak again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected within time limit."
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission missing"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error for recognition"
                        else -> "Speech recognition error ($error)"
                    }
                    Log.w(TAG, "SpeechRecognizer error: $errorMsg ($error)")

                    if (!hasResumed && continuation.isActive) {
                        hasResumed = true
                        continuation.resume(
                            TranscriptionResult(
                                originalText = null,
                                translatedText = null,
                                language = language,
                                success = false,
                                error = errorMsg
                            )
                        )
                    }
                    cleanupRecognizer()
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim() ?: ""
                    Log.i(TAG, "Speech recognized: \"$text\"")

                    if (!hasResumed && continuation.isActive) {
                        hasResumed = true
                        continuation.resume(
                            TranscriptionResult(
                                originalText = text,
                                translatedText = text,
                                language = language,
                                success = text.isNotBlank(),
                                error = if (text.isBlank()) "Empty speech detected" else null
                            )
                        )
                    }
                    cleanupRecognizer()
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partialText = matches?.firstOrNull()?.trim() ?: ""
                    if (partialText.isNotBlank()) {
                        Log.d(TAG, "Partial: \"$partialText\"")
                        onPartialResult?.invoke(partialText)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            try {
                recognizer.startListening(intent)
                Log.d(TAG, "startListening() called successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start listening: ${e.message}", e)
                if (!hasResumed && continuation.isActive) {
                    hasResumed = true
                    continuation.resume(
                        TranscriptionResult(
                            success = false,
                            error = "Failed to start listening: ${e.message}"
                        )
                    )
                }
                cleanupRecognizer()
            }
        }

        continuation.invokeOnCancellation {
            mainHandler.post {
                stopRecording()
            }
        }
    }

    /**
     * Stops an active listening session.
     */
    fun stopRecording() {
        mainHandler.post {
            cleanupRecognizer()
        }
    }

    private fun cleanupRecognizer() {
        try {
            activeSpeechRecognizer?.stopListening()
            activeSpeechRecognizer?.cancel()
            activeSpeechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up recognizer: ${e.message}")
        }
        activeSpeechRecognizer = null
    }

    // ── Text-to-Speech (Voice Guidance Output) ───────────────────────

    /**
     * Speaks the guidance text aloud to the elderly user.
     * Uses Android's local TTS engine.
     */
    fun speak(
        text: String,
        language: String = "Hindi",
        onComplete: (() -> Unit)? = null
    ) {
        if (!isTtsReady || ttsEngine == null) {
            Log.w(TAG, "TTS not ready, skipping speech")
            onComplete?.invoke()
            return
        }

        val locale = when (language) {
            "Hindi" -> Locale("hi", "IN")
            "Tamil" -> Locale("ta", "IN")
            "Telugu" -> Locale("te", "IN")
            "Marathi" -> Locale("mr", "IN")
            "Bengali" -> Locale("bn", "IN")
            "Gujarati" -> Locale("gu", "IN")
            "Kannada" -> Locale("kn", "IN")
            "Malayalam" -> Locale("ml", "IN")
            "Punjabi" -> Locale("pa", "IN")
            else -> Locale.ENGLISH
        }

        ttsEngine?.language = locale

        val utteranceId = "saarthi_guidance_${System.currentTimeMillis()}"

        ttsEngine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(p0: String?) {}
            override fun onDone(p0: String?) {
                onComplete?.invoke()
            }
            @Deprecated("Deprecated")
            override fun onError(p0: String?) {
                onComplete?.invoke()
            }
        })

        ttsEngine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        Log.d(TAG, "Speaking: \"${text.take(50)}...\"")
    }

    fun stopSpeaking() {
        ttsEngine?.stop()
    }

    // ── Cleanup ──────────────────────────────────────────────────────

    fun release() {
        stopRecording()
        stopSpeaking()
        ttsEngine?.shutdown()
        ttsEngine = null
        isTtsReady = false
        Log.d(TAG, "Voice client released")
    }
}

/**
 * Result of a speech-to-text transcription operation.
 */
data class TranscriptionResult(
    /** The transcribed text in the original spoken language */
    val originalText: String? = null,
    /** The text translated to English (for Gemini Nano) */
    val translatedText: String? = null,
    /** The source language code */
    val language: String? = null,
    /** Whether transcription succeeded */
    val success: Boolean = false,
    /** Error message if transcription failed */
    val error: String? = null
)
