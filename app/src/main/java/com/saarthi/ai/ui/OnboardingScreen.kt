package com.saarthi.ai.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saarthi.ai.ui.theme.*

@Composable
fun OnboardingScreen(
    isOverlayGranted: Boolean,
    isAccessibilityGranted: Boolean,
    isAudioGranted: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestAudio: () -> Unit,
    onStartService: () -> Unit,
    onEmergencyKill: () -> Unit
) {
    val allGranted = isOverlayGranted && isAccessibilityGranted && isAudioGranted
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = SurfaceBackground,
        bottomBar = {
            Surface(
                color = SurfaceCard,
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onStartService,
                        enabled = allGranted,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarmAmber,
                            disabledContainerColor = Color(0xFFCBD5E1),
                            contentColor = Color.White,
                            disabledContentColor = Color(0xFF64748B)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                    ) {
                        Text(
                            text = if (allGranted) "Start Using Saarthi • शुरू करें" else "Please Complete Steps Above",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Designed with love for elders • WCAG AAA Accessible",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 14.sp,
                        color = TextMuted
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header: Avatar & Title
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(PrimarySapphire, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Saarthi AI Emblem",
                    tint = WarmAmberLight,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Saarthi (सारथी)",
                style = MaterialTheme.typography.headlineLarge,
                color = PrimarySapphire,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Your voice-guided digital companion for banking and daily apps",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
            )

            // Privacy Guarantee Trust Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = StatusSecureGreenBg),
                border = BorderStroke(2.dp, StatusSecureGreen),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Security Verified",
                            tint = StatusSecureGreen,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "100% On-Device & Safe",
                            style = MaterialTheme.typography.titleLarge,
                            color = StatusSecureGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "• Zero Cloud Screen Storage: Processed in phone RAM only.\n" +
                               "• Numbers & Balances Masked: Redacted before AI sees it.\n" +
                               "• Offline Gemini Nano: Powered 100% on your device.",
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 24.sp,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Step 1: Accessibility Service
            PermissionStepCard(
                stepNumber = "Step 1",
                title = "Allow Reading Button Names",
                description = "Enables Accessibility Service so Saarthi can locate buttons like 'Send Money' or 'Check Balance'.",
                icon = Icons.Default.Visibility,
                isGranted = isAccessibilityGranted,
                actionButtonText = "Enable Accessibility (सारथी चालू करें)",
                onAction = onRequestAccessibility
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step 2: Overlay Permission
            PermissionStepCard(
                stepNumber = "Step 2",
                title = "Allow Screen Guidance Box",
                description = "Enables 'Draw Over Other Apps' so Saarthi can highlight the exact button with a glowing golden box.",
                icon = Icons.Default.Layers,
                isGranted = isOverlayGranted,
                actionButtonText = "Allow Draw Over Apps (स्क्रीन बॉक्स अनुमति)",
                onAction = onRequestOverlay
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Step 3: Microphone Permission
            PermissionStepCard(
                stepNumber = "Step 3",
                title = "Microphone for Voice Input",
                description = "Allows you to speak to Saarthi in Hindi, Tamil, Telugu, English, or your mother tongue.",
                icon = Icons.Default.Mic,
                isGranted = isAudioGranted,
                actionButtonText = "Allow Microphone (माइक अनुमति)",
                onAction = onRequestAudio
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Emergency Kill Switch Card
            Card(
                colors = CardDefaults.cardColors(containerColor = StatusAlertRedBg),
                border = BorderStroke(1.5.dp, StatusAlertRed),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Emergency Kill Switch",
                            style = MaterialTheme.typography.titleLarge,
                            fontSize = 18.sp,
                            color = StatusAlertRed,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Turn off screen guidance and floating overlay immediately.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }

                    Button(
                        onClick = onEmergencyKill,
                        colors = ButtonDefaults.buttonColors(containerColor = StatusAlertRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Stop",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun PermissionStepCard(
    stepNumber: String,
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    actionButtonText: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(
            1.5.dp,
            if (isGranted) StatusSecureGreen else SurfaceBorder
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (isGranted) StatusSecureGreenBg else WarmAmberGlow,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
                        contentDescription = null,
                        tint = if (isGranted) StatusSecureGreen else WarmAmber,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stepNumber.uppercase(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isGranted) StatusSecureGreen else WarmAmber
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimarySapphire
                    )
                }

                if (isGranted) {
                    Text(
                        text = "GRANTED",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusSecureGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 24.sp
            )

            if (!isGranted) {
                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimarySapphire,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = actionButtonText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
