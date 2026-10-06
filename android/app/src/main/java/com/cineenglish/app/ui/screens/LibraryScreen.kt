package com.cineenglish.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import com.cineenglish.app.data.local.MaterialEntity
import com.cineenglish.app.data.local.SentenceEntity
import com.cineenglish.app.ui.navigation.Screen
import com.cineenglish.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val materials by db.materialDao().getAllMaterialsFlow().collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }

    // File picker for subtitle (SRT, VTT, TXT)
    val subtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                        BufferedReader(InputStreamReader(stream)).readText()
                    } ?: ""
                    val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported Material"
                    val cleanTitle = fileName.substringBeforeLast('.')

                    val materialId = db.materialDao().insertMaterial(
                        MaterialEntity(
                            title = cleanTitle,
                            subtitleSource = "local_device",
                            localSubtitlePath = uri.toString()
                        )
                    )

                    val lines = content.lines().filter { it.isNotBlank() }
                    val sentences = mutableListOf<SentenceEntity>()
                    var sIndex = 0
                    var currentStart = 0L

                    for (line in lines) {
                        if (line.contains("-->") || line.trim().all { it.isDigit() }) continue
                        val cleanText = line.replace(Regex("<[^>]*>"), "")
                            .replace(Regex("\\[.*?\\]"), "")
                            .replace(Regex("\\(.*?\\)"), "")
                            .trim()
                        if (cleanText.length > 2) {
                            val duration = (cleanText.split(" ").size * 400L).coerceAtLeast(2000L)
                            sentences.add(
                                SentenceEntity(
                                    materialId = materialId,
                                    index = sIndex++,
                                    startMs = currentStart,
                                    endMs = currentStart + duration,
                                    text = cleanText
                                )
                            )
                            currentStart += duration + 300L
                        }
                    }
                    db.sentenceDao().insertSentences(sentences)
                    db.materialDao().updateMaterial(
                        db.materialDao().getMaterialById(materialId)!!.copy(
                            sentenceCount = sentences.size,
                            durationMs = currentStart
                        )
                    )
                } catch (e: Exception) {
                    // Handle import error gracefully
                }
            }
        }
    }

    val filteredMaterials = materials.filter {
        it.title.contains(searchQuery, ignoreCase = true)
    }

    var isImportingClassics by remember { mutableStateOf(false) }

    fun loadFullClassics(force: Boolean = false) {
        scope.launch {
            isImportingClassics = true
            try {
                com.cineenglish.app.data.local.BuiltinMaterialsLoader.ensureFullClassicsImported(
                    context = context,
                    db = db,
                    forceReload = force
                )
            } finally {
                isImportingClassics = false
            }
        }
    }

    LaunchedEffect(Unit) {
        // Automatically ensure full scripts for Shawshank Redemption & Forrest Gump are loaded
        loadFullClassics(force = false)
    }

    LaunchedEffect(materials) {
        materials.forEach {
            android.util.Log.i("LibraryDebug", "Material in DB: id=${it.id}, title=${it.title}, sentenceCount=${it.sentenceCount}")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("CineEnglish", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Movie English Shadowing Studio", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigate(Screen.SearchSubtitle.route) }) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "Search Online Subtitles", tint = SecondaryTeal)
                    }
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Import Subtitle", tint = PrimaryIndigo)
                    }
                    IconButton(onClick = { onNavigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = DarkSurface) {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(Icons.Default.Movie, contentDescription = "Materials") },
                    label = { Text("Materials") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigate(Screen.Vocabulary.route) },
                    icon = { Icon(Icons.Default.Book, contentDescription = "Vocab") },
                    label = { Text("Vocab") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigate(Screen.Review.route) },
                    icon = { Icon(Icons.Default.Replay, contentDescription = "Review") },
                    label = { Text("Review") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { onNavigate(Screen.LearningRecords.route) },
                    icon = { Icon(Icons.Default.Analytics, contentDescription = "Records") },
                    label = { Text("Records") }
                )
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text("Search materials...", color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryIndigo,
                    unfocusedBorderColor = DarkSurfaceVariant,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface
                ),
                singleLine = true
            )

            // Quick Stats Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text("Total Titles: ${materials.size}", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Text("Practice with authentic lines", color = TextSecondary, fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onNavigate(Screen.SearchSubtitle.route) },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Search Subtitles", fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(12.dp))

            if (filteredMaterials.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = TextMuted, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No materials found", color = TextSecondary, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("Search online or import your SRT/VTT subtitle files.", color = TextMuted, fontSize = 13.sp)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { loadFullClassics(force = true) },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Load Full Classics (Shawshank & Forrest Gump)")
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { subtitlePicker.launch("*/*") },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SecondaryTeal)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Import Local Subtitle")
                        }
                    }
                }
            } else {
                if (isImportingClassics) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = PrimaryIndigo)
                            Spacer(Modifier.width(12.dp))
                            Text("Importing full movie scripts (Shawshank & Forrest Gump)...", fontSize = 13.sp, color = TextPrimary)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredMaterials, key = { it.id }) { mat ->
                        MaterialItemCard(
                            material = mat,
                            onPractice = {
                                android.util.Log.i("LibraryDebug", "Clicked practice for id=${mat.id}, title=${mat.title}, count=${mat.sentenceCount}")
                                onNavigate(Screen.SentencePractice.createRoute(mat.id))
                            },
                            onVideo = { onNavigate(Screen.VideoPractice.createRoute(mat.id)) },
                            onRecall = { onNavigate(Screen.ShadowRecall.createRoute(mat.id)) },
                            onRolePlay = { onNavigate(Screen.RolePlay.createRoute(mat.id)) },
                            onDelete = {
                                scope.launch(Dispatchers.IO) {
                                    db.materialDao().deleteMaterial(mat)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import Subtitle or Media") },
            text = {
                Text("Select a subtitle file (SRT, VTT, ASS, TXT) from device storage, or load full official scripts of classic movies (The Shawshank Redemption & Forrest Gump).")
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        showImportDialog = false
                        loadFullClassics(force = true)
                    }) {
                        Text("Load Full Classics", color = SecondaryTeal)
                    }
                    TextButton(onClick = {
                        showImportDialog = false
                        subtitlePicker.launch("*/*")
                    }) {
                        Text("Select File", color = PrimaryIndigo)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
fun MaterialItemCard(
    material: MaterialEntity,
    onPractice: () -> Unit,
    onVideo: () -> Unit,
    onRecall: () -> Unit,
    onRolePlay: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onPractice() },
        colors = CardDefaults.cardColors(containerColor = DarkCard)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = material.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${material.sentenceCount} sentences • ${material.releaseVersion ?: material.subtitleSource}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(10.dp))

            // Action Mode Buttons (Anti-squeeze horizontal scroll, never broken words)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onPractice,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = DarkSurfaceVariant),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryIndigo)
                    Spacer(Modifier.width(6.dp))
                    Text("Shadowing", fontSize = 12.sp, color = TextPrimary, maxLines = 1)
                }
                FilledTonalButton(
                    onClick = onVideo,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = DarkSurfaceVariant),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = SecondaryTeal)
                    Spacer(Modifier.width(6.dp))
                    Text("Video", fontSize = 12.sp, color = TextPrimary, maxLines = 1)
                }
                FilledTonalButton(
                    onClick = onRecall,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = DarkSurfaceVariant),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentAmber)
                    Spacer(Modifier.width(6.dp))
                    Text("Recall", fontSize = 12.sp, color = TextPrimary, maxLines = 1)
                }
                FilledTonalButton(
                    onClick = onRolePlay,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = DarkSurfaceVariant),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryIndigo)
                    Spacer(Modifier.width(6.dp))
                    Text("RolePlay", fontSize = 12.sp, color = TextPrimary, maxLines = 1)
                }
            }
        }
    }
}
