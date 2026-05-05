package com.mindforge.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mindforge.app.data.repository.MergeSuggestion
import com.mindforge.app.ui.viewmodel.NotesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeSuggestionsScreen(
    viewModel: NotesViewModel,
    onNavigateBack: () -> Unit
) {
    val mergeSuggestions by viewModel.mergeSuggestions.collectAsState()
    val mergeAnalysisRunning by viewModel.mergeGroupAnalysisRunning.collectAsState()
    val analysisProgress by viewModel.analysisProgress.collectAsState()
    var showMergeDialog by remember { mutableStateOf<MergeSuggestion?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Merge Suggestions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshMergeSuggestionsOnly() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Info card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Merge,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Suggestions are saved on disk and restored after restart. " +
                            "They update when you add a note or edit a note shown here, or when you tap refresh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            if (mergeAnalysisRunning) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val p = analysisProgress
                    if (p != null) {
                        LinearProgressIndicator(
                            progress = { p },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Analyzing note pairs… ${(p * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            "Preparing analysis…",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            when {
                mergeSuggestions.isEmpty() && mergeAnalysisRunning -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "First scan in progress — see progress above.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                mergeSuggestions.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "No similar notes detected",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "No suggestions yet, or none found. Add a note to rescan, or tap " +
                                    "refresh to analyze all notes.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (mergeAnalysisRunning) {
                            item {
                                Text(
                                    "Updating… results below refresh when done.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        item {
                            Text(
                                "${mergeSuggestions.size} merge suggestions",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        
                        items(mergeSuggestions) { suggestion ->
                            MergeSuggestionCard(
                                suggestion = suggestion,
                                onMerge = { showMergeDialog = suggestion },
                                onDismiss = { /* Optionally implement dismiss */ }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Merge confirmation dialog with title selection
    showMergeDialog?.let { suggestion ->
        var selectedTitleOption by remember { mutableStateOf("note1") } // note1, note2, or custom
        var customTitle by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showMergeDialog = null },
            icon = { Icon(Icons.Default.Merge, contentDescription = null) },
            title = { Text("Merge Notes") },
            text = {
                Column {
                    Text("Combining these two notes:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("• ${suggestion.note1.title}", style = MaterialTheme.typography.bodySmall)
                    Text("• ${suggestion.note2.title}", style = MaterialTheme.typography.bodySmall)
                    
                    Spacer(Modifier.height(16.dp))
                    Divider()
                    Spacer(Modifier.height(16.dp))
                    
                    Text("Choose merged note title:", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    
                    // Title option 1
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTitleOption == "note1",
                            onClick = { selectedTitleOption = "note1" }
                        )
                        Text(
                            suggestion.note1.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    
                    // Title option 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTitleOption == "note2",
                            onClick = { selectedTitleOption = "note2" }
                        )
                        Text(
                            suggestion.note2.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    
                    // Custom title option
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTitleOption == "custom",
                            onClick = { selectedTitleOption = "custom" }
                        )
                        Text(
                            "Custom title",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    
                    if (selectedTitleOption == "custom") {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customTitle,
                            onValueChange = { customTitle = it },
                            label = { Text("Enter title") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "⚠️ This action cannot be undone.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalTitle = when (selectedTitleOption) {
                            "note1" -> null // Use default (note1)
                            "note2" -> suggestion.note2.title
                            "custom" -> customTitle.ifBlank { null }
                            else -> null
                        }
                        viewModel.mergeNotes(suggestion.note1, suggestion.note2, finalTitle)
                        showMergeDialog = null
                    },
                    enabled = selectedTitleOption != "custom" || customTitle.isNotBlank()
                ) {
                    Icon(Icons.Default.Merge, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Merge")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergeDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MergeSuggestionCard(
    suggestion: MergeSuggestion,
    onMerge: () -> Unit,
    onDismiss: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Similarity: ${(suggestion.similarityScore * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                
                LinearProgressIndicator(
                    progress = suggestion.similarityScore,
                    modifier = Modifier
                        .width(100.dp)
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Note 1
            Column {
                Text(
                    "📝 ${suggestion.note1.title}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    suggestion.note1.content.take(100) + if (suggestion.note1.content.length > 100) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(Modifier.height(8.dp))
            Divider()
            Spacer(Modifier.height(8.dp))
            
            // Note 2
            Column {
                Text(
                    "📝 ${suggestion.note2.title}",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    suggestion.note2.content.take(100) + if (suggestion.note2.content.length > 100) "..." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
                Button(onClick = onMerge) {
                    Icon(Icons.Default.Merge, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Merge")
                }
            }
        }
    }
}
