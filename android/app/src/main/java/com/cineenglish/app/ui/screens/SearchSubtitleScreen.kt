package com.cineenglish.app.ui.screens

import androidx.compose.foundation.background
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
import com.cineenglish.app.data.local.MaterialEntity
import com.cineenglish.app.data.local.SentenceEntity
import com.cineenglish.app.data.remote.ApiClient
import com.cineenglish.app.data.remote.SubtitleItemDto
import com.cineenglish.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSubtitleScreen(
    onNavigateBack: () -> Unit,
    onMaterialImported: (Long) -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val settings = remember { AppSettings(context) }
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var seasonText by remember { mutableStateOf("") }
    var episodeText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<SubtitleItemDto>>(emptyList()) }

    fun doSearch() {
        if (searchQuery.isBlank()) return
        isLoading = true
        errorMessage = null
        scope.launch(Dispatchers.IO) {
            try {
                val baseUrl = settings.backendUrlFlow.first()
                val api = ApiClient.getService(baseUrl)
                val seasonNum = seasonText.toIntOrNull()
                val episodeNum = episodeText.toIntOrNull()

                val resp = api.searchSubtitles(
                    query = searchQuery.trim(),
                    season = seasonNum,
                    episode = episodeNum
                )
                withContext(Dispatchers.Main) {
                    isLoading = false
                    if (resp.success && resp.data != null) {
                        results = resp.data
                        if (results.isEmpty()) {
                            errorMessage = "No English subtitles found for '$searchQuery'."
                        }
                    } else {
                        errorMessage = resp.message ?: "Subtitle query failed. Check backend connection."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    errorMessage = "Network error: ${e.localizedMessage ?: "Cannot reach backend"}"
                }
            }
        }
    }

    fun downloadAndImport(item: SubtitleItemDto) {
        isLoading = true
        scope.launch(Dispatchers.IO) {
            try {
                val baseUrl = settings.backendUrlFlow.first()
                val api = ApiClient.getService(baseUrl)

                val body = mapOf<String, Any>(
                    "subtitle_id" to item.subtitleId,
                    "movie_name" to item.movieName,
                    "release" to (item.release ?: "OpenSubtitles")
                )
                val importedNet = api.downloadSubtitle(body)

                // Fetch sentences and insert into local Room DB
                val netSentences = api.getSentences(importedNet.id)
                val localMaterialId = db.materialDao().insertMaterial(
                    MaterialEntity(
                        title = importedNet.title,
                        year = importedNet.year,
                        season = importedNet.season,
                        episode = importedNet.episode,
                        mediaType = importedNet.mediaType,
                        releaseVersion = importedNet.releaseVersion,
                        subtitleSource = "opensubtitles",
                        sentenceCount = netSentences.size,
                        durationMs = importedNet.durationMs
                    )
                )

                val localSentences = netSentences.map { ns ->
                    SentenceEntity(
                        materialId = localMaterialId,
                        index = ns.index,
                        startMs = ns.startMs,
                        endMs = ns.endMs,
                        text = ns.text,
                        rawText = ns.rawText,
                        speaker = ns.speaker
                    )
                }
                db.sentenceDao().insertSentences(localSentences)

                withContext(Dispatchers.Main) {
                    isLoading = false
                    onMaterialImported(localMaterialId)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    errorMessage = "Import failed: ${e.message}"
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search English Subtitles") },
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
            // Search Input Row
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Movie or TV show (e.g. Friends, Inception)", color = TextMuted) },
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(onClick = { doSearch() }) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = PrimaryIndigo)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface,
                    focusedBorderColor = PrimaryIndigo,
                    unfocusedBorderColor = DarkSurfaceVariant
                ),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            // Season & Episode filter row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = seasonText,
                    onValueChange = { seasonText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Season (opt)", color = TextMuted, fontSize = 13.sp) },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    singleLine = true
                )
                OutlinedTextField(
                    value = episodeText,
                    onValueChange = { episodeText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Episode (opt)", color = TextMuted, fontSize = 13.sp) },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    singleLine = true
                )
                Button(
                    onClick = { doSearch() },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    modifier = Modifier.height(56.dp)
                ) {
                    Text("Search")
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SecondaryTeal)
                }
            }

            errorMessage?.let { err ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = AccentAmber)
                        Spacer(Modifier.width(8.dp))
                        Text(err, color = TextPrimary, fontSize = 13.sp)
                    }
                }
            }

            // Results List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results, key = { it.subtitleId }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { downloadAndImport(item) },
                        colors = CardDefaults.cardColors(containerColor = DarkCard)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.movieName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(3.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (item.seasonNumber != null && item.episodeNumber != null) {
                                        Text(
                                            "S${item.seasonNumber}E${item.episodeNumber}",
                                            color = SecondaryTeal,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        item.release ?: "Release",
                                        color = TextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (item.hearingImpaired) {
                                        Text("[HI]", color = AccentAmber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = { downloadAndImport(item) }) {
                                Icon(Icons.Default.Download, contentDescription = "Import", tint = SecondaryTeal)
                            }
                        }
                    }
                }
            }
        }
    }
}
