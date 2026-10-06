package com.cineenglish.app.ui.screens

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.cineenglish.app.data.local.AppDatabase
import com.cineenglish.app.data.local.SentenceEntity
import com.cineenglish.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPracticeScreen(
    materialId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    val material by produceState<com.cineenglish.app.data.local.MaterialEntity?>(initialValue = null) {
        value = db.materialDao().getMaterialById(materialId)
    }
    val sentences by db.sentenceDao().getSentencesByMaterialFlow(materialId).collectAsState(initial = emptyList())

    val listState = rememberLazyListState()
    var currentPlaybackPosition by remember { mutableLongStateOf(0L) }
    var pauseAtSentenceEnd by remember { mutableStateOf(true) }
    var loopCurrentSentence by remember { mutableStateOf(false) }
    var activeSentenceIndex by remember { mutableIntStateOf(0) }
    var timelineOffsetMs by remember { mutableLongStateOf(0L) }

    // Media3 Player Instance
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
        }
    }

    // Local Video/Audio File Picker
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                material?.let { mat ->
                    val updated = mat.copy(localVideoUri = uri.toString())
                    db.materialDao().updateMaterial(updated)
                }
            }
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer.prepare()
        }
    }

    // Setup media if already attached
    LaunchedEffect(material?.localVideoUri) {
        material?.localVideoUri?.let { uriStr ->
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(uriStr)))
            exoPlayer.prepare()
        }
    }

    // Position tracker & sentence end pause logic
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.isPlaying) {
                val pos = exoPlayer.currentPosition + timelineOffsetMs
                currentPlaybackPosition = pos

                // Find active sentence
                val idx = sentences.indexOfFirst { s -> pos in s.startMs..s.endMs }
                if (idx != -1 && idx != activeSentenceIndex) {
                    activeSentenceIndex = idx
                    listState.animateScrollToItem(idx)
                }

                // Check sentence end
                if (idx != -1) {
                    val s = sentences[idx]
                    if (pos >= s.endMs - 150) {
                        if (loopCurrentSentence) {
                            exoPlayer.seekTo(s.startMs - timelineOffsetMs)
                        } else if (pauseAtSentenceEnd) {
                            exoPlayer.pause()
                        }
                    }
                }
            }
            delay(100)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(material?.title ?: "Video Timeline Practice") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { mediaPicker.launch("video/*") }) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Bind Video", tint = SecondaryTeal)
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
        ) {
            // Video Player Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (material?.localVideoUri != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.VideoFile, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("No video attached to this script", color = TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = { mediaPicker.launch("video/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                        ) {
                            Text("Select Local Video File")
                        }
                    }
                }
            }

            // Timeline Controls (Anti-squeeze horizontal scroll)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = pauseAtSentenceEnd,
                        onClick = { pauseAtSentenceEnd = !pauseAtSentenceEnd },
                        label = { Text("Pause at End", fontSize = 12.sp, maxLines = 1) },
                        leadingIcon = {
                            Icon(Icons.Default.PauseCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    FilterChip(
                        selected = loopCurrentSentence,
                        onClick = { loopCurrentSentence = !loopCurrentSentence },
                        label = { Text("Loop", fontSize = 12.sp, maxLines = 1) },
                        leadingIcon = {
                            Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                }

                // Timeline Offset Controls (+ / - 500ms)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = { timelineOffsetMs -= 500 },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("-0.5s", color = TextSecondary, fontSize = 12.sp)
                    }
                    Text(
                        "${if (timelineOffsetMs >= 0) "+" else ""}${timelineOffsetMs / 1000f}s",
                        color = SecondaryTeal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = { timelineOffsetMs += 500 },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("+0.5s", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }

            // Subtitle Timeline List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(sentences, key = { _, s -> s.id }) { idx, s ->
                    val isActive = idx == activeSentenceIndex
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                activeSentenceIndex = idx
                                exoPlayer.seekTo(s.startMs - timelineOffsetMs)
                                exoPlayer.play()
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) DarkSurfaceVariant else DarkCard
                        ),
                        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, PrimaryIndigo) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${idx + 1}",
                                color = if (isActive) SecondaryTeal else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(32.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    s.text,
                                    color = if (isActive) TextPrimary else TextSecondary,
                                    fontSize = 15.sp,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${formatMs(s.startMs)} → ${formatMs(s.endMs)}",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            if (isActive) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = SecondaryTeal)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}
