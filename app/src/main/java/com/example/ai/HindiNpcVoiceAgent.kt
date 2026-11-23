package com.example.ai

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
import com.example.engine.gl.AiGoal
import com.example.engine.gl.EntityCategory
import com.example.engine.gl.EntityKind
import com.example.engine.gl.TalkableNpcSnapshot
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import java.util.Locale

private const val TAG = "HindiNpcVoiceAgent"

data class NpcChatMessage(
    val senderName: String,
    val textHindi: String,
    val isPlayer: Boolean,
    val decidedActionHindi: String? = null
)

data class NpcAiResponse(
    val replyHindi: String,
    val decidedGoal: AiGoal? = null,
    val friendshipGain: Int = 10,
    val giftCoins: Int = 0
)

/**
 * Real Firebase AI Logic (Gemini 3.8 Flash) + Human-Sounding Hindi Voice (Android TTS `hi-IN`)
 * + Live Hindi Microphone Speech Recognition (`SpeechRecognizer` `hi-IN`) for autonomous 3D NPCs.
 */
class HindiNpcVoiceAgent(private val context: Context) {

    // Lazy initialization ensures App Check runs in MainActivity.onCreate() before Firebase.ai is touched.
    private val model by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _voiceStatus = MutableStateFlow("Hindi Voice Ready")
    val voiceStatus: StateFlow<String> = _voiceStatus.asStateFlow()

    init {
        initHindiTts()
    }

    private fun initHindiTts() {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val hindiLocale = Locale.forLanguageTag("hi-IN")
                    val langRes = tts?.setLanguage(hindiLocale)
                    if (langRes != TextToSpeech.LANG_MISSING_DATA && langRes != TextToSpeech.LANG_NOT_SUPPORTED) {
                        // Select the most natural/high-quality Hindi neural voice available on the device
                        try {
                            val bestHindiVoice = tts?.voices
                                ?.filter { it.locale.language == "hi" }
                                ?.sortedByDescending { v ->
                                    val name = v.name.lowercase()
                                    val qualityScore = v.quality
                                    val neuralBonus = if ("network" in name || "wavenet" in name || "neural" in name) 200 else 0
                                    qualityScore + neuralBonus
                                }
                                ?.firstOrNull()
                            if (bestHindiVoice != null) {
                                tts?.voice = bestHindiVoice
                            }
                        } catch (_: Throwable) {
                            // Keep default hi-IN voice
                        }
                        ttsReady = true
                        _voiceStatus.value = "Hindi Voice Ready (hi-IN)"
                    } else {
                        ttsReady = true
                        _voiceStatus.value = "Voice Ready (System Default)"
                    }
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }
                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                        }
                    })
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "TTS unavailable in current environment: ${e.message}")
        }
    }

    /**
     * Speaks natural Hindi dialogue aloud with character-specific vocal pitch & natural cadence.
     */
    fun speakHindi(text: String, kind: EntityKind, customPitch: Float? = null) {
        if (!ttsReady || text.isBlank()) return
        val cleanText = text
            .replace(Regex("\\[ACTION:.*?\\]"), "")
            .replace("*", "")
            .trim()
        if (cleanText.isBlank()) return

        try {
            val (basePitch, rate) = when (kind) {
                EntityKind.RAJA, EntityKind.ELEPHANT -> 0.84f to 0.92f
                EntityKind.HANUMAN, EntityKind.BHEEM, EntityKind.BULL, EntityKind.BUFFALO -> 0.87f to 0.95f
                EntityKind.PUJARI -> 0.95f to 0.90f
                EntityKind.RAJU, EntityKind.CAT, EntityKind.BIRD_SPARROW, EntityKind.BIRD_MYNA -> 1.22f to 1.05f
                EntityKind.DOG -> 1.12f to 1.04f
                EntityKind.COW, EntityKind.HORSE -> 0.96f to 0.94f
                EntityKind.KRISHNA -> 1.02f to 0.94f
                else -> 1.0f to 0.96f
            }
            tts?.setPitch((customPitch ?: basePitch).coerceIn(0.65f, 1.5f))
            tts?.setSpeechRate(rate)
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, "npc_${System.currentTimeMillis()}")
        } catch (e: Throwable) {
            Log.w(TAG, "TTS speak failed: ${e.message}")
        }
    }

    /**
     * Generates a lively, behavior-authentic Hindi greeting when the player walks right in front of
     * ANY Animal, Society Character, Hero, or Admin Custom 3D Character.
     */
    fun buildProximityBehaviorGreeting(snapshot: TalkableNpcSnapshot): String {
        if (!snapshot.customGreeting.isNullOrBlank()) {
            return snapshot.customGreeting
        }
        return when (snapshot.kind) {
            EntityKind.COW -> "अम्बा... नमस्ते मित्र! मैं गौ माता हूँ, यहाँ हरी घास खा रही हूँ। आप चाहें तो साथ चल सकती हूँ!"
            EntityKind.BUFFALO -> "म्म्म्म्... नमस्ते दोस्त! मुझे नदी का ठंडा पानी बहुत पसंद है।"
            EntityKind.BULL -> "हूँ... मैं मज़बूत बैल हूँ! खेत और रास्ते में आपकी मदद के लिए तैयार हूँ।"
            EntityKind.DOG -> "भौं भौं! नमस्ते मेरे प्यारे दोस्त! मैं आपका वफ़ादार कुत्ता हूँ, बोलिए कहाँ चलें?"
            EntityKind.CAT -> "म्याऊं म्याऊं! आप मेरे पास आए, मुझे बहुत अच्छा लगा!"
            EntityKind.HORSE -> "हिनहिन! नमस्ते घुड़सवार मित्र! मैं तेज़ दौड़ने और आपके साथ घूमने के लिए तैयार हूँ।"
            EntityKind.ELEPHANT -> "पाँव-पाँव! मैं शाही हाथी हूँ! आप कहें तो अपनी सूंड से यहाँ पानी ले आऊँ या साथ चलूँ?"
            EntityKind.BIRD_SPARROW, EntityKind.BIRD_MYNA -> "टें-टें! नमस्ते दोस्त! आसमान से पूरी दुनिया बहुत सुंदर दिख रही है!"
            EntityKind.RAJA -> "प्रणाम मित्र! मैं राजा हूँ। मेरे राज्य में आपका स्वागत है—बोलिए महल, घर, पानी या पेड़ क्या बनवाना है?"
            EntityKind.PUJARI -> "हरि ओम! मैं मंदिर का पुजारी हूँ। आइए, पूजा करें या एक नया पावन मंदिर बनाएं!"
            EntityKind.MAJDUR -> "नमस्ते साहब! मैं मज़दूर हूँ—कहिए तो अभी आपके सामने पक्का घर बना दूँ, नहर खोदकर पानी ले आऊँ या पेड़ लगा दूँ!"
            EntityKind.AMIR_SETH -> "आदाब और नमस्ते! मैं अमीर सेठ हूँ—शाही बाज़ार में नए 3D मॉडल और खज़ाना ज़रूर देखें!"
            EntityKind.GARIB_KISAN -> "राम-राम भैया! मैं किसान हूँ। आप कहें तो खेत के लिए पानी ले आऊँ और हरे पेड़ लगा दूँ!"
            EntityKind.KRISHNA -> "राधे-राधे मित्र! निडर होकर आगे बढ़ो, मैं श्री कृष्ण तुम्हारे साथ हूँ। बोलो क्या कार्य करें?"
            EntityKind.RAM -> "जय सिया राम मित्र! धर्म और मित्रता के पथ पर मैं सदा तुम्हारे साथ हूँ।"
            EntityKind.HANUMAN -> "जय श्री राम! मैं बजरंगबली हूँ—कोई भी दुश्मन आए या बड़ा काम हो, बस आदेश दो!"
            EntityKind.BHEEM -> "नमस्ते दोस्त! मैं भीम हूँ, अपनी गदा और ताकत से घर, नहर या दुश्मनों का सामना—सब कर दूँगा!"
            EntityKind.RAJU -> "अरे दोस्त! मैं राजू हूँ! चलो मिलकर घर बनाएं, पानी लाएं या पेड़ लगाएं!"
            else -> "नमस्ते मित्र! मैं ${snapshot.displayName} हूँ। माइक दबाकर बोलिए—घर बनाओ, पानी लाओ या पेड़ लगाओ!"
        }
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
            _isSpeaking.value = false
        } catch (_: Throwable) {
        }
    }

    /**
     * Starts real-time Hindi microphone speech recognition (`hi-IN`) on the Main thread.
     */
    fun startHindiVoiceListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        mainHandler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    onError("इस डिवाइस पर Voice Recognition उपलब्ध नहीं है। कृपया नीचे हिंदी में टाइप करें।")
                    return@post
                }
                stopSpeaking()
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            _isListening.value = true
                            _voiceStatus.value = "सुन रहा हूँ... हिंदी में बोलिए"
                        }
                        override fun onBeginningOfSpeech() = Unit
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() {
                            _isListening.value = false
                            _voiceStatus.value = "समझ रहा हूँ..."
                        }
                        override fun onError(error: Int) {
                            _isListening.value = false
                            _voiceStatus.value = "Hindi Voice Ready"
                            val msg = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH -> "आवाज़ साफ़ सुनाई नहीं दी, फिर से बोलिए।"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "माइक की अनुमति (Microphone permission) दें।"
                                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "वॉइस के लिए इंटरनेट कनेक्शन जांचें।"
                                else -> "वॉइस इनपुट रुक गया, कृपया फिर से प्रयास करें।"
                            }
                            onError(msg)
                        }
                        override fun onResults(results: Bundle?) {
                            _isListening.value = false
                            _voiceStatus.value = "Hindi Voice Ready"
                            val spoken = results
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                ?.trim()
                            if (!spoken.isNullOrBlank()) {
                                onResult(spoken)
                            } else {
                                onError("कुछ सुनाई नहीं दिया, कृपया फिर से बोलें।")
                            }
                        }
                        override fun onPartialResults(partialResults: Bundle?) {
                            val partial = partialResults
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                            if (!partial.isNullOrBlank()) {
                                _voiceStatus.value = "आप बोल रहे हैं: $partial"
                            }
                        }
                        override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    })
                }
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                _isListening.value = false
                Log.e(TAG, "Failed to start Hindi SpeechRecognizer", e)
                onError(e.localizedMessage ?: "माइक शुरू नहीं हो सका।")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                _isListening.value = false
                _voiceStatus.value = "Hindi Voice Ready"
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Generates an authentic, independent-thinking Hindi conversation reply from the NPC
     * using Firebase AI Logic (`gemini-3.8-flash`) when Firebase is configured, or executes
     * direct Hindi voice/text world commands cleanly when running in offline/no-Firebase mode.
     */
    suspend fun chatWithNpc(
        kind: EntityKind,
        currentGoal: String,
        friendshipLevel: Int,
        history: List<NpcChatMessage>,
        playerMessage: String
    ): Result<NpcAiResponse> {
        val firebaseReady = try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Throwable) {
            false
        }

        if (!firebaseReady) {
            val lower = playerMessage.lowercase()
            val isAnimal = kind.category == EntityCategory.ANIMALS
            val (goal, coins, reply) = when {
                lower.contains("पानी") || lower.contains("pani") || lower.contains("water") || lower.contains("नदी") || lower.contains("तालाब") || lower.contains("जल") -> Triple(
                    AiGoal.BRING_WATER,
                    0,
                    if (isAnimal) {
                        "बिल्कुल दोस्त! मैं अभी रास्ता बनाकर और झरना खोदकर यहाँ ताज़ा बहता पानी ले आता हूँ!"
                    } else {
                        "जी बिल्कुल! मैंने अभी नहर खोदकर आपके सामने ताज़ा बहता पानी ला दिया है!"
                    }
                )
                lower.contains("पेड़") || lower.contains("ped") || lower.contains("tree") || lower.contains("जंगल") || lower.contains("पौधे") || lower.contains("बगीचा") -> Triple(
                    AiGoal.PLANT_TREES,
                    0,
                    "बहुत बढ़िया विचार! मैं अभी आपके चारों ओर हरे-भरे पेड़ और सुंदर बगीचा लगा रहा हूँ!"
                )
                lower.contains("मंदिर") || lower.contains("mandir") || lower.contains("temple") -> Triple(
                    AiGoal.BUILD_MANDIR,
                    0,
                    "जी मित्र! मैं अभी आपके सामने एक सुंदर पावन मंदिर बना देता हूँ।"
                )
                lower.contains("घर") || lower.contains("ghar") || lower.contains("house") || lower.contains("मकान") || lower.contains("महल") -> Triple(
                    AiGoal.BUILD_HOUSE,
                    0,
                    "बिल्कुल दोस्त! मैं अभी आपके लिए एक मज़बूत पक्का घर बना रहा हूँ।"
                )
                lower.contains("पूजा") || lower.contains("puja") || lower.contains("आरती") || lower.contains("भजन") -> Triple(
                    AiGoal.DO_PUJA,
                    0,
                    "जय श्री राम! मैं अभी मंदिर में पूजा और घंटी आरती शुरू कर रहा हूँ।"
                )
                lower.contains("साथ") || lower.contains("चलो") || lower.contains("दोस्त") || lower.contains("friend") || lower.contains("follow") -> Triple(
                    AiGoal.FOLLOW_FRIEND,
                    15,
                    if (kind == EntityKind.DOG) "भौं भौं! मैं आपका सबसे पक्का दोस्त हूँ, चलिए आपके साथ चलता हूँ!"
                    else "आप मेरे पक्के दोस्त हैं! चलिए, मैं आपके साथ-साथ चलता हूँ।"
                )
                lower.contains("सिक्के") || lower.contains("पैसा") || lower.contains("coin") || lower.contains("इनाम") -> Triple(
                    null,
                    25,
                    "यह लीजिए मित्र, मेरी तरफ से पच्चीस सोने के सिक्के! शाही दुकान में काम आएंगे।"
                )
                else -> Triple(
                    null,
                    0,
                    if (isAnimal) {
                        buildProximityBehaviorGreeting(
                            TalkableNpcSnapshot(
                                id = 0,
                                kind = kind,
                                displayName = kind.displayName,
                                goalLabelHindi = currentGoal,
                                friendshipLevel = friendshipLevel,
                                isFriend = true,
                                distanceBlocks = 2,
                                isAnimal = true
                            )
                        )
                    } else {
                        "नमस्ते मित्र! मैं ${kind.displayName} हूँ। आप मुझसे 'घर बनाओ', 'पानी यहाँ ले आओ', 'पेड़ लगाओ', 'मंदिर बनाओ' या 'साथ चलो' कह सकते हैं!"
                    }
                )
            }
            return Result.success(
                NpcAiResponse(
                    replyHindi = reply,
                    decidedGoal = goal,
                    friendshipGain = if (goal != null) 18 else 10,
                    giftCoins = coins
                )
            )
        }

        val recentHistory = history.takeLast(6).joinToString("\n") {
            "${if (it.isPlayer) "खिलाड़ी" else kind.displayName}: ${it.textHindi}"
        }
        val prompt = """
            तुम BlockForge 3D वॉक्सेल दुनिया के एक जीवित, स्वतंत्र सोचने वाले किरदार हो।
            तुम्हारा नाम और भूमिका: ${kind.displayName} — ${kind.roleSummaryHindi}
            तुम्हारा वर्तमान काम: $currentGoal
            खिलाड़ी से तुम्हारी दोस्ती का स्तर: $friendshipLevel / 100

            नियम:
            1. हमेशा शुद्ध, प्राकृतिक, आत्मीय और बोलचाल की हिंदी (देवनागरी लिपि) में 1 से 3 छोटे वाक्यों में जवाब दो ताकि सुनने में बिल्कुल असली इंसान या प्यारे जानवर की तरह लगे।
            2. तुम अपने मन से निर्णय ले सकते हो — यदि खिलाड़ी घर बनवाना चाहे, मंदिर बनवाना चाहे, पानी यहाँ मंगवाना चाहे, पेड़ लगवाना चाहे, पूजा करवाना चाहे, दोस्ती या साथ चलना चाहे, तो जवाब के बिल्कुल अंत में इनमें से कोई एक टैग जोड़ो:
               [ACTION:BUILD_HOUSE] या [ACTION:BUILD_MANDIR] या [ACTION:BRING_WATER] या [ACTION:PLANT_TREES] या [ACTION:DO_PUJA] या [ACTION:FOLLOW_FRIEND] या [ACTION:GIFT_COINS] या [ACTION:NONE]
            3. कोई मार्कडाउन (जैसे ** या #) या इमोजी मत लिखो क्योंकि यह आवाज़ में बोला जाएगा।

            पिछली बातचीत:
            $recentHistory

            खिलाड़ी ने अभी कहा: "$playerMessage"
            अब ${kind.displayName} के रूप में हिंदी में स्वाभाविक जवाब दो:
        """.trimIndent()

        return try {
            val response = withTimeout(45_000L) {
                model.generateContent(prompt)
            }
            val rawText = response.text?.trim()
            if (rawText.isNullOrBlank()) {
                Result.failure(IllegalStateException("AI ने कोई उत्तर नहीं दिया। कृपया फिर प्रयास करें।"))
            } else {
                val parsedGoal = when {
                    rawText.contains("[ACTION:BUILD_HOUSE]") -> AiGoal.BUILD_HOUSE
                    rawText.contains("[ACTION:BUILD_MANDIR]") -> AiGoal.BUILD_MANDIR
                    rawText.contains("[ACTION:BRING_WATER]") -> AiGoal.BRING_WATER
                    rawText.contains("[ACTION:PLANT_TREES]") -> AiGoal.PLANT_TREES
                    rawText.contains("[ACTION:DO_PUJA]") -> AiGoal.DO_PUJA
                    rawText.contains("[ACTION:FOLLOW_FRIEND]") -> AiGoal.FOLLOW_FRIEND
                    else -> null
                }
                val giftCoins = if (rawText.contains("[ACTION:GIFT_COINS]")) 25 else 0
                val cleanReply = rawText
                    .replace(Regex("\\[ACTION:[A-Z_]+\\]"), "")
                    .replace("**", "")
                    .trim()
                Result.success(
                    NpcAiResponse(
                        replyHindi = cleanReply,
                        decidedGoal = parsedGoal,
                        friendshipGain = if (parsedGoal != null) 18 else 12,
                        giftCoins = giftCoins
                    )
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini Hindi chat timed out", e)
            Result.failure(IllegalStateException("AI जवाब देने में समय लग रहा है। कृपया फिर से प्रयास करें।"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI Logic chat failed", e)
            Result.failure(IllegalStateException("AI नेटवर्क से संपर्क नहीं हो सका। इंटरनेट जांचें या नीचे दिए बटन इस्तेमाल करें।"))
        }
    }

    fun release() {
        stopSpeaking()
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (_: Throwable) {
            }
        }
        try {
            tts?.shutdown()
            tts = null
        } catch (_: Throwable) {
        }
    }
}
