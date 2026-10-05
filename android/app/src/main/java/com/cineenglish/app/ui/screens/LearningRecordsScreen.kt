package com.cineenglish.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cineenglish.app.data.local.AppDatabase
import com.cineenglish.app.domain.audio.AudioPlayer
import com.cineenglish.app.ui.theme.*
import com.cineenglish.app.worker.RecordingUploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningRecordsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val player = remember { AudioPlayer(context) }

    var totalAttempts by remember { mutableIntStateOf(0) }
    var passedCount by remember { mutableIntStateOf(0) }
    var avgScore by remember { mutableFloatStateOf(0f) }
    var pendingSyncCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        totalAttempts = db.practiceRecordDao().getTotalAttempts()
        passedCount = db.practiceRecordDao().getPassedCount()
        avgScore = db.practiceRecordDao().getAverageScore() ?: 0f
        pendingSyncCount = db.practiceRecordDao().getPendingSyncRecords().size
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Learning Records & Recordings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        RecordingUploadWorker.enqueueSync(context)
                    }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Recordings", tint = SecondaryTeal)
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
                .padding(horizontal = 16.dp)
        ) {
            // Stats Overview Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    title = "Attempts",
                    value = "$totalAttempts",
                    modifier = Modifier.weight(1f),
                    color = PrimaryIndigo
                )
                StatCard(
                    title = "Passed",
                    value = "$passedCount",
                    modifier = Modifier.weight(1f),
                    color = ScoreGreen
                )
                StatCard(
                    title = "Avg Score",
                    value = "${avgScore.toInt()}",
                    modifier = Modifier.weight(1f),
                    color = SecondaryTeal
                )
            }

            // Sync Status Indicator
            if (pendingSyncCount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudQueue, contentDescription = null, tint = AccentAmber)
                            Spacer(Modifier.width(8.dp))
                            Text("$pendingSyncCount recordings pending upload", fontSize = 12.sp, color = TextPrimary)
                        }
                        TextButton(onClick = { RecordingUploadWorker.enqueueSync(context) }) {
                            Text("Sync Now", color = SecondaryTeal, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text("Practice Log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))

            // History Records List
            val recordsFlow = remember { db.practiceRecordDao().getRecordsByMaterialFlow(0) }
            val records by recordsFlow.collectAsState(initial = emptyList())

            if (records.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No local recordings found yet.", color = TextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(records, key = { it.id }) { rec ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DarkCard)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "Score: ${rec.overallScore.toInt()}",
                                            fontWeight = FontWeight.Bold,
                                            color = if (rec.isPassed) ScoreGreen else ScoreYellow
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            rec.practiceMode.replace('_', ' '),
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(rec.createdAt)),
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }

                                IconButton(onClick = {
                                    val f = File(rec.localAudioPath)
                                    if (f.exists()) {
                                        player.playAudio(Uri.fromFile(f))
                                    }
                                }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = SecondaryTeal)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 11.sp, color = TextMuted)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
