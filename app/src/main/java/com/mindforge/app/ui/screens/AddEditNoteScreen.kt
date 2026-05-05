package com.mindforge.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mindforge.app.ui.viewmodel.NotesViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditNoteScreen(
    viewModel: NotesViewModel,
    noteId: Long?,
    onNavigateBack: () -> Unit,
    onOpenRelatedNote: (Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val duplicateWarnings by viewModel.duplicateWarnings.collectAsState()
    val allNotes by viewModel.allNotes.collectAsState()
    val relatedNotes by viewModel.relatedNotes.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(noteId) {
        onDispose {
            viewModel.clearRelatedNotes()
        }
    }

    LaunchedEffect(noteId, allNotes) {
        if (noteId == null) {
            title = ""
            content = ""
            viewModel.clearRelatedNotes()
        } else {
            viewModel.loadRelatedNotes(noteId)
            allNotes.find { it.id == noteId }?.let { note ->
                title = note.title
                content = note.content
            }
        }
    }

    LaunchedEffect(title, content, noteId) {
        val draftLen = title.length + content.length
        if (draftLen > 35) {
            delay(550)
            viewModel.checkForDuplicates(title, content, excludeNoteId = noteId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (noteId == null) "New Note" else "Edit Note") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (noteId != null) {
                        IconButton(onClick = {
                            allNotes.find { it.id == noteId }?.let {
                                viewModel.deleteNote(it)
                                onNavigateBack()
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    IconButton(
                        onClick = {
                            if (title.isNotBlank() && content.isNotBlank()) {
                                coroutineScope.launch {
                                    isLoading = true
                                    try {
                                        if (noteId == null) {
                                            viewModel.saveNewNote(title, content)
                                        } else {
                                            viewModel.saveEditedNote(noteId, title, content)
                                        }
                                        onNavigateBack()
                                    } catch (e: Exception) {
                                        android.util.Log.e("AddEditNote", "Save failed", e)
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            if (duplicateWarnings.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Similar notes detected (BERT)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${duplicateWarnings.size} note(s) are semantically close to what you typed. Consider merging from the Merge screen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        duplicateWarnings.take(3).forEach { duplicate ->
                            Text(
                                "• ${duplicate.title}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Content") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                maxLines = Int.MAX_VALUE
            )

            if (noteId != null && relatedNotes.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Related notes (BERT similarity)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        relatedNotes.forEach { (related, score) ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { onOpenRelatedNote(related.id) },
                                tonalElevation = 1.dp,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            related.title.ifBlank { "Untitled" },
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            related.content.take(80).let { if (related.content.length > 80) "$it…" else it },
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                        )
                                    }
                                    Text(
                                        "${(score * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
