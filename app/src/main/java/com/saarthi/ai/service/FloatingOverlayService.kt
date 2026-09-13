package com.saarthi.ai.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.saarthi.ai.MainActivity
import com.saarthi.ai.SaarthiApplication
import com.saarthi.ai.ai.InferenceResult
import com.saarthi.ai.ai.SaarthiInferenceOrchestrator
import com.saarthi.ai.overlay.OverlayManager
import com.saarthi.ai.voice.BhashiniVoiceClient
import kotlinx.coroutines.*

/**
 * The heart of Saarthi AI — the Foreground Service that ties together:
 *
 *   Floating Bubble → Voice Recording → Speech STT → Gemini Nano → Highlight Overlay → TTS Guidance
 *
 * LIFECYCLE:
 * 1. Started by MainActivity after all permissions are granted
 * 2. Runs as a foreground service with a persistent notification (+ Kill Switch)
 * 3. Shows the floating mic bubble via OverlayManager
 * 4. On bubble tap: records voice or accepts quick action chip → runs AI inference → highlights target
 * 5. Stopped via Kill Switch (notification action or dashboard button)
 *
 * PRIVACY ENFORCEMENT:
 * - All screen data is volatile (RAM only), destroyed after each inference cycle
 * - Zero cloud screen telemetry
 * - On service stop, all overlays, models, and audio resources are released
 */
class FloatingOverlayService : Service() {

    companion object {
        private const val TAG = "SaarthiService"
        const val ACTION_SHOW_FLOATING_BUBBLE = "com.saarthi.ai.SHOW_FLOATING_BUBBLE"
        const val ACTION_STOP_SERVICE = "com.saarthi.ai.STOP_SERVICE"
        private const val NOTIFICATION_ID = 1001

        // Auto-dismiss highlight after 12 seconds
        private const val HIGHLIGHT_AUTO_DISMISS_MS = 12_000L
        private const val SPEECH_TIMEOUT_MS = 8_000L
    }

    // Core components
    private var overlayManager: OverlayManager? = null
    private var voiceClient: BhashiniVoiceClient? = null
    private var inferenceOrchestrator: SaarthiInferenceOrchestrator? = null

    // Coroutine scope for async operations
    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Unhandled exception in service scope: ${throwable.message}", throwable)
        }
    )

    private val mainHandler = Handler(Looper.getMainLooper())

    // State
    private var isListening = false
    private var userLanguage = "Hindi"

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Service Lifecycle ────────────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                Log.i(TAG, "Kill switch activated — stopping all Saarthi services")
                shutdownEverything()
                return START_NOT_STICKY
            }
            ACTION_SHOW_FLOATING_BUBBLE -> {
                startForeground(NOTIFICATION_ID, buildForegroundNotification())
                initializeComponents()
                return START_STICKY
            }
        }
        return START_STICKY
    }

    private fun initializeComponents() {
        Log.i(TAG, "═══ Initializing Saarthi Floating Companion ═══")

        // 1. Initialize Overlay Manager
        overlayManager = OverlayManager(this)

        // 2. Initialize Voice Client
        voiceClient = BhashiniVoiceClient(this).apply {
            initialize()
            onAmplitudeUpdate = { amplitude ->
                mainHandler.post {
                    overlayManager?.updateListeningAmplitude(amplitude)
                }
            }
        }

        // 3. Initialize AI Orchestrator
        inferenceOrchestrator = SaarthiInferenceOrchestrator(this).apply {
            this.userLanguage = this@FloatingOverlayService.userLanguage
        }

        // 4. Initialize Gemini Nano in background
        serviceScope.launch {
            val aiReady = inferenceOrchestrator?.initialize() ?: false
            Log.i(TAG, if (aiReady) "✓ Gemini Nano ready" else "✗ Gemini Nano fallback active")
        }

        // 5. Show the floating bubble
        mainHandler.post {
            showFloatingBubble()
        }

        Log.i(TAG, "═══ Saarthi Floating Companion initialized ═══")
    }

    // ── Floating Bubble ──────────────────────────────────────────────

    private fun showFloatingBubble() {
        overlayManager?.showBubble()

        overlayManager?.bubbleView?.onBubbleTapped = {
            if (!isListening) {
                startListeningFlow()
            } else {
                stopListeningFlow()
            }
        }
    }

    // ── Main Voice / Quick Action Pipeline ────────────────────────────

    private fun startListeningFlow() {
        isListening = true

        // Update bubble to active state
        mainHandler.post {
            overlayManager?.bubbleView?.isActive = true
        }

        // Show listening sheet
        mainHandler.post {
            overlayManager?.showListeningSheet()
            overlayManager?.listeningSheet?.onCancelTapped = {
                stopListeningFlow()
            }
            overlayManager?.listeningSheet?.onQuickActionTapped = { actionIntent ->
                Log.i(TAG, "Quick action chip tapped: \"$actionIntent\"")
                voiceClient?.stopRecording()
                executeIntentFlow(actionIntent)
            }
        }

        // Start recording with speech timeout guard
        serviceScope.launch {
            try {
                Log.d(TAG, "Step 1: Listening for voice input...")

                val transcriptionResult = withTimeoutOrNull(SPEECH_TIMEOUT_MS) {
                    voiceClient?.recordAndTranscribe(
                        language = userLanguage,
                        onPartialResult = { partial ->
                            mainHandler.post {
                                overlayManager?.updateTranscription(partial)
                            }
                        }
                    )
                }

                if (transcriptionResult == null || !transcriptionResult.success) {
                    val errorMsg = transcriptionResult?.error ?: "कृपया दोबारा बोलें या नीचे विकल्प चुनें"
                    Log.w(TAG, "Transcription failed or timed out: $errorMsg")
                    mainHandler.post {
                        overlayManager?.listeningSheet?.statusText = errorMsg
                    }
                    delay(2500)
                    mainHandler.post { stopListeningFlow() }
                    return@launch
                }

                val userIntent = transcriptionResult.translatedText
                    ?: transcriptionResult.originalText
                    ?: ""

                Log.d(TAG, "Transcribed intent: \"$userIntent\"")
                executeIntentFlow(userIntent)

            } catch (e: Exception) {
                Log.e(TAG, "Voice pipeline error: ${e.message}", e)
                mainHandler.post { stopListeningFlow() }
            }
        }
    }

    /**
     * Executes the screen parse, AI inference, and highlight drawing for any given user intent.
     */
    private fun executeIntentFlow(userIntent: String) {
        serviceScope.launch {
            try {
                mainHandler.post {
                    overlayManager?.updateTranscription(userIntent)
                    overlayManager?.setListeningProcessing(true)
                }

                Log.d(TAG, "Step 2: Running AI inference for intent: \"$userIntent\"")
                val inferenceResult = inferenceOrchestrator?.processUserIntent(userIntent)
                    ?: InferenceResult.Error("Orchestrator not initialized")

                // Step 3: Dismiss listening sheet and highlight target
                mainHandler.post {
                    overlayManager?.hideListeningSheet()
                    overlayManager?.bubbleView?.isActive = false
                    isListening = false
                }

                when (inferenceResult) {
                    is InferenceResult.Success -> {
                        val target = inferenceResult.target
                        Log.i(TAG, "✓ Target found! Drawing highlight at ${target.bounds}")

                        mainHandler.post {
                            overlayManager?.showHighlight(
                                bounds = target.bounds,
                                guidance = target.guidanceText,
                                action = "${target.actionDescription} • यहाँ दबाएं"
                            )
                        }

                        voiceClient?.speak(
                            text = target.guidanceText,
                            language = userLanguage
                        )

                        mainHandler.postDelayed({
                            overlayManager?.hideHighlight()
                        }, HIGHLIGHT_AUTO_DISMISS_MS)
                    }

                    is InferenceResult.NotFound -> {
                        Log.i(TAG, "○ Element not found: ${inferenceResult.reason}")
                        voiceClient?.speak(
                            text = inferenceResult.suggestion,
                            language = userLanguage
                        )
                    }

                    is InferenceResult.Error -> {
                        Log.e(TAG, "✗ Inference error: ${inferenceResult.message}")
                        voiceClient?.speak(
                            text = "माफ़ कीजिए, कुछ गड़बड़ हो गई। कृपया दोबारा बोलें।",
                            language = userLanguage
                        )
                    }

                    is InferenceResult.Unavailable -> {
                        Log.e(TAG, "✗ AI unavailable: ${inferenceResult.reason}")
                        voiceClient?.speak(
                            text = "AI सेवा उपलब्ध नहीं है। कृपया बाद में प्रयास करें।",
                            language = userLanguage
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing intent flow: ${e.message}", e)
                mainHandler.post { stopListeningFlow() }
            }
        }
    }

    /**
     * Cancels the current listening/inference flow.
     */
    private fun stopListeningFlow() {
        isListening = false
        voiceClient?.stopRecording()
        voiceClient?.stopSpeaking()

        mainHandler.post {
            overlayManager?.hideListeningSheet()
            overlayManager?.bubbleView?.isActive = false
        }

        Log.d(TAG, "Listening flow cancelled")
    }

    // ── Notification ─────────────────────────────────────────────────

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val killIntent = Intent(this, FloatingOverlayService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val killPendingIntent = PendingIntent.getService(
            this, 1, killIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SaarthiApplication.CHANNEL_ID_OVERLAY)
            .setContentTitle("Saarthi AI Active • सारथी चालू है")
            .setContentText("Tap the floating mic to ask for help. Tap here to open dashboard.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "⏻ Kill Switch (बंद करें)",
                killPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    // ── Shutdown ──────────────────────────────────────────────────────

    private fun shutdownEverything() {
        Log.i(TAG, "═══ Shutting down Saarthi ═══")

        serviceScope.cancel()

        mainHandler.post {
            overlayManager?.removeAllOverlays()
        }

        voiceClient?.release()
        voiceClient = null

        inferenceOrchestrator?.release()
        inferenceOrchestrator = null

        overlayManager = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        Log.i(TAG, "═══ Saarthi shutdown complete — all resources freed ═══")
    }

    override fun onDestroy() {
        super.onDestroy()
        shutdownEverything()
    }
}
