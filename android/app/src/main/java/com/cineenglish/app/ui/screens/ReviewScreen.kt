package com.cineenglish.app.ui.screens

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
import com.cineenglish.app.data.local.ReviewItemEntity
import com.cineenglish.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val dueItems by db.reviewDao().getDueReviewItemsFlow(System.currentTimeMillis()).collectAsState(initial = emptyList())

    val intervalsDays = listOf(1, 2, 4, 7, 15, 30)

    fun submitReviewResult(item: ReviewItemEntity, quality: Int) {
        scope.launch(Dispatchers.IO) {
            val newStage = when {
                quality >= 3 -> (item.srsStage + 1).coerceAtMost(intervalsDays.size - 1)
                quality == 1 -> 0
                else -> item.srsStage
            }
            val isMasteredNow = (quality >= 3 && newStage >= intervalsDays.size - 1)
            val nextDays = intervalsDays[newStage]
            val nextTime = System.currentTimeMillis() + (nextDays * 24 * 3600 * 1000L)

            db.reviewDao().updateReviewItem(
                item.copy(
                    srsStage = newStage,
                    nextReviewAt = nextTime,
                    lastReviewedAt = System.currentTimeMillis(),
                    isMastered = isMasteredNow
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spaced Review (SRS)") },
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
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "Due Today: ${dueItems.size} items",
                style = MaterialTheme.typography.titleMedium,
                color = SecondaryTeal,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Review weak sentences and words using spaced intervals.",
                color = TextSecondary,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(14.dp))

            if (dueItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ScoreGreen, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("All caught up!", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text("No pending reviews for today. Great job!", color = TextMuted, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(dueItems, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = DarkCard)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        item.itemType.uppercase(),
                                        fontSize = 11.sp,
                                        color = AccentAmber,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    TextButton(onClick = {
                                        // Ignore false positive
                                        scope.launch(Dispatchers.IO) {
                                            db.reviewDao().updateReviewItem(item.copy(isIgnored = true))
                                        }
                                    }) {
                                        Text("Ignore False Alarm", fontSize = 11.sp, color = TextMuted)
                                    }
                                }

                                Text(
                                    item.content,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )

                                item.contextSentence?.let { ctx ->
                                    if (ctx != item.content) {
                                        Spacer(Modifier.height(4.dp))
                                        Text("Context: $ctx", fontSize = 12.sp, color = TextSecondary)
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                // SRS Quality Buttons (Anti-squeeze, compact & single line)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { submitReviewResult(item, 1) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ScoreRed),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                                    ) {
                                        Text("Forgot", fontSize = 11.sp, maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = { submitReviewResult(item, 2) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ScoreYellow),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                                    ) {
                                        Text("Hard", fontSize = 11.sp, maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = { submitReviewResult(item, 3) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SecondaryTeal),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                                    ) {
                                        Text("Good", fontSize = 11.sp, maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = { submitReviewResult(item, 4) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ScoreGreen),
                                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                                    ) {
                                        Text("Mastered", fontSize = 11.sp, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
