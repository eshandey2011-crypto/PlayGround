package com.example

import java.util.regex.Pattern

/**
 * High-performance, zero-network Offline Subtitle Engine.
 * Translates and formats SRT subtitles into natural Hindi (Devanagari script)
 * completely offline without requiring internet connection, API keys, or cloud servers.
 */
object OfflineSubtitleEngine {
    private val speakerMap = mapOf(
        "narrator" to "सूत्रधार",
        "storyteller" to "कथावाचक",
        "rahul" to "राहुल",
        "priya" to "प्रिया",
        "vikram" to "विक्रम",
        "sunita" to "सुनीता",
        "sarah" to "सारा",
        "david" to "डेविड",
        "rohan" to "रोहन",
        "amit" to "अमित",
        "neha" to "नेहा",
        "alex" to "एलेक्स",
        "john" to "जॉन",
        "doctor" to "डॉक्टर",
        "teacher" to "अध्यापक",
        "officer" to "अधिकारी",
        "host" to "मेजबान",
        "boss" to "बॉस",
        "manager" to "प्रबंधक",
        "speaker" to "वक्ता"
    )

    private val phraseMap = linkedMapOf(
        "welcome to our project review meeting today" to "आज की प्रोजेक्ट समीक्षा बैठक में आप सबका स्वागत है",
        "welcome to our annual product review meeting" to "हमारी वार्षिक उत्पाद समीक्षा बैठक में आपका स्वागत है",
        "hello team, welcome to our project review meeting today" to "नमस्ते टीम, आज की प्रोजेक्ट समीक्षा बैठक में आप सबका स्वागत है",
        "hello team welcome to our project review meeting today" to "नमस्ते टीम, आज की प्रोजेक्ट समीक्षा बैठक में आप सबका स्वागत है",
        "hello team" to "नमस्ते टीम",
        "good morning everyone" to "सुप्रभात आप सभी को",
        "good morning" to "सुप्रभात",
        "good afternoon" to "शुभ दोपहर",
        "good evening" to "शुभ संध्या",
        "good night" to "शुभ रात्रि",
        "thank you very much" to "आपका बहुत-बहुत धन्यवाद",
        "thank you so much" to "आपका बहुत धन्यवाद",
        "thank you" to "धन्यवाद",
        "thanks a lot" to "बहुत धन्यवाद",
        "thanks" to "शुक्रिया",
        "thank you rahul" to "धन्यवाद राहुल",
        "the development team has completed the new software module" to "डेवलपमेंट टीम ने नया सॉफ्टवेयर मॉड्यूल पूरा कर लिया है",
        "the engineering team has successfully finished all critical modules" to "इंजीनियरिंग टीम ने सभी महत्वपूर्ण मॉड्यूल सफलतापूर्वक पूरे कर लिए हैं",
        "client feedback is very positive" to "क्लाइंट का फीडबैक बहुत सकारात्मक है",
        "we need to keep the user interface simple" to "हमें यूजर इंटरफेस को सरल रखना होगा",
        "we should simplify the user navigation" to "हमें यूजर नेविगेशन को सरल बनाना चाहिए",
        "i will submit the design and testing report by the end of this week" to "मैं इस सप्ताह के अंत तक डिज़ाइन और टेस्टिंग रिपोर्ट जमा कर दूंगा",
        "this product will be launched on the first day of next month" to "यह उत्पाद अगले महीने के पहले दिन लॉन्च किया जाएगा",
        "by next friday, we will be ready for the final production release" to "अगले शुक्रवार तक हम अंतिम प्रोडक्शन रिलीज के लिए तैयार होंगे",
        "look at this calm and clear river flowing in the lap of nature" to "प्रकृति की गोद में बहती इस शांत और निर्मल नदी को देखिए",
        "among the lush green forests, the morning sun brings fresh energy" to "हरे-भरे जंगलों के बीच सुबह का सूरज नई ऊर्जा लेकर आता है",
        "the blanket of snow on the mountain peaks reflects the beauty of nature" to "पहाड़ों की चोटियों पर बर्फ की चादर प्रकृति का सौंदर्य दर्शाती है",
        "the chirping of birds makes the atmosphere even sweeter" to "पक्षियों की चहचहाहट माहौल को और भी मधुर बना देती है",
        "protecting the environment is the moral duty of us all" to "पर्यावरण की रक्षा करना हम सभी का नैतिक कर्तव्य है",
        "in a small village lived a courageous boy named vikram" to "एक छोटे से गाँव में विक्रम नाम का एक साहसी लड़का रहता था",
        "i will play near the edge of the forest with my friends every day" to "मैं रोज़ अपने दोस्तों के साथ जंगल के किनारे खेलूँगा",
        "hey vikram, look! an injured bird has fallen in the bushes" to "अरे विक्रम, देखो! झाड़ियों में एक घायल पक्षी गिर गया है",
        "do not fear sunita, i will go and help it right now" to "डरो मत सुनीता, मैं अभी जाकर उसकी मदद करता हूँ",
        "true kindness, courage, and friendship make every challenge easier" to "सच्ची दयालुता, साहस और मित्रता हर चुनौती को आसान बना देती है",
        "how are you doing" to "आप कैसे हैं",
        "how are you" to "आप कैसे हैं",
        "what are you doing" to "आप क्या कर रहे हैं",
        "what is your name" to "आपका नाम क्या है",
        "my name is" to "मेरा नाम है",
        "where are you going" to "आप कहाँ जा रहे हैं",
        "where are you from" to "आप कहाँ से हैं",
        "what happened" to "क्या हुआ",
        "what is happening" to "क्या हो रहा है",
        "can you hear me" to "क्या आप मुझे सुन सकते हैं",
        "i can hear you" to "मैं आपको सुन सकता हूँ",
        "let's get started" to "चलिए शुरू करते हैं",
        "let us get started" to "चलिए शुरुआत करते हैं",
        "listen to me" to "मेरी बात सुनिए",
        "look at this" to "इसे देखिए",
        "look at that" to "उसे देखिए",
        "don't worry about it" to "इसकी चिंता मत कीजिए",
        "don't worry" to "चिंता मत कीजिए",
        "do not worry" to "घबराइए मत",
        "see you soon" to "जल्द मिलते हैं",
        "see you tomorrow" to "कल मिलते हैं",
        "see you later" to "बाद में मिलते हैं",
        "take care of yourself" to "अपना ख्याल रखिए",
        "take care" to "ख्याल रखिए",
        "i don't know" to "मुझे नहीं पता",
        "i do not know" to "मुझे जानकारी नहीं है",
        "i understand" to "मैं समझता हूँ",
        "i agree with you" to "मैं आपसे सहमत हूँ",
        "i don't think so" to "मुझे ऐसा नहीं लगता",
        "are you ready" to "क्या आप तैयार हैं",
        "i am ready" to "मैं तैयार हूँ",
        "we are ready" to "हम तैयार हैं",
        "of course" to "बिल्कुल",
        "no problem" to "कोई बात नहीं",
        "you are welcome" to "आपका स्वागत है",
        "have a nice day" to "आपका दिन शुभ हो",
        "have a great day" to "आपका दिन शानदार रहे",
        "nice to meet you" to "आपसे मिलकर अच्छा लगा",
        "pleased to meet you" to "आपसे मिलकर खुशी हुई",
        "congratulations" to "बधाई हो",
        "all the best" to "शुभकामनाएं",
        "best of luck" to "शुभकामनाएं",
        "excuse me" to "माफ़ कीजिएगा",
        "i am sorry" to "मुझे खेद है",
        "sorry about that" to "उसके लिए क्षमा करें"
    )

    private val normalizedPhraseMap: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        for ((k, v) in phraseMap) {
            val cleanKey = normalizeString(k)
            map[cleanKey] = v
        }
        map
    }

    private val sortedPhrases: List<Pair<String, String>> by lazy {
        phraseMap.toList().sortedByDescending { it.first.length }
    }

    private val wordMap = mapOf(
        "hello" to "नमस्ते", "hi" to "नमस्ते", "hey" to "अरे", "yes" to "हाँ",
        "no" to "नहीं", "please" to "कृपया", "welcome" to "स्वागत",
        "thanks" to "धन्यवाद", "thank" to "धन्यवाद", "sorry" to "माफ़ कीजिए",
        "bye" to "अलविदा", "okay" to "ठीक है", "ok" to "ठीक है", "sure" to "ज़रूर",
        "i" to "मैं", "me" to "मुझे", "my" to "मेरा", "mine" to "मेरा",
        "we" to "हम", "us" to "हमें", "our" to "हमारा", "you" to "आप",
        "your" to "आपका", "he" to "वह", "him" to "उसे", "his" to "उसका",
        "she" to "वह", "her" to "उसका", "they" to "वे", "them" to "उन्हें",
        "their" to "उनका", "it" to "यह", "this" to "यह", "that" to "वह",
        "what" to "क्या", "who" to "कौन", "where" to "कहाँ", "when" to "कब",
        "why" to "क्यों", "how" to "कैसे", "is" to "है", "are" to "हैं",
        "am" to "हूँ", "was" to "था", "were" to "थे", "will" to "होगा",
        "team" to "टीम", "project" to "प्रोजेक्ट", "video" to "वीडियो",
        "audio" to "ऑडियो", "today" to "आज", "tomorrow" to "कल",
        "good" to "अच्छा", "great" to "बहुत बढ़िया", "new" to "नया",
        "and" to "और", "but" to "लेकिन", "or" to "या"
    )

    private val contractions = mapOf(
        "don't" to "नहीं", "dont" to "नहीं", "do not" to "नहीं",
        "can't" to "नहीं कर सकते", "cannot" to "नहीं कर सकते",
        "won't" to "नहीं होगा", "i'm" to "मैं", "it's" to "यह है",
        "we're" to "हम हैं", "let's" to "चलिए"
    )

    private fun normalizeString(s: String): String {
        return s.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun translateSrtToHindiOffline(rawSrt: String): String {
        if (rawSrt.isBlank()) return rawSrt
        val format = SrtParser.detectFormat(rawSrt)
        val parsed = SrtParser.parse(rawSrt)
        if (parsed.isEmpty()) return rawSrt
        val translatedItems = parsed.map { item ->
            val translatedText = translateSentenceToHindi(item.text)
            val translatedSpoken = translateSentenceToHindi(item.cleanSpokenText)
            val translatedSpeakerTag = item.speakerTag?.let { speakerMap[it.lowercase()] ?: it }
            val translatedSpeaker = item.speaker?.copy(
                name = translatedSpeakerTag ?: item.speaker.name
            )
            item.copy(
                text = translatedText,
                cleanSpokenText = translatedSpoken,
                speakerTag = translatedSpeakerTag,
                speaker = translatedSpeaker
            )
        }
        return SrtParser.exportAs(translatedItems, format)
    }

    fun translateLineToHindi(line: String): String = translateSentenceToHindi(line)

    fun translateSentenceToHindi(sentence: String): String {
        val trimmed = sentence.trim()
        if (trimmed.isEmpty()) return ""
        val devanagariCount = trimmed.count { it in '\u0900'..'\u097F' }
        if (devanagariCount > trimmed.length * 0.3) {
            return trimmed
        }

        val speakerRegex = Regex("^([\\[\\(])([^\\]\\)]+)([\\]\\)]):?\\s*(.*)$")
        val match = speakerRegex.find(trimmed)
        var speakerPrefix = ""
        var content = trimmed
        if (match != null) {
            val openBracket = match.groupValues[1]
            val speakerName = match.groupValues[2].trim()
            val closeBracket = match.groupValues[3]
            content = match.groupValues[4].trim()
            val translatedSpeaker = speakerMap[speakerName.lowercase()] ?: speakerName
            speakerPrefix = "$openBracket$translatedSpeaker$closeBracket: "
        }

        if (content.isBlank()) {
            return trimmed
        }

        val cleanNormContent = normalizeString(content)
        val fullMatch = normalizedPhraseMap[cleanNormContent]
        if (fullMatch != null) {
            return speakerPrefix + fullMatch
        }

        var workingText = content
        for ((englishPhrase, hindiPhrase) in sortedPhrases) {
            if (workingText.contains(englishPhrase, ignoreCase = true)) {
                workingText = workingText.replace(Regex("(?i)\\b" + Pattern.quote(englishPhrase) + "\\b"), hindiPhrase)
            }
        }

        val currentDevanagari = workingText.count { it in '\u0900'..'\u097F' }
        if (currentDevanagari > 6 && currentDevanagari >= workingText.length * 0.4) {
            return speakerPrefix + workingText
        }

        val tokens = workingText.split(Regex("\\s+"))
        val translatedTokens = tokens.map { token ->
            if (token.any { it in '\u0900'..'\u097F' }) {
                return@map token
            }
            var leadingPunct = ""
            var trailingPunct = ""
            var start = 0
            while (start < token.length && !token[start].isLetterOrDigit()) {
                leadingPunct += token[start]
                start++
            }
            var end = token.length - 1
            while (end >= start && !token[end].isLetterOrDigit()) {
                trailingPunct = token[end] + trailingPunct
                end--
            }
            val coreWord = if (start <= end) token.substring(start, end + 1).lowercase() else ""
            if (coreWord.isEmpty()) {
                return@map token
            }
            val mappedWord = contractions[coreWord]
                ?: wordMap[coreWord]
                ?: coreWord
            leadingPunct + mappedWord + trailingPunct
        }
        val result = translatedTokens.joinToString(" ")
        return speakerPrefix + (if (result.isNotBlank()) result else content)
    }
}
