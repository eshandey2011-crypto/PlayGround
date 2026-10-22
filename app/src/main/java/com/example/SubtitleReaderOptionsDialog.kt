package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.ui.theme.MxBlueSecondary

@Composable
fun SubtitleReaderOptionsDialog(
    uiState: TtsUiState,
    onSelectLanguage: (AppLanguage) -> Unit,
    onToggleOfflineVoice: () -> Unit,
    onToggleContinuous: () -> Unit,
    onDelayChange: (Float) -> Unit,
    onStartReader: () -> Unit,
    onStopReader: () -> Unit,
    onTestVoice: () -> Unit,
    onInstallHindiVoice: () -> Unit,
    onOpenVoiceProfiles: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("subtitle_reader_options_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MxBluePrimary,
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
                    Text(
                        text = "Subtitle Reader Options",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${uiState.parsedSubtitles.size} dialogues • ${uiState.appLanguage.nativeName}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("reader_options_close")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Reader language grid (multi-language)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = MxBluePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reader Language",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                ) {
                    items(AppLanguage.entries.toList(), key = { it.code }) { lang ->
                        val selected = lang == uiState.appLanguage
                        Surface(
                            onClick = { onSelectLanguage(lang) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) MxBluePrimary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(
                                1.dp,
                                if (selected) MxBlueSecondary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.testTag("reader_lang_${lang.code}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = lang.flagEmoji, fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = lang.nativeName,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) MxBlueSecondary
                                    else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                if (selected) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MxBlueSecondary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Hindi (Devanagari) offline voice status + one-tap installer
                Surface(
                    onClick = onInstallHindiVoice,
                    shape = RoundedCornerShape(10.dp),
                    color = if (uiState.isHindiOfflineVoiceAvailable) {
                        Color(0xFF10B981).copy(alpha = 0.12f)
                    } else {
                        Color(0xFFFFB300).copy(alpha = 0.16f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (uiState.isHindiOfflineVoiceAvailable) Color(0xFF10B981)
                        else Color(0xFFFFB300)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reader_hindi_voice_row")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (uiState.isHindiOfflineVoiceAvailable) {
                                Icons.Default.Check
                            } else {
                                Icons.Default.RecordVoiceOver
                            },
                            contentDescription = null,
                            tint = if (uiState.isHindiOfflineVoiceAvailable) Color(0xFF10B981)
                            else Color(0xFFFFB300),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (uiState.isHindiOfflineVoiceAvailable) {
                                    "Hindi offline voice installed"
                                } else {
                                    "Hindi offline voice install karo"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = if (uiState.isHindiOfflineVoiceAvailable) {
                                    "Devanagari klar sunai dega — bina internet"
                                } else {
                                    "Tap karo → Hindi voice data download → wapas aao"
                                },
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!uiState.isHindiOfflineVoiceAvailable) {
                            Text(
                                text = "INSTALL",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB300)
                            )
                        }
                    }
                }

                // Offline / Online voice row
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (uiState.preferOfflineVoice) Icons.Default.CloudOff else Icons.Default.Cloud,
                                contentDescription = null,
                                tint = if (uiState.preferOfflineVoice) Color(0xFF10B981) else Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (uiState.preferOfflineVoice) "Offline Voice (bina internet)" else "Online Voice (best quality)",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = if (uiState.preferOfflineVoice) "Internet band par bhi awaaz" else "Internet chahiye",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = !uiState.preferOfflineVoice,
                            onCheckedChange = { onToggleOfflineVoice() },
                            modifier = Modifier.testTag("reader_offline_switch")
                        )
                    }
                }

                // Continuous reading row
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Lagatar padho (continuous)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = if (uiState.isSubtitleReaderContinuous) "Saare dialogues ek-ek karke" else "Sirf ek dialogue",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.isSubtitleReaderContinuous,
                            onCheckedChange = { onToggleContinuous() },
                            modifier = Modifier.testTag("reader_continuous_switch")
                        )
                    }
                }

                // Gap between dialogues
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Dialogue gap",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${uiState.subtitleReaderDelaySeconds}s",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MxBluePrimary
                        )
                    }
                    Slider(
                        value = uiState.subtitleReaderDelaySeconds,
                        onValueChange = { onDelayChange(it) },
                        valueRange = 0f..5f,
                        steps = 9,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reader_delay_slider")
                    )
                }

                if (uiState.isSubtitleReaderPlaying) {
                    Text(
                        text = "Padh raha hai... dialogue ${uiState.readingSubtitleIndex + 1}/${uiState.parsedSubtitles.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiState.isSubtitleReaderPlaying) {
                    Button(
                        onClick = onStopReader,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("reader_stop_btn")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onStartReader,
                        colors = ButtonDefaults.buttonColors(containerColor = MxBluePrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("reader_start_btn")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Read", fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    onClick = onTestVoice,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("reader_test_voice_btn")
                ) {
                    Text("Test", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onOpenVoiceProfiles,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Voices", fontSize = 12.sp)
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
