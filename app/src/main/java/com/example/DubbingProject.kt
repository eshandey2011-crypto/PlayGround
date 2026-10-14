package com.example

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity representing a saved Dubbing Project (SRT + Video + Voice settings).
 */
@Entity(tableName = "dubbing_projects")
data class DubbingProject(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val videoUri: String? = null,
    val videoTitle: String = "Untitled Video",
    val rawSrtText: String = "",
    val subtitleCount: Int = 0,
    val languageCode: String = "hi",
    val activeAudioTrack: String = AudioTrackMode.HINDI_TTS_DUB.name,
    val audioPresetMode: String = AudioPresetMode.PURE_HINDI_ONLY.name,
    val isBgmEnabled: Boolean = false,
    val bgmVolume: Float = 0.35f,
    val bgmStyle: String = BgmStyle.CINEMATIC_AMBIENT.name,
    val subtitleFontSize: Int = 18,
    val subtitleTheme: String = SubtitleTheme.MX_YELLOW.name,
    val subtitleBackgroundOpacity: Float = 0.75f,
    val subtitleAlignment: String = SubtitleAlignment.CENTER.name,
    val speedProgress: Int = 50,
    val pitchProgress: Int = 50,
    val selectedVoiceName: String? = null,
    val speakerAssignmentsJson: String = "{}",
    val lastPlaybackPositionMs: Long = 0L,
    val bgmMusicUri: String? = null,
    val bgmMusicName: String? = null,
    val bgmDuckingRatio: Float = 0.30f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
