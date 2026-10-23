package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MxBluePrimary

/**
 * Guided Hindi (Devanagari) offline voice installer. Android apps cannot copy
 * voice data directly; this walks the user through the engine's own installer
 * and re-checks availability when they come back.
 */
@Composable
fun HindiVoiceSetupDialog(
    uiState: TtsUiState,
    onInstallVoiceData: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onOpenPlayStore: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val installed = uiState.isHindiOfflineVoiceAvailable
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("hindi_voice_setup_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (installed) Color(0xFF10B981) else MxBluePrimary,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.size(28.dp)
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hindi Offline Voice", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = uiState.ttsEngineLabel,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (installed) Color(0xFF10B981).copy(alpha = 0.14f)
                    else Color(0xFFFFB300).copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, if (installed) Color(0xFF10B981) else Color(0xFFFFB300)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (installed) Icons.Default.Check else Icons.Default.Download,
                            contentDescription = null,
                            tint = if (installed) Color(0xFF10B981) else Color(0xFFFFB300),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (installed) {
                                    "Hindi Devanagari voice installed"
                                } else {
                                    "Hindi Devanagari voice missing"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = uiState.selectedVoice?.displayName
                                    ?: "Voice: engine default",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.padding(top = 10.dp))

                Text(
                    text = uiState.voiceDiagnostics,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.padding(top = 6.dp))

                StepRow("1", "Install Voice Data dabao — engine ka installer khulega.")
                StepRow("2", "List me Hindi (हिन्दी) chuno aur Download/Update karo.")
                StepRow("3", "Wapas aakar Refresh dabao — row green ho jayegi.")

                Spacer(modifier = Modifier.padding(top = 8.dp))

                Text(
                    text = "Iske baad offline reader poora Devanagari Hindi (deep male voice) me bolega — bina internet.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onInstallVoiceData,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MxBluePrimary),
                modifier = Modifier.testTag("hindi_voice_install_btn")
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Install Voice Data", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRefresh, modifier = Modifier.testTag("hindi_voice_refresh_btn")) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Refresh", fontSize = 12.sp)
                }
                TextButton(onClick = onOpenTtsSettings) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("TTS Settings", fontSize = 12.sp)
                }
                IconButton(onClick = onOpenPlayStore) {
                    Icon(
                        Icons.Default.Shop,
                        contentDescription = "Get TTS engine",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    )
}

@Composable
private fun StepRow(number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MxBluePrimary.copy(alpha = 0.18f),
            shape = RoundedCornerShape(50),
            modifier = Modifier.size(18.dp)
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MxBluePrimary
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
