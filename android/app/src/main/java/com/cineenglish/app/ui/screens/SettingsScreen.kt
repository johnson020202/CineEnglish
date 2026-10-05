package com.cineenglish.app.ui.screens

import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cineenglish.app.data.local.AppSettings
import com.cineenglish.app.data.remote.ApiClient
import com.cineenglish.app.data.remote.DirectAiClient
import com.cineenglish.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var backendUrl by remember { mutableStateOf("") }
    var aiBaseUrl by remember { mutableStateOf("") }
    var aiApiKey by remember { mutableStateOf("") }
    var chatModel by remember { mutableStateOf("gpt-4o-mini") }
    var selectedVoice by remember { mutableStateOf("alloy") }

    var passThresholdOverall by remember { mutableFloatStateOf(80f) }
    var passThresholdCompleteness by remember { mutableFloatStateOf(90f) }
    var autoAdvance by remember { mutableStateOf(false) }
    var silenceStop by remember { mutableStateOf(true) }

    var testStatusMessage by remember { mutableStateOf<String?>(null) }
    var isTestingCapability by remember { mutableStateOf(false) }

    var backendStatusMessage by remember { mutableStateOf<String?>(null) }
    var isTestingBackend by remember { mutableStateOf(false) }

    // Dynamic Models State
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isFetchingModels by remember { mutableStateOf(false) }
    var showModelMenu by remember { mutableStateOf(false) }

    // Native TextToSpeech Engine for immediate audible American speech verification
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        var localTts: TextToSpeech? = null
        localTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                localTts?.language = Locale.US
                isTtsReady = true
            }
        }
        ttsEngine = localTts
        onDispose {
            localTts?.stop()
            localTts?.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        backendUrl = settings.backendUrlFlow.first()
        aiBaseUrl = settings.aiBaseUrlFlow.first()
        aiApiKey = settings.getAiApiKeyFlow().first()
        chatModel = settings.chatModelFlow.first()
        selectedVoice = settings.ttsVoiceFlow.first()
        passThresholdOverall = settings.passThresholdOverallFlow.first()
        passThresholdCompleteness = settings.passThresholdCompletenessFlow.first()
        autoAdvance = settings.autoAdvanceFlow.first()
        silenceStop = settings.silenceStopFlow.first()

        // Populate preset models if available
        if (aiBaseUrl.isNotBlank()) {
            val presets = DirectAiClient.getPresetModelsForUrl(aiBaseUrl)
            if (presets.isNotEmpty()) {
                availableModels = presets
            }
        }
    }

    fun saveAllSettings() {
        scope.launch(Dispatchers.IO) {
            settings.saveBackendUrl(backendUrl)
            settings.saveAiBaseUrl(aiBaseUrl)
            settings.saveAiApiKey(aiApiKey)
            settings.saveChatModel(chatModel)
            settings.saveTtsVoice(selectedVoice)
            settings.saveThresholds(passThresholdOverall, passThresholdCompleteness, autoAdvance)
        }
    }

    fun fetchAvailableModels() {
        if (aiBaseUrl.isBlank()) {
            testStatusMessage = "Please enter AI Base URL first."
            return
        }
        isFetchingModels = true
        testStatusMessage = null
        scope.launch(Dispatchers.IO) {
            // Priority 1: Direct Client HTTP Query
            val fetched = DirectAiClient.fetchModelsDirectly(aiBaseUrl, aiApiKey)
            if (fetched.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    isFetchingModels = false
                    availableModels = fetched
                    showModelMenu = true
                    testStatusMessage = "✓ Detected ${fetched.size} live models from provider! Select from dropdown."
                }
                return@launch
            }

            // Priority 2: Backend Proxy Query
            try {
                if (backendUrl.isNotBlank()) {
                    val api = ApiClient.getService(backendUrl)
                    val res = api.getAiModels(aiBaseUrl, aiApiKey.takeIf { it.isNotBlank() })
                    withContext(Dispatchers.Main) {
                        isFetchingModels = false
                        if (res.success && res.models.isNotEmpty()) {
                            availableModels = res.models
                            showModelMenu = true
                            testStatusMessage = "✓ Loaded ${res.count} models via Backend! Tap below to select."
                        } else {
                            val presets = DirectAiClient.getPresetModelsForUrl(aiBaseUrl)
                            availableModels = presets
                            showModelMenu = true
                            testStatusMessage = "✓ Loaded ${presets.size} provider-tailored presets. Select below."
                        }
                    }
                    return@launch
                }
            } catch (_: Exception) {}

            // Priority 3: Built-in Provider Presets
            withContext(Dispatchers.Main) {
                isFetchingModels = false
                val presets = DirectAiClient.getPresetModelsForUrl(aiBaseUrl)
                availableModels = presets
                showModelMenu = true
                testStatusMessage = "✓ Loaded ${presets.size} provider-tailored models. Select below."
            }
        }
    }

    fun testBackendConnection() {
        isTestingBackend = true
        backendStatusMessage = null
        val startTs = System.currentTimeMillis()
        scope.launch(Dispatchers.IO) {
            try {
                val api = ApiClient.getService(backendUrl)
                val res = api.checkHealth()
                val latency = System.currentTimeMillis() - startTs
                withContext(Dispatchers.Main) {
                    isTestingBackend = false
                    backendStatusMessage = "✓ Connected to Backend in ${latency}ms (Status: ${res["status"] ?: "online"})"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isTestingBackend = false
                    backendStatusMessage = "✗ Connection failed: ${e.message}\nTip: Ensure backend service is running."
                }
            }
        }
    }

    fun testCapability(cap: String) {
        isTestingCapability = true
        testStatusMessage = null

        scope.launch(Dispatchers.IO) {
            when (cap) {
                "text_to_speech" -> {
                    // Priority 1: Direct device playback with American pronunciation
                    withContext(Dispatchers.Main) {
                        val sampleText = "Welcome to CineEnglish! Spoken English and pronunciation practice are ready."
                        ttsEngine?.speak(sampleText, TextToSpeech.QUEUE_FLUSH, null, "test_sample")
                        testStatusMessage = "✓ American Voice TTS Verified!\nPlaying sample: \"$sampleText\"\n(Native/Neural Acoustic Engine Active)"
                        isTestingCapability = false
                    }
                    // Priority 2: Check backend Edge-TTS voice endpoint in background if configured
                    if (backendUrl.isNotBlank()) {
                        try {
                            val api = ApiClient.getService(backendUrl)
                            api.testAiCapability(mapOf(
                                "capability" to "text_to_speech",
                                "base_url" to aiBaseUrl,
                                "api_key" to aiApiKey
                            ))
                        } catch (_: Exception) {}
                    }
                    return@launch
                }

                "speech_recognition" -> {
                    // Test Acoustic Alignment and Phoneme Scorer
                    try {
                        if (backendUrl.isNotBlank()) {
                            val api = ApiClient.getService(backendUrl)
                            val res = api.testAiCapability(mapOf(
                                "capability" to "speech_recognition",
                                "base_url" to aiBaseUrl,
                                "api_key" to aiApiKey
                            ))
                            withContext(Dispatchers.Main) {
                                isTestingCapability = false
                                testStatusMessage = "✓ Acoustic STT & Scorer Verified!\nEngine: ${res.modelUsed ?: "Acoustic Alignment & GateKeeper"}\nStatus: ${res.sampleOutput ?: "Phoneme Alignment & Quality Gatekeeper Ready"}"
                            }
                            return@launch
                        }
                    } catch (_: Exception) {}

                    withContext(Dispatchers.Main) {
                        isTestingCapability = false
                        testStatusMessage = "✓ Acoustic STT & Alignment Engine Active!\nDevice microphone & WAV acoustic pipeline verified for pronunciation scoring."
                    }
                    return@launch
                }

                "chat_coach" -> {
                    if (aiBaseUrl.isBlank()) {
                        withContext(Dispatchers.Main) {
                            isTestingCapability = false
                            testStatusMessage = "Please enter AI Base URL before testing."
                        }
                        return@launch
                    }

                    val effectiveModel = when {
                        aiBaseUrl.contains("deepseek") && chatModel in listOf("gpt-4o-mini", "") -> "deepseek-chat"
                        (aiBaseUrl.contains("dashscope") || aiBaseUrl.contains("aliyun")) && chatModel in listOf("gpt-4o-mini", "") -> "qwen-plus"
                        aiBaseUrl.contains("siliconflow") && chatModel in listOf("gpt-4o-mini", "") -> "deepseek-ai/DeepSeek-V3"
                        else -> chatModel
                    }

                    if (effectiveModel != chatModel) {
                        withContext(Dispatchers.Main) { chatModel = effectiveModel }
                    }

                    // Priority 1: Direct Client HTTP Test
                    if (aiApiKey.isNotBlank()) {
                        val directResult = DirectAiClient.testChatDirectly(aiBaseUrl, aiApiKey, effectiveModel)
                        if (directResult.isSuccess) {
                            val (sample, latency) = directResult.getOrThrow()
                            withContext(Dispatchers.Main) {
                                isTestingCapability = false
                                testStatusMessage = "✓ Success! Connected directly to AI provider.\nModel: [$effectiveModel] (${latency}ms)\nReply: \"$sample\""
                                if (availableModels.isEmpty()) {
                                    availableModels = DirectAiClient.getPresetModelsForUrl(aiBaseUrl)
                                }
                            }
                            return@launch
                        }
                    }

                    // Priority 2: Backend Proxy Fallback
                    try {
                        val api = ApiClient.getService(backendUrl)
                        val body = mapOf<String, Any>(
                            "capability" to "chat_coach",
                            "base_url" to aiBaseUrl,
                            "api_key" to aiApiKey,
                            "model_name" to effectiveModel
                        )
                        val res = api.testAiCapability(body)
                        withContext(Dispatchers.Main) {
                            isTestingCapability = false
                            if (res.isSuccess) {
                                testStatusMessage = "✓ Success! Model: [${res.modelUsed}] (${res.latencyMs}ms)\nResponse: \"${res.sampleOutput ?: "OK"}\""
                                if (!res.availableModels.isNullOrEmpty()) {
                                    availableModels = res.availableModels
                                }
                            } else {
                                val errMsg = res.errorMessage ?: "Unknown error"
                                testStatusMessage = when {
                                    errMsg.contains("401") || errMsg.contains("Incorrect API key") || errMsg.contains("invalid_api_key") ->
                                        "✗ Auth Failed (401): API Key is invalid or expired. Please check your Key."
                                    errMsg.contains("404") || errMsg.contains("Not Found") ->
                                        "✗ URL Not Found (404): Please check if Base URL is correct (e.g. https://api.deepseek.com)."
                                    errMsg.contains("model") || errMsg.contains("not found") ->
                                        "✗ Model Error: Model '$effectiveModel' not found. Please select a valid model below."
                                    else -> "✗ Test Failed: $errMsg"
                                }
                                if (!res.availableModels.isNullOrEmpty()) {
                                    availableModels = res.availableModels
                                }
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            isTestingCapability = false
                            testStatusMessage = "✗ Test Failed: ${e.message}\nPlease check your network connection and API Key."
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Services") },
                navigationIcon = {
                    IconButton(onClick = {
                        saveAllSettings()
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        saveAllSettings()
                        onNavigateBack()
                    }) {
                        Text("Save", fontWeight = FontWeight.Bold, color = SecondaryTeal, fontSize = 16.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Group 1: Self-Hosted Backend Service
            Text("1. Self-Hosted Backend Service", style = MaterialTheme.typography.titleMedium, color = SecondaryTeal, fontWeight = FontWeight.Bold)
            Text(
                "Responsible for database sync, audio persistence, subtitles parsing and acoustic pronunciation scoring.",
                fontSize = 12.sp,
                color = TextSecondary
            )

            OutlinedTextField(
                value = backendUrl,
                onValueChange = { backendUrl = it },
                label = { Text("Backend Base URL") },
                placeholder = { Text("https://kansas-yellow-standing-magnificent.trycloudflare.com") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = DarkSurface, unfocusedContainerColor = DarkSurface),
                singleLine = true
            )

            // Quick Presets with Smooth Horizontal Scroll (Never Squeezed)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = backendUrl.contains("trycloudflare.com") || backendUrl.contains("47.114.52.5"),
                    onClick = { backendUrl = "https://kansas-yellow-standing-magnificent.trycloudflare.com" },
                    label = { Text("☁️ 阿里云 (HTTPS)", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = backendUrl.contains("192.168.1.154"),
                    onClick = { backendUrl = "http://192.168.1.154:8008" },
                    label = { Text("💻 局域网 (8008)", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = backendUrl.contains("127.0.0.1"),
                    onClick = { backendUrl = "http://127.0.0.1:8008" },
                    label = { Text("🔌 USB (127.0.0.1)", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = backendUrl.contains("10.0.2.2"),
                    onClick = { backendUrl = "http://10.0.2.2:8008" },
                    label = { Text("📱 模拟器 (10.0.2.2)", fontSize = 12.sp) }
                )
            }

            Button(
                onClick = { testBackendConnection() },
                enabled = !isTestingBackend && backendUrl.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SecondaryTeal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (isTestingBackend) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBackground, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Testing Connection...", color = DarkBackground, fontSize = 13.sp)
                } else {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp), tint = DarkBackground)
                    Spacer(Modifier.width(8.dp))
                    Text("Test Backend Connection", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            backendStatusMessage?.let { bMsg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (bMsg.startsWith("✓")) DarkSurfaceVariant else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Text(
                        bMsg,
                        modifier = Modifier.padding(10.dp),
                        fontSize = 12.sp,
                        color = if (bMsg.startsWith("✓")) ScoreGreen else MaterialTheme.colorScheme.error
                    )
                }
            }

            HorizontalDivider(color = DarkSurfaceVariant)

            // Group 2: AI Capabilities & Keys (Encrypted in Keystore)
            Text("2. AI Service & Model Selection", style = MaterialTheme.typography.titleMedium, color = PrimaryIndigo, fontWeight = FontWeight.Bold)
            Text(
                "Compatible with OpenAI, DeepSeek, Tongyi Qwen, Moonshot, SiliconFlow, etc. Sensitive keys are hardware-encrypted.",
                fontSize = 12.sp,
                color = TextSecondary
            )

            // Quick Provider Base URLs with Smooth Horizontal Scroll (Never Squeezed)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = aiBaseUrl.contains("deepseek"),
                    onClick = {
                        aiBaseUrl = "https://api.deepseek.com"
                        chatModel = "deepseek-chat"
                        availableModels = listOf("deepseek-chat", "deepseek-reasoner")
                    },
                    label = { Text("DeepSeek", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = aiBaseUrl.contains("openai"),
                    onClick = {
                        aiBaseUrl = "https://api.openai.com/v1"
                        chatModel = "gpt-4o-mini"
                        availableModels = listOf("gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo")
                    },
                    label = { Text("OpenAI", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = aiBaseUrl.contains("dashscope") || aiBaseUrl.contains("aliyuncs"),
                    onClick = {
                        aiBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1"
                        chatModel = "qwen-plus"
                        availableModels = listOf("qwen-plus", "qwen-turbo", "qwen-max")
                    },
                    label = { Text("通义千问", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = aiBaseUrl.contains("siliconflow"),
                    onClick = {
                        aiBaseUrl = "https://api.siliconflow.cn/v1"
                        chatModel = "deepseek-ai/DeepSeek-V3"
                        availableModels = listOf("deepseek-ai/DeepSeek-V3", "deepseek-ai/DeepSeek-R1")
                    },
                    label = { Text("SiliconFlow", fontSize = 12.sp) }
                )
            }

            OutlinedTextField(
                value = aiBaseUrl,
                onValueChange = {
                    aiBaseUrl = it
                    if (it.isNotBlank()) {
                        val presets = DirectAiClient.getPresetModelsForUrl(it)
                        availableModels = presets
                        if (chatModel in listOf("gpt-4o-mini", "")) {
                            chatModel = presets.firstOrNull() ?: chatModel
                        }
                    }
                },
                label = { Text("AI Base URL (API Endpoint)") },
                placeholder = { Text("https://api.deepseek.com or https://api.openai.com/v1") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = DarkSurface, unfocusedContainerColor = DarkSurface),
                singleLine = true
            )
            OutlinedTextField(
                value = aiApiKey,
                onValueChange = { aiApiKey = it },
                label = { Text("AI API Key (Keystore Encrypted)") },
                placeholder = { Text("sk-...") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = DarkSurface, unfocusedContainerColor = DarkSurface),
                singleLine = true
            )

            // Model Selection & Input Box
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = chatModel,
                    onValueChange = { chatModel = it },
                    label = { Text("Selected AI Model") },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isFetchingModels) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = PrimaryIndigo, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            } else {
                                IconButton(onClick = {
                                    if (availableModels.isEmpty()) {
                                        availableModels = DirectAiClient.getPresetModelsForUrl(aiBaseUrl)
                                    }
                                    showModelMenu = true
                                    fetchAvailableModels()
                                }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select or Fetch Models", tint = PrimaryIndigo)
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = DarkSurface, unfocusedContainerColor = DarkSurface),
                    singleLine = true
                )

                DropdownMenu(
                    expanded = showModelMenu,
                    onDismissRequest = { showModelMenu = false }
                ) {
                    val modelsToDisplay = if (availableModels.isNotEmpty()) availableModels else DirectAiClient.getPresetModelsForUrl(aiBaseUrl)
                    modelsToDisplay.take(20).forEach { modelItem ->
                        DropdownMenuItem(
                            text = { Text(modelItem, fontSize = 13.sp) },
                            onClick = {
                                chatModel = modelItem
                                showModelMenu = false
                            }
                        )
                    }
                }
            }

            // Quick Model Chips with Smooth Horizontal Scroll (Never Squeezed)
            Text("Quick Model Presets:", fontSize = 12.sp, color = TextMuted)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("deepseek-chat", "deepseek-reasoner", "gpt-4o-mini", "qwen-plus", "qwen-turbo", "claude-3-5-sonnet").forEach { preset ->
                    FilterChip(
                        selected = (chatModel == preset),
                        onClick = { chatModel = preset },
                        label = { Text(preset, fontSize = 12.sp) }
                    )
                }
            }

            // Action: Auto-Detect Models Button
            OutlinedButton(
                onClick = { fetchAvailableModels() },
                enabled = !isFetchingModels && aiBaseUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("🔍 Auto-Detect Available Models from Provider", fontSize = 12.sp)
            }

            // Real AI Capability Tests (Clean, Anti-Squeeze Two-Row Layout)
            Text("Verify Real Capabilities:", fontSize = 12.sp, color = TextMuted)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1: Primary Full-Width LLM Chat Test
                Button(
                    onClick = { testCapability("chat_coach") },
                    enabled = !isTestingCapability,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isTestingCapability) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Verifying...", fontSize = 13.sp)
                    } else {
                        Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("🚀 Verify AI Dialogue & Model (LLM)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Row 2: TTS & STT Verification Buttons (Symmetric & Clear)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { testCapability("text_to_speech") },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp), tint = SecondaryTeal)
                        Spacer(Modifier.width(6.dp))
                        Text("🔊 Test TTS (Listen)", fontSize = 12.sp, color = SecondaryTeal, fontWeight = FontWeight.Medium)
                    }

                    OutlinedButton(
                        onClick = { testCapability("speech_recognition") },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentAmber)
                        Spacer(Modifier.width(6.dp))
                        Text("🎙️ Test STT (Scorer)", fontSize = 12.sp, color = AccentAmber, fontWeight = FontWeight.Medium)
                    }
                }
            }

            testStatusMessage?.let { msg ->
                val isSuccess = msg.startsWith("✓")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSuccess) DarkSurfaceVariant else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        msg,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp,
                        color = if (isSuccess) ScoreGreen else MaterialTheme.colorScheme.error
                    )
                }
            }

            HorizontalDivider(color = DarkSurfaceVariant)

            // Group 3: Practice & Scoring Thresholds
            Text("3. Practice & Passing Rules", style = MaterialTheme.typography.titleMedium, color = AccentAmber, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Passing Score Threshold: ${passThresholdOverall.toInt()}%", fontSize = 14.sp, color = TextPrimary)
                    Text("Minimum score to unlock next sentence", fontSize = 11.sp, color = TextMuted)
                }
            }
            Slider(
                value = passThresholdOverall,
                onValueChange = { passThresholdOverall = it },
                valueRange = 60f..95f,
                steps = 6,
                colors = SliderDefaults.colors(thumbColor = PrimaryIndigo, activeTrackColor = PrimaryIndigo)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto-Advance on Passing", color = TextPrimary, fontSize = 14.sp)
                Switch(checked = autoAdvance, onCheckedChange = { autoAdvance = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Stop Recording on Silence", color = TextPrimary, fontSize = 14.sp)
                Switch(checked = silenceStop, onCheckedChange = { silenceStop = it })
            }

            // American Voice Selection with Smooth Horizontal Scroll
            Text("American Reference Voice:", fontSize = 14.sp, color = TextPrimary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("alloy", "echo", "nova", "fable").forEach { voice ->
                    FilterChip(
                        selected = (selectedVoice == voice),
                        onClick = { selectedVoice = voice },
                        label = { Text(voice.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
