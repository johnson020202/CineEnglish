package com.cineenglish.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cineenglish.app.data.local.AppDatabase
import com.cineenglish.app.data.local.AppSettings
import com.cineenglish.app.domain.audio.AudioPlayer
import com.cineenglish.app.domain.audio.NativeTtsEngine
import com.cineenglish.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularyScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val settings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()
    val vocabularies by db.vocabularyDao().getAllVocabulariesFlow().collectAsState(initial = emptyList())
    val player = remember { AudioPlayer(context) }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    fun playWordAudio(word: String) {
        scope.launch(Dispatchers.IO) {
            val baseUrl = settings.backendUrlFlow.first()
            val aiApiKey = settings.getAiApiKeyFlow().first()
            val aiBaseUrl = settings.aiBaseUrlFlow.first()
            val voiceUrl = "$baseUrl/api/v1/ai/synthesize-american-voice?text=${Uri.encode(word)}&voice=alloy&api_key=${Uri.encode(aiApiKey)}&base_url=${Uri.encode(aiBaseUrl)}"

            withContext(Dispatchers.Main) {
                player.onPlaybackError = {
                    player.onPlaybackError = null
                    NativeTtsEngine.speak(word)
                }
                try {
                    player.playAudio(Uri.parse(voiceUrl))
                } catch (e: Exception) {
                    NativeTtsEngine.speak(word)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vocabulary Notebook (${vocabularies.size})") },
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
            if (vocabularies.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Book, contentDescription = null, tint = TextMuted, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No saved words yet", color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Text("Tap words in sentence practice to look up and bookmark.", color = TextMuted, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(vocabularies, key = { it.id }) { item ->
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
                                    Row(
                                        modifier = Modifier.weight(1f, fill = false),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            item.word,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        item.phonetic?.let {
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                it,
                                                color = SecondaryTeal,
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        IconButton(
                                            onClick = { playWordAudio(item.word) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.VolumeUp, contentDescription = "Pronounce", tint = PrimaryIndigo, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                db.vocabularyDao().deleteVocabulary(item)
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove", tint = TextMuted, modifier = Modifier.size(18.dp))
                                    }
                                }

                                Spacer(Modifier.height(6.dp))
                                Text(item.definitionEn, color = TextPrimary, fontSize = 13.sp)

                                item.simpleExplanation?.let { simple ->
                                    Spacer(Modifier.height(4.dp))
                                    Text("Simple: $simple", color = TextSecondary, fontSize = 12.sp)
                                }

                                item.contextSentence?.let { ctx ->
                                    Spacer(Modifier.height(6.dp))
                                    Text("Context: \"$ctx\"", color = TextMuted, fontSize = 11.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
