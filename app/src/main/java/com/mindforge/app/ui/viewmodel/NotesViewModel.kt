package com.mindforge.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mindforge.app.data.local.entity.Note
import com.mindforge.app.data.repository.MergeSuggestion
import com.mindforge.app.data.repository.NoteRepository
import com.mindforge.app.debug.DebugSampleNotes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class NotesViewModel(
    private val repository: NoteRepository
) : ViewModel() {

    val allNotes = repository.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _duplicateWarnings = MutableStateFlow<List<Note>>(emptyList())
    val duplicateWarnings: StateFlow<List<Note>> = _duplicateWarnings.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Pair<Note, Float>>>(emptyList())
    val searchResults: StateFlow<List<Pair<Note, Float>>> = _searchResults.asStateFlow()

    private val _mergeSuggestions = MutableStateFlow<List<MergeSuggestion>>(emptyList())
    val mergeSuggestions: StateFlow<List<MergeSuggestion>> = _mergeSuggestions.asStateFlow()

    private val _groupedNotes = MutableStateFlow<List<List<Note>>>(emptyList())
    val groupedNotes: StateFlow<List<List<Note>>> = _groupedNotes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** True only while ML merge/group work from [launchExclusiveMergeGroupWork] runs. */
    private val _mergeGroupAnalysisRunning = MutableStateFlow(false)
    val mergeGroupAnalysisRunning: StateFlow<Boolean> = _mergeGroupAnalysisRunning.asStateFlow()

    /** Determinate progress 0f..1f while merge/group analysis runs; null when idle. */
    private val _analysisProgress = MutableStateFlow<Float?>(null)
    val analysisProgress: StateFlow<Float?> = _analysisProgress.asStateFlow()

    private val _relatedNotes = MutableStateFlow<List<Pair<Note, Float>>>(emptyList())
    val relatedNotes: StateFlow<List<Pair<Note, Float>>> = _relatedNotes.asStateFlow()

    /** In-flight merge/group ML work — cancelled so stale results never overwrite after merge/delete/etc. */
    private var mergeGroupAnalysisJob: Job? = null

    /**
     * Pending disk loads from [init] must not overwrite newer UI after ML or explicit refresh.
     * Separate epochs so e.g. "refresh groups" does not drop a still-valid merge list from disk.
     */
    private val mergeSuggestionsUiEpoch = AtomicInteger(0)
    private val groupedNotesUiEpoch = AtomicInteger(0)

    init {
        val mergeEpochAtRead = mergeSuggestionsUiEpoch.get()
        val groupEpochAtRead = groupedNotesUiEpoch.get()
        viewModelScope.launch {
            try {
                val merge = repository.loadMergeSuggestionsFromCache()
                if (mergeSuggestionsUiEpoch.get() == mergeEpochAtRead) {
                    _mergeSuggestions.value = merge
                }
                val groups = repository.loadSimilarGroupsFromCache()
                if (groupedNotesUiEpoch.get() == groupEpochAtRead) {
                    _groupedNotes.value = groups
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("NotesViewModel", "Failed to load persisted merge/group cache", e)
            }
        }
    }

    private fun pruneStaleMergeSuggestionsAndGroups(removedIds: Set<Long>) {
        if (removedIds.isEmpty()) return
        _mergeSuggestions.value = _mergeSuggestions.value.filter {
            it.note1.id !in removedIds && it.note2.id !in removedIds
        }
        _groupedNotes.value =
            _groupedNotes.value
                .map { group -> group.filter { it.id !in removedIds } }
                .filter { it.size > 1 }
    }

    private suspend fun runFullMergeAndGroupsRecomputeAndPersist() {
        _analysisProgress.value = 0f
        val merge = repository.findMergeSuggestions(
            onProgress = { done, total ->
                _analysisProgress.value = (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) * 0.5f
            },
            onIncrementalMerge = { list ->
                withContext(Dispatchers.Main.immediate) {
                    _mergeSuggestions.value = list
                }
            },
            incrementalOnnx = true
        )
        withContext(Dispatchers.Main.immediate) {
            _mergeSuggestions.value = merge
        }

        _analysisProgress.value = 0.5f
        val groups = repository.groupSimilarNotes(
            onProgress = { done, total ->
                _analysisProgress.value =
                    0.5f + (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) * 0.5f
            },
            onIncrementalGroups = { g ->
                withContext(Dispatchers.Main.immediate) {
                    _groupedNotes.value = g
                }
            }
        )
        withContext(Dispatchers.Main.immediate) {
            _groupedNotes.value = groups
        }

        repository.persistMergeAndGroupAnalysis(merge, groups)
        repository.markAllNotesAnalyzedForSimilarity()
    }

    /**
     * Cancels any in-flight merge/group analysis, then launches [block] with loading UX.
     * Prevents overlapping BERT passes from committing stale pairs after merges/deletes.
     */
    private fun launchExclusiveMergeGroupWork(
        tag: String,
        invalidateMergeSuggestionsDiskLoad: Boolean = true,
        invalidateGroupedNotesDiskLoad: Boolean = true,
        block: suspend () -> Unit
    ) {
        mergeGroupAnalysisJob?.cancel()
        if (invalidateMergeSuggestionsDiskLoad) mergeSuggestionsUiEpoch.incrementAndGet()
        if (invalidateGroupedNotesDiskLoad) groupedNotesUiEpoch.incrementAndGet()
        mergeGroupAnalysisJob =
            viewModelScope.launch {
                // Only this job may clear analysis UX. A cancelled predecessor's finally must not
                // run after mergeGroupAnalysisJob already points at a newer Job (stale finally would
                // zero out progress while the new analysis is still running — reanalyze appears dead).
                val thisJob = coroutineContext[Job]!!
                try {
                    _mergeGroupAnalysisRunning.value = true
                    _analysisProgress.value = 0f
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("NotesViewModel", "$tag failed", e)
                } finally {
                    if (mergeGroupAnalysisJob === thisJob) {
                        _mergeGroupAnalysisRunning.value = false
                        _analysisProgress.value = null
                    }
                }
            }
    }

    suspend fun saveNewNote(title: String, content: String) {
        val note = Note(title = title, content = content, analyzedForSimilarity = false)
        repository.insertNote(note)
        recalculateMergeAndGroupsFull()
    }

    suspend fun saveEditedNote(noteId: Long, title: String, content: String) {
        val existing = repository.getNoteById(noteId) ?: return
        repository.updateNote(
            existing.copy(
                title = title,
                content = content,
                updatedAt = System.currentTimeMillis(),
                analyzedForSimilarity = false
            )
        )
        if (noteAppearsInCachedMergeOrGroups(noteId)) {
            recalculateMergeAndGroupsFull()
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            val wasInMerge =
                _mergeSuggestions.value.any { it.note1.id == note.id || it.note2.id == note.id }
            val wasInGroups = _groupedNotes.value.any { g -> g.any { it.id == note.id } }
            repository.deleteNote(note)
            pruneStaleMergeSuggestionsAndGroups(setOf(note.id))
            if (wasInMerge || wasInGroups) {
                recalculateMergeAndGroupsFull()
            }
        }
    }

    fun checkForDuplicates(title: String, content: String, excludeNoteId: Long? = null) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val duplicates =
                    repository.findDuplicateNotes(title, content, excludeNoteId)
                _duplicateWarnings.value = duplicates
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("NotesViewModel", "checkForDuplicates failed", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadRelatedNotes(noteId: Long) {
        viewModelScope.launch {
            try {
                _relatedNotes.value = repository.findRelatedNotes(noteId)
            } catch (e: Exception) {
                android.util.Log.e("NotesViewModel", "Related notes failed", e)
                _relatedNotes.value = emptyList()
            }
        }
    }

    fun clearRelatedNotes() {
        _relatedNotes.value = emptyList()
    }

    fun clearDuplicateWarnings() {
        _duplicateWarnings.value = emptyList()
    }

    private val _lastSearchWasExact = MutableStateFlow(false)
    val lastSearchWasExact: StateFlow<Boolean> = _lastSearchWasExact.asStateFlow()

    fun exactSearchNotes(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _lastSearchWasExact.value = true
                val notes = repository.exactTextSearch(q)
                _searchResults.value = notes.map { it to 1f }
            } catch (e: Exception) {
                android.util.Log.e("NotesViewModel", "Error exact search", e)
                _searchResults.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun semanticSearchNotes(query: String) {
        val q = query.trim()
        if (q.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _lastSearchWasExact.value = false
                val results = repository.semanticSearch(q)
                _searchResults.value = results
            } catch (e: Exception) {
                android.util.Log.e("NotesViewModel", "Error semantic search", e)
                _searchResults.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
        _lastSearchWasExact.value = false
    }

    private fun noteAppearsInCachedMergeOrGroups(noteId: Long): Boolean {
        val inMerge =
            _mergeSuggestions.value.any { it.note1.id == noteId || it.note2.id == noteId }
        val inGroups = _groupedNotes.value.any { g -> g.any { it.id == noteId } }
        return inMerge || inGroups
    }

    /**
     * Recomputes merge suggestions **and** similar groups, persists to DB, marks all notes analyzed.
     */
    fun recalculateMergeAndGroupsFull() {
        launchExclusiveMergeGroupWork(
            tag = "merge+groups full recompute",
            invalidateMergeSuggestionsDiskLoad = true,
            invalidateGroupedNotesDiskLoad = true
        ) {
            runFullMergeAndGroupsRecomputeAndPersist()
        }
    }

    fun refreshSimilarGroupsOnly() {
        launchExclusiveMergeGroupWork(
            tag = "groups refresh",
            invalidateMergeSuggestionsDiskLoad = false,
            invalidateGroupedNotesDiskLoad = true
        ) {
            _analysisProgress.value = 0f
            val groups = repository.groupSimilarNotes(
                onProgress = { done, total ->
                    _analysisProgress.value = (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                },
                onIncrementalGroups = { g ->
                    withContext(Dispatchers.Main.immediate) {
                        _groupedNotes.value = g
                    }
                },
                incrementalOnnx = false
            )
            withContext(Dispatchers.Main.immediate) {
                _groupedNotes.value = groups
            }
            repository.replaceSimilarGroupCache(groups)
        }
    }

    fun refreshMergeSuggestionsOnly() {
        launchExclusiveMergeGroupWork(
            tag = "merge refresh",
            invalidateMergeSuggestionsDiskLoad = true,
            invalidateGroupedNotesDiskLoad = false
        ) {
            _analysisProgress.value = 0f
            val merge = repository.findMergeSuggestions(
                onProgress = { done, total ->
                    _analysisProgress.value = (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                },
                onIncrementalMerge = { list ->
                    withContext(Dispatchers.Main.immediate) {
                        _mergeSuggestions.value = list
                    }
                },
                incrementalOnnx = false
            )
            withContext(Dispatchers.Main.immediate) {
                _mergeSuggestions.value = merge
            }
            repository.replaceMergeSuggestionCache(merge)
        }
    }

    /**
     * Runs the DB merge and prunes stale merge/group UI + stored cache for removed ids.
     * Does **not** run BERT merge/group analysis — use refresh actions on those screens when wanted.
     */
    fun mergeNotes(note1: Note, note2: Note, customTitle: String? = null) {
        val removed = setOf(note1.id, note2.id)
        viewModelScope.launch {
            mergeGroupAnalysisJob?.cancel()
            mergeSuggestionsUiEpoch.incrementAndGet()
            groupedNotesUiEpoch.incrementAndGet()
            try {
                repository.mergeNotes(note1, note2, customTitle)
                pruneStaleMergeSuggestionsAndGroups(removed)
                _mergeSuggestions.value = repository.loadMergeSuggestionsFromCache()
                _groupedNotes.value = repository.loadSimilarGroupsFromCache()
            } catch (e: Exception) {
                android.util.Log.e("NotesViewModel", "mergeNotes failed", e)
            }
        }
    }

    /** Inserts [DebugSampleNotes.presets] and runs merge/group analysis. Debug UI only in debug builds. */
    fun populateDebugNotes() {
        viewModelScope.launch {
            try {
                for ((title, content) in DebugSampleNotes.presets) {
                    repository.insertNote(
                        Note(
                            title = title,
                            content = content,
                            analyzedForSimilarity = false
                        )
                    )
                }
                recalculateMergeAndGroupsFull()
                android.util.Log.d(
                    "NotesViewModel",
                    "Debug: inserted ${DebugSampleNotes.presets.size} sample notes"
                )
            } catch (e: Exception) {
                android.util.Log.e("NotesViewModel", "populateDebugNotes failed", e)
            }
        }
    }
}
