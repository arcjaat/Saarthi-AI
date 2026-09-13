package com.saarthi.ai

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.saarthi.ai.permission.PermissionManager
import com.saarthi.ai.service.FloatingOverlayService
import com.saarthi.ai.ui.OnboardingScreen
import com.saarthi.ai.ui.theme.SaarthiTheme

class MainActivity : ComponentActivity() {

    private var isOverlayGranted by mutableStateOf(false)
    private var isAccessibilityGranted by mutableStateOf(false)
    private var isAudioGranted by mutableStateOf(false)

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        isAudioGranted = granted
        if (!granted) {
            Toast.makeText(
                this,
                "Microphone permission is needed so you can speak to Saarthi.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        refreshPermissionStates()

        setContent {
            SaarthiTheme {
                OnboardingScreen(
                    isOverlayGranted = isOverlayGranted,
                    isAccessibilityGranted = isAccessibilityGranted,
                    isAudioGranted = isAudioGranted,
                    onRequestOverlay = {
                        PermissionManager.openOverlaySettings(this)
                    },
                    onRequestAccessibility = {
                        Toast.makeText(
                            this,
                            "Locate 'Saarthi AI Companion' and switch it ON.",
                            Toast.LENGTH_LONG
                        ).show()
                        PermissionManager.openAccessibilitySettings(this)
                    },
                    onRequestAudio = {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onStartService = {
                        startCompanionService()
                    },
                    onEmergencyKill = {
                        emergencyKillSwitch()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()
    }

    private fun refreshPermissionStates() {
        isOverlayGranted = PermissionManager.isOverlayPermissionGranted(this)
        isAccessibilityGranted = PermissionManager.isAccessibilityServiceEnabled(this)
        isAudioGranted = PermissionManager.isRecordAudioPermissionGranted(this)
    }

    private fun startCompanionService() {
        if (!PermissionManager.areAllPermissionsGranted(this)) {
            Toast.makeText(
                this,
                "Please grant all required permissions first.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Start Floating Overlay Companion
        val intent = Intent(this, FloatingOverlayService::class.java).apply {
            action = FloatingOverlayService.ACTION_SHOW_FLOATING_BUBBLE
        }
        startForegroundService(intent)

        Toast.makeText(
            this,
            "Saarthi is now active! The floating microphone is ready.",
            Toast.LENGTH_LONG
        ).show()

        // Minimize app so user can return to their home screen or banking apps
        moveTaskToBack(true)
    }

    private fun emergencyKillSwitch() {
        // Stop floating overlay service
        val intent = Intent(this, FloatingOverlayService::class.java).apply {
            action = FloatingOverlayService.ACTION_STOP_SERVICE
        }
        startService(intent)

        Toast.makeText(
            this,
            "Saarthi stopped. Opening Accessibility settings to disable service.",
            Toast.LENGTH_LONG
        ).show()

        PermissionManager.openAccessibilitySettings(this)
    }
}
