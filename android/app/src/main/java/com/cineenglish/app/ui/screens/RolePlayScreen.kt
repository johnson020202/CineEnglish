package com.cineenglish.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cineenglish.app.data.local.AppDatabase
import com.cineenglish.app.data.local.AppSettings
import com.cineenglish.app.data.local.SentenceEntity
import com.cineenglish.app.data.remote.ApiClient
import com.cineenglish.app.domain.audio.AudioPlayer
import com.cineenglish.app.domain.audio.AudioRecorder
import com.cineenglish.app.domain.audio.NativeTtsEngine
import com.cineenglish.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DialogueMessage(
    val speaker: String,
    val text: String,
    val isUser: Boolean,
    val isAiGenerated: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RolePlayScreen(
    materialId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val settings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()

    val sentences by db.sentenceDao().getSentencesByMaterialFlow(materialId).collectAsState(initial = emptyList())
    val speakers = remember(sentences) {
        val detected = sentences.mapNotNull { it.speaker }.distinct()
        if (detected.isNotEmpty()) detected else listOf("CHARACTER A", "CHARACTER B")
    }

    var userCharacter by remember { mutableStateOf(speakers.getOrNull(1) ?: "YOU") }
    var currentSentenceIndex by remember { mutableIntStateOf(0) }

    val recorder = remember { AudioRecorder(context) }
    val player = remember { AudioPlayer(context) }
    var isRecording by remember { mutableStateOf(false) }

    var dialogueHistory by remember { mutableStateOf<List<DialogueMessage>>(emptyList()) }
    var enableAiExtension by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            recorder.cancelRecording()
            player.release()
        }
    }

    fun playDialogueTurn(turn: SentenceEntity) {
        val isUserTurn = (turn.speaker == userCharacter)
        if (isUserTurn) {
            // Pause and wait for user voice
            dialogueHistory = dialogueHistory + DialogueMessage(turn.speaker ?: userCharacter, turn.text, isUser = true)
        } else {
            // Opponent turn: synthesize voice and play
            val speakerName = turn.speaker ?: "OPPONENT"
            dialogueHistory = dialogueHistory + DialogueMessage(speakerName, turn.text, isUser = false)
            scope.launch(Dispatchers.IO) {
                val baseUrl = settings.backendUrlFlow.first()
                val aiApiKey = settings.getAiApiKeyFlow().first()
                val aiBaseUrl = settings.aiBaseUrlFlow.first()
                val voiceUrl = "$baseUrl/api/v1/ai/synthesize-american-voice?text=${Uri.encode(turn.text)}&voice=alloy&api_key=${Uri.encode(aiApiKey)}&base_url=${Uri.encode(aiBaseUrl)}"

                withContext(Dispatchers.Main) {
                    player.onPlaybackError = {
                        player.onPlaybackError = null
                        NativeTtsEngine.speak(turn.text)
                    }
                    try {
                        player.playAudio(Uri.parse(voiceUrl))
                    } catch (e: Exception) {
                        NativeTtsEngine.speak(turn.text)
                    }
                }
            }
        }
    }

    fun requestAiExtension() {
        scope.launch(Dispatchers.IO) {
            try {
                val baseUrl = settings.backendUrlFlow.first()
                val aiApiKey = settings.getAiApiKeyFlow().first()
                val aiBaseUrl = settings.aiBaseUrlFlow.first()
                val api = ApiClient.getService(baseUrl)
                val opponentName = speakers.firstOrNull { it != userCharacter } ?: "CO-STAR"

                val body = mapOf(
                    "material_title" to "Scene Roleplay",
                    "character_name" to opponentName,
                    "user_character_name" to userCharacter,
                    "dialogue_history" to dialogueHistory.map { mapOf("speaker" to it.speaker, "text" to it.text) },
                    "difficulty_level" to "intermediate",
                    "api_key" to aiApiKey,
                    "base_url" to aiBaseUrl
                )
                val res = api.extendRolePlay(body)
                withContext(Dispatchers.Main) {
                    dialogueHistory = dialogueHistory + DialogueMessage(res.speaker, res.text, isUser = false, isAiGenerated = true)
                    val voiceUrl = "$baseUrl/api/v1/ai/synthesize-american-voice?text=${Uri.encode(res.text)}&voice=alloy&api_key=${Uri.encode(aiApiKey)}&base_url=${Uri.encode(aiBaseUrl)}"
                    player.onPlaybackError = {
                        player.onPlaybackError = null
                        NativeTtsEngine.speak(res.text)
                    }
                    try {
                        player.playAudio(Uri.parse(voiceUrl))
                    } catch (e: Exception) {
                        NativeTtsEngine.speak(res.text)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Character Roleplay") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    FilterChip(
                        selected = enableAiExtension,
                        onClick = { enableAiExtension = !enableAiExtension },
                        label = { Text("AI Extend", fontSize = 12.sp) }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Speaker Assignment Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Select Your Character:", fontSize = 12.sp, color = TextMuted)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        speakers.forEach { spk ->
                            FilterChip(
                                selected = (userCharacter == spk),
                                onClick = { userCharacter = spk },
                                label = { Text(spk, fontSize = 12.sp) },
                                leadingIcon = {
                                    if (userCharacter == spk) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Dialogue Chat Stream
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(dialogueHistory) { msg ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(msg.speaker, fontSize = 11.sp, color = if (msg.isUser) PrimaryIndigo else SecondaryTeal, fontWeight = FontWeight.SemiBold)
                            if (msg.isAiGenerated) {
                                Spacer(Modifier.width(4.dp))
                                Text("[AI Extended]", fontSize = 10.sp, color = AccentAmber)
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (msg.isUser) PrimaryIndigo else DarkSurface)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(msg.text, color = TextPrimary, fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Action / Recording Control Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (currentSentenceIndex < sentences.size) {
                            playDialogueTurn(sentences[currentSentenceIndex])
                            currentSentenceIndex++
                        } else if (enableAiExtension) {
                            requestAiExtension()
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SecondaryTeal)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Next Line")
                }

                // Voice Reply Record Button
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) ScoreRed else PrimaryIndigo)
                        .clickable {
                            if (isRecording) {
                                isRecording = false
                                recorder.stopRecording()
                                if (enableAiExtension) {
                                    requestAiExtension()
                                }
                            } else {
                                isRecording = true
                                recorder.startRecording(0L, enableSilenceStop = true)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Speak In Character",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                if (enableAiExtension) {
                    TextButton(onClick = { requestAiExtension() }) {
                        Text("AI Continue", color = AccentAmber)
                    }
                } else {
                    Spacer(Modifier.width(48.dp))
                }
            }
        }
    }
}
