package com.example

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.MxBluePrimary
import com.example.ui.theme.MxBlueSecondary

enum class AppLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String,
    val flagEmoji: String,
    val ttsLocale: String
) {
    HINDI("hi", "हिन्दी", "Hindi", "🇮🇳", "hi-IN"),
    ENGLISH("en", "English", "English", "🇺🇸", "en-US"),
    BENGALI("bn", "বাংলা", "Bengali", "🇧🇩", "bn-IN"),
    MARATHI("mr", "मराठी", "Marathi", "🇮🇳", "mr-IN"),
    TELUGU("te", "తెలుగు", "Telugu", "🇮🇳", "te-IN"),
    TAMIL("ta", "தமிழ்", "Tamil", "🇮🇳", "ta-IN"),
    GUJARATI("gu", "ગુજરાતી", "Gujarati", "🇮🇳", "gu-IN"),
    PUNJABI("pa", "ਪੰਜਾਬੀ", "Punjabi", "🇮🇳", "pa-IN"),
    KANNADA("kn", "ಕನ್ನಡ", "Kannada", "🇮🇳", "kn-IN"),
    MALAYALAM("ml", "മലയാളം", "Malayalam", "🇮🇳", "ml-IN"),
    URDU("ur", "اردو", "Urdu", "🇵🇰", "ur-IN");

    val flag: String get() = flagEmoji

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: HINDI
        }
    }
}

object AppStrings {
    fun appTitle(lang: AppLanguage): String = "SRT Video Player"

    fun tabLocal(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "लोकल वीडियो"
        AppLanguage.BENGALI -> "লোকাল"
        AppLanguage.MARATHI -> "लोकल"
        AppLanguage.TELUGU -> "స్థానిక"
        AppLanguage.TAMIL -> "உள்ளூர்"
        AppLanguage.GUJARATI -> "સ્થાનિક"
        AppLanguage.PUNJABI -> "ਲੋਕਲ"
        AppLanguage.KANNADA -> "ಸ್ಥಳೀಯ"
        AppLanguage.MALAYALAM -> "ലോക്കൽ"
        AppLanguage.URDU -> "مقامی"
        else -> "Local"
    }

    fun tabPlayer(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "प्लेयर"
        AppLanguage.BENGALI -> "প্লেয়ার"
        AppLanguage.MARATHI -> "प्लेअर"
        AppLanguage.TELUGU -> "ప్లేయర్"
        AppLanguage.TAMIL -> "பிளேயர்"
        AppLanguage.GUJARATI -> "પ્લેયર"
        AppLanguage.PUNJABI -> "ਪਲੇਅਰ"
        AppLanguage.KANNADA -> "ಪ್ಲೇಯರ್"
        AppLanguage.MALAYALAM -> "പ്ലെയർ"
        AppLanguage.URDU -> "پلیئر"
        else -> "Player"
    }

    fun tabSubtitleTts(lang: AppLanguage): String = when (lang) {
        AppLanguage.HINDI -> "सबटाइटल TTS"
        AppLanguage.BENGALI -> "সাবটাইটেল TTS"
        AppLanguage.MARATHI -> "सबटायटल TTS"
        AppLanguage.TELUGU -> "సబ్‌టైటిల్ TTS"
        AppLanguage.TAMIL -> "துணைத்தலைப்பு TTS"
        AppLanguage.GUJARATI -> "સબટાઈટલ TTS"
        AppLanguage.PUNJABI -> "ਸਬਟਾਈਟਲ TTS"
        AppLanguage.KANNADA -> "ಉಪಶೀರ್ಷಿಕೆ TTS"
        AppLanguage.MALAYALAM -> "സബ്‌ടൈറ്റിൽ TTS"
        AppLanguage.URDU -> "سب ٹائٹل TTS"
        else -> "Subtitle TTS"
    }

    fun languageBarLabel(lang: AppLanguage): String = "Language"
}

@Composable
fun MultipleLanguagesSettingBar(
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    onOpenFullSettings: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("multiple_languages_setting_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = if (isCompact) 4.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MxBluePrimary.copy(alpha = 0.18f),
                modifier = Modifier
                    .clickable { onOpenFullSettings() }
                    .padding(end = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = "Language",
                        tint = MxBlueSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = AppStrings.languageBarLabel(currentLanguage),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppLanguage.entries.forEach { lang ->
                    val isSelected = lang == currentLanguage
                    val chipBackground by animateColorAsState(
                        targetValue = if (isSelected) MxBluePrimary else MaterialTheme.colorScheme.surface,
                        animationSpec = tween(durationMillis = 200),
                        label = "chip_bg"
                    )

                    Surface(
                        onClick = { onLanguageSelected(lang) },
                        shape = RoundedCornerShape(8.dp),
                        color = chipBackground,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) MxBlueSecondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("lang_chip_${lang.code}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = lang.flagEmoji,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = lang.nativeName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = onOpenFullSettings,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("more_languages_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "All Languages",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun MultiLanguageSelectorDialog(
    currentLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredLanguages = remember(searchQuery) {
        if (searchQuery.isBlank()) AppLanguage.entries.toList()
        else {
            AppLanguage.entries.filter {
                it.englishName.contains(searchQuery, ignoreCase = true) ||
                it.nativeName.contains(searchQuery, ignoreCase = true) ||
                it.code.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 580.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MxBluePrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MxBluePrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Language & Dubbing Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Multi-Language & Voice Dubbing Hub",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search language (Hindi, English, Bengali...)", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MxBluePrimary.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, MxBlueSecondary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(currentLanguage.flagEmoji, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Current: ${currentLanguage.nativeName} (${currentLanguage.englishName})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Locale: ${currentLanguage.ttsLocale}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MxBluePrimary
                        ) {
                            Text(
                                text = "ACTIVE",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLanguages, key = { it.code }) { lang ->
                        val isSelected = lang == currentLanguage
                        Surface(
                            onClick = {
                                onSelectLanguage(lang)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MxBluePrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MxBlueSecondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_lang_item_${lang.code}")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(lang.flagEmoji, fontSize = 24.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = lang.nativeName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = if (isSelected) MxBlueSecondary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "(${lang.englishName})",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "TTS Locale: ${lang.ttsLocale}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        onSelectLanguage(lang)
                                        onDismiss()
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MxBlueSecondary,
                                        unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = MxBluePrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
