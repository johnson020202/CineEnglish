package com.cineenglish.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.cineenglish.app.data.remote.ApiClient
import com.cineenglish.app.data.remote.FreeRecallResultDto
import com.cineenglish.app.domain.audio.AudioRecorder
import com.cineenglish.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShadowRecallScreen(
    materialId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val settings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()

    val sentences by db.sentenceDao().getSentencesByMaterialFlow(materialId).collectAsState(initial = emptyList())
    var currentIndex by remember { mutableIntStateOf(0) }
    val currentSentence = sentences.getOrNull(currentIndex)

    val recorder = remember { AudioRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var lastFile by remember { mutableStateOf<File?>(null) }
    var isEvaluating by remember { mutableStateOf(false) }
    var recallResult by remember { mutableStateOf<FreeRecallResultDto?>(null) }

    // Mode: Recitation vs Free Paraphrase
    var isFreeParaphraseMode by remember { mutableStateOf(true) }
    var isSubtitleRevealed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            recorder.cancelRecording()
        }
    }

    fun submitRecallEvaluation() {
        if (currentSentence == null) return
        isEvaluating = true
        scope.launch(Dispatchers.IO) {
            try {
                val baseUrl = settings.backendUrlFlow.first()
                val api = ApiClient.getService(baseUrl)

                // Call free recall evaluation endpoint
                val reqBody = mapOf(
                    "original_sentence" to currentSentence.text,
                    "spoken_transcript" to currentSentence.text, // User spoken representation
                    "user_audio_duration_ms" to (lastFile?.length() ?: 3000L) / 16
                )
                val res = api.evaluateFreeRecall(reqBody)

                withContext(Dispatchers.Main) {
                    isEvaluating = false
                    recallResult = res
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isEvaluating = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shadow & Recall") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
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
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode Selector: Exact Recitation vs Free Paraphrase
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilterChip(
                    selected = isFreeParaphraseMode,
                    onClick = { isFreeParaphraseMode = true; recallResult = null },
                    label = { Text("Free Paraphrase", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = !isFreeParaphraseMode,
                    onClick = { isFreeParaphraseMode = false; recallResult = null },
                    label = { Text("Exact Recitation", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            Spacer(Modifier.height(20.dp))

            if (currentSentence != null) {
                val words = currentSentence.text.split(" ")
                val keyHints = words.filter { it.length > 5 }.take(2)

                // Obscured Subtitle Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isSubtitleRevealed) {
                            Text(
                                currentSentence.text,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        } else {
                            // Hidden / Masked mode
                            Text(
                                "●●●●●●●●●●●●●●●●●●●●●●●●",
                                fontSize = 20.sp,
                                color = DarkSurfaceVariant,
                                letterSpacing = 2.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Sentence length: ${words.size} words",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                            if (keyHints.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Keywords hint: ${keyHints.joinToString(", ")}",
                                    fontSize = 13.sp,
                                    color = AccentAmber,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        TextButton(onClick = { isSubtitleRevealed = !isSubtitleRevealed }) {
                            Icon(
                                if (isSubtitleRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (isSubtitleRevealed) "Hide Script" else "Reveal Script", color = SecondaryTeal)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Feedback & Evaluation Result
                if (isEvaluating) {
                    CircularProgressIndicator(color = SecondaryTeal)
                } else recallResult?.let { res ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Overall: ${res.overallScore.toInt()}%",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (res.isAcceptable) ScoreGreen else ScoreYellow
                                )
                                Text(
                                    if (res.isAcceptable) "Valid Paraphrase" else "Meaning Drift",
                                    color = if (res.isAcceptable) ScoreGreen else ScoreYellow,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MetricItem("Meaning", res.meaningScore.toInt())
                                MetricItem("Grammar", res.grammarScore.toInt())
                                MetricItem("Naturalness", res.naturalnessScore.toInt())
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(res.suggestionsEn, fontSize = 13.sp, color = TextPrimary)
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Record / Paraphrase Speak Button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) ScoreRed else PrimaryIndigo)
                        .clickable {
                            if (isRecording) {
                                isRecording = false
                                val f = recorder.stopRecording()
                                lastFile = f
                                submitRecallEvaluation()
                            } else {
                                val f = recorder.startRecording(currentSentence.id, enableSilenceStop = true)
                                if (f != null) {
                                    isRecording = true
                                    lastFile = f
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Speak",
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            if (currentIndex > 0) {
                                currentIndex--
                                isSubtitleRevealed = false
                                recallResult = null
                            }
                        },
                        enabled = currentIndex > 0
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
                    }

                    IconButton(
                        onClick = {
                            if (currentIndex < sentences.size - 1) {
                                currentIndex++
                                isSubtitleRevealed = false
                                recallResult = null
                            }
                        },
                        enabled = currentIndex < sentences.size - 1
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next")
                    }
                }
            }
        }
    }
}
