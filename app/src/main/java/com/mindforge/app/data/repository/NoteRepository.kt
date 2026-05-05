package com.mindforge.app.data.repository

import com.mindforge.app.data.local.dao.AnalysisCacheDao
import com.mindforge.app.data.local.dao.NoteDao
import com.mindforge.app.data.local.dao.NoteGroupDao
import com.mindforge.app.data.local.entity.MergeSuggestionCacheEntity
import com.mindforge.app.data.local.entity.Note
import com.mindforge.app.data.local.entity.NoteGroup
import com.mindforge.app.data.local.entity.SimilarGroupCacheEntity
import com.mindforge.app.data.local.entity.SimilarGroupMemberEntity
import com.mindforge.app.ml.ParaphraseDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Reports completed units and total units (e.g. comparison count). */
typealias AnalysisProgressCallback = (done: Int, total: Int) -> Unit

/** Sorted merge list updates while [findMergeSuggestions] runs. */
typealias IncrementalMergeCallback = suspend (List<MergeSuggestion>) -> Unit

/** Group list updates while [groupSimilarNotes] runs. */
typealias IncrementalGroupsCallback = suspend (List<List<Note>>) -> Unit

/**
 * Paraphrase signals for merge heuristics from a single note pair.
 * [maxFourWindows] matches the former `bestParaphraseScoreForNotePair` (four windows).
 * [titleSimilarity] is reused from the trimmed-title window (no duplicate ONNX).
 * [contentLeadRaw512Similarity] matches legacy `content.take(512)` vs `take(512)` (not in the four-window max).
 */
data class MergePairParaphraseBreakdown(
    val maxFourWindows: Float,
    val titleSimilarity: Float,
    val contentLeadRaw512Similarity: Float
)

class NoteRepository(
    private val noteDao: NoteDao,
    private val noteGroupDao: NoteGroupDao,
    private val analysisCacheDao: AnalysisCacheDao,
    private val paraphraseDetector: ParaphraseDetector
) {

    fun getAllNotes(): Flow<List<Note>> = noteDao.getAllNotes()

    suspend fun getNoteById(id: Long): Note? = noteDao.getNoteById(id)

    suspend fun insertNote(note: Note): Long {
        android.util.Log.d("NoteRepository", "insertNote: '${note.title}'")
        return noteDao.insertNote(note)
    }

    suspend fun updateNote(note: Note) {
        noteDao.updateNote(note)
    }

    suspend fun deleteNote(note: Note) {
        noteDao.deleteNote(note)
        analysisCacheDao.pruneForDeletedNote(note.id)
    }

    suspend fun loadMergeSuggestionsFromCache(): List<MergeSuggestion> {
        val rows = analysisCacheDao.getAllMergeRows()
        val out = mutableListOf<MergeSuggestion>()
        for (r in rows) {
            val n1 = noteDao.getNoteById(r.note1Id) ?: continue
            val n2 = noteDao.getNoteById(r.note2Id) ?: continue
            out.add(MergeSuggestion(n1, n2, r.score))
        }
        return out.sortedByDescending { it.similarityScore }
    }

    suspend fun loadSimilarGroupsFromCache(): List<List<Note>> {
        val ids = analysisCacheDao.getAllGroupIds()
        val result = mutableListOf<List<Note>>()
        for (gid in ids) {
            val noteIds = analysisCacheDao.getMemberNoteIds(gid)
            val notes = noteIds.mapNotNull { noteDao.getNoteById(it) }
            if (notes.size > 1) result.add(notes)
        }
        return result
    }

    private fun filterGroupsToExistingIds(groups: List<List<Note>>, validIds: Set<Long>): List<List<Note>> =
        groups.map { g -> g.filter { it.id in validIds } }.filter { it.size > 1 }

    private fun idsShareCachedGroup(id1: Long, id2: Long, cachedIdSets: List<Set<Long>>): Boolean {
        if (id1 == id2) return true
        for (s in cachedIdSets) {
            if (id1 in s && id2 in s) return true
        }
        return false
    }

    suspend fun replaceMergeSuggestionCache(suggestions: List<MergeSuggestion>) {
        analysisCacheDao.clearMergeCache()
        if (suggestions.isEmpty()) return
        val rows = suggestions.map { s ->
            val (a, b) = normalizeNotePair(s.note1.id, s.note2.id)
            MergeSuggestionCacheEntity(note1Id = a, note2Id = b, score = s.similarityScore)
        }
        analysisCacheDao.insertMergeRows(rows)
    }

    suspend fun replaceSimilarGroupCache(groups: List<List<Note>>) {
        analysisCacheDao.clearGroupMembers()
        analysisCacheDao.clearGroupCache()
        for (group in groups) {
            val gid = analysisCacheDao.insertGroup(SimilarGroupCacheEntity())
            val members = group.map { SimilarGroupMemberEntity(groupId = gid, noteId = it.id) }
            analysisCacheDao.insertMembers(members)
        }
    }

    /**
     * Persists merge + group caches atomically — prevents one table cleared while the other keeps
     * stale rows, or clearing merge then failing before groups commit.
     */
    suspend fun persistMergeAndGroupAnalysis(
        merge: List<MergeSuggestion>,
        groups: List<List<Note>>,
    ) {
        val mergeRows =
            merge.map { s ->
                val (a, b) = normalizeNotePair(s.note1.id, s.note2.id)
                MergeSuggestionCacheEntity(note1Id = a, note2Id = b, score = s.similarityScore)
            }
        val groupsNoteIds = groups.map { g -> g.map { it.id } }
        analysisCacheDao.replaceMergeAndGroupCachesTransactional(mergeRows, groupsNoteIds)
    }

    suspend fun markAllNotesAnalyzedForSimilarity() {
        noteDao.markAllNotesAnalyzedForSimilarity()
    }

    suspend fun findDuplicateNotes(
        draftTitle: String,
        draftContent: String,
        excludeNoteId: Long? = null,
        threshold: Float = 0.56f
    ): List<Note> {
        return try {
            if (draftContent.isBlank() && draftTitle.isBlank()) return emptyList()
            val draft = draftNote(draftTitle, draftContent)

            val allNotes = noteDao.getAllNotes().first()
            val duplicates = mutableListOf<Note>()

            for (note in allNotes) {
                if (excludeNoteId != null && note.id == excludeNoteId) continue
                try {
                    val score = bestParaphraseScoreForNotePair(draft, note)
                    if (score >= threshold) {
                        duplicates.add(note)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NoteRepository", "Error checking duplicate", e)
                }
            }
            duplicates
        } catch (e: Exception) {
            android.util.Log.e("NoteRepository", "Error finding duplicates", e)
            emptyList()
        }
    }

    private fun draftNote(title: String, content: String) = Note(
        id = 0L,
        title = title.trim().ifBlank { "(untitled)" },
        content = content.trim(),
        analyzedForSimilarity = false
    )

    suspend fun exactTextSearch(query: String): List<Note> {
        val q = query.trim().replace("%", "").replace("_", "")
        if (q.isEmpty()) return emptyList()
        return try {
            noteDao.searchNotes(q).first()
        } catch (e: Exception) {
            android.util.Log.e("NoteRepository", "Error exact search", e)
            emptyList()
        }
    }

    suspend fun semanticSearch(query: String, minScore: Float = 0.12f): List<Pair<Note, Float>> {
        return try {
            if (query.isBlank()) return emptyList()

            val allNotes = noteDao.getAllNotes().first()
            val results = mutableListOf<Pair<Note, Float>>()

            for (note in allNotes) {
                try {
                    val score = bestParaphraseScoreForQuery(query, note)
                    if (score >= minScore) {
                        results.add(note to score)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NoteRepository", "Error searching note", e)
                }
            }
            results.sortedByDescending { it.second }
        } catch (e: Exception) {
            android.util.Log.e("NoteRepository", "Error performing semantic search", e)
            emptyList()
        }
    }

    suspend fun findRelatedNotes(noteId: Long, topK: Int = 6, minScore: Float = 0.12f): List<Pair<Note, Float>> {
        val target = noteDao.getNoteById(noteId) ?: return emptyList()
        val allNotes = noteDao.getAllNotes().first()
        val scored = mutableListOf<Pair<Note, Float>>()
        for (other in allNotes) {
            if (other.id == noteId) continue
            try {
                val s = bestParaphraseScoreForNotePair(target, other)
                if (s >= minScore) scored.add(other to s)
            } catch (_: Exception) {
            }
        }
        return scored.sortedByDescending { it.second }.take(topK)
    }

    private suspend fun bestParaphraseScoreForQuery(query: String, note: Note): Float {
        val title = note.title.trim()
        val body = note.content.trim()
        val lead = "$title. ${body}".trim().take(512)
        val headBody = body.take(512)
        val sTitle = if (title.isNotEmpty()) paraphraseDetector.calculateSimilarity(query, title) else 0f
        val sLead = if (lead.isNotEmpty()) paraphraseDetector.calculateSimilarity(query, lead) else 0f
        val sBody = if (headBody.isNotEmpty()) paraphraseDetector.calculateSimilarity(query, headBody) else 0f
        return maxOf(sTitle, sLead, sBody)
    }

    /**
     * @param incrementalOnnx If true (default): when every note has [Note.analyzedForSimilarity],
     * returns persisted groups only (no ONNX). When some notes are unanalyzed, ONNX runs only on
     * pairs that include at least one unanalyzed note; pairs of two analyzed notes reuse prior
     * group membership from cache (no ONNX). If false, every eligible pair runs ONNX (refresh-all).
     */
    suspend fun groupSimilarNotes(
        threshold: Float = 0.38f,
        onProgress: AnalysisProgressCallback? = null,
        onIncrementalGroups: IncrementalGroupsCallback? = null,
        incrementalOnnx: Boolean = true
    ): List<List<Note>> {
        return try {
            val allNotes = noteDao.getAllNotes().first()

            if (allNotes.size <= 1) {
                onIncrementalGroups?.invoke(emptyList())
                return emptyList()
            }

            val validIds = allNotes.map { it.id }.toSet()
            val unanalyzedIds = allNotes.filter { !it.analyzedForSimilarity }.map { it.id }.toSet()

            if (incrementalOnnx && unanalyzedIds.isEmpty()) {
                val cached = filterGroupsToExistingIds(loadSimilarGroupsFromCache(), validIds)
                onIncrementalGroups?.invoke(cached.map { it.toList() })
                return cached
            }

            val baseCacheFiltered =
                filterGroupsToExistingIds(loadSimilarGroupsFromCache(), validIds)
            val cachedIdSets = baseCacheFiltered.map { g -> g.map { it.id }.toSet() }

            val n = allNotes.size
            val totalPairs = maxOf(1, n * (n - 1) / 2)
            var donePairs = 0

            val groups = mutableListOf<MutableList<Note>>()
            val processed = mutableSetOf<Long>()

            for (note in allNotes) {
                if (note.id in processed) continue

                val group = mutableListOf(note)
                processed.add(note.id)

                for (otherNote in allNotes) {
                    if (otherNote.id in processed) continue

                    try {
                        currentCoroutineContext().ensureActive()
                        val score: Float = when {
                            !incrementalOnnx -> bestParaphraseScoreForNotePair(note, otherNote)
                            !note.analyzedForSimilarity || !otherNote.analyzedForSimilarity ->
                                bestParaphraseScoreForNotePair(note, otherNote)
                            idsShareCachedGroup(note.id, otherNote.id, cachedIdSets) -> threshold
                            else -> 0f
                        }
                        donePairs++
                        onProgress?.invoke(donePairs, totalPairs)
                        if (score >= threshold) {
                            group.add(otherNote)
                            processed.add(otherNote.id)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e("NoteRepository", "Error comparing notes", e)
                    }
                }

                if (group.size > 1) {
                    groups.add(group)
                    onIncrementalGroups?.invoke(groups.map { g -> g.toList() })
                }
            }

            onIncrementalGroups?.invoke(groups.map { it.toList() })

            groups
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("NoteRepository", "Error grouping notes", e)
            throw e
        }
    }

    private data class FourWindowParaphraseScores(
        val sTitle: Float,
        val sLead: Float,
        val sBody: Float,
        val sConcat: Float
    ) {
        fun maxScore(): Float = maxOf(sTitle, sLead, sBody, sConcat)
    }

    /** Exactly four ONNX calls (matches legacy `bestParaphraseScoreForNotePair`). */
    private suspend fun fourWindowParaphraseScores(note1: Note, note2: Note): FourWindowParaphraseScores {
        val t1 = note1.title.trim()
        val t2 = note2.title.trim()
        val lead1 = "$t1. ${note1.content}".trim().take(512)
        val lead2 = "$t2. ${note2.content}".trim().take(512)
        val b1 = note1.content.trim().take(512)
        val b2 = note2.content.trim().take(512)
        val concat1 = t1 + ". $b1"
        val concat2 = t2 + ". $b2"

        val sTitle =
            if (t1.isNotBlank() && t2.isNotBlank()) paraphraseDetector.calculateSimilarity(t1, t2) else 0f
        val sLead =
            if (lead1.isNotBlank() && lead2.isNotBlank()) paraphraseDetector.calculateSimilarity(
                lead1,
                lead2
            )
            else 0f
        val sBody =
            if (b1.isNotBlank() && b2.isNotBlank()) paraphraseDetector.calculateSimilarity(b1, b2) else 0f
        val sConcat =
            if (concat1.isNotBlank() && concat2.isNotBlank())
                paraphraseDetector.calculateSimilarity(concat1, concat2) else 0f

        return FourWindowParaphraseScores(sTitle, sLead, sBody, sConcat)
    }

    /**
     * Merge path: reuse four-window scores for [maxFourWindows] and [titleSimilarity];
     * adds one ONNX on raw `content.take(512)` (legacy merge gate; not part of four-window max).
     */
    private suspend fun mergeParaphraseBreakdownForPair(note1: Note, note2: Note): MergePairParaphraseBreakdown {
        val fw = fourWindowParaphraseScores(note1, note2)
        val raw1 = note1.content.take(512)
        val raw2 = note2.content.take(512)
        val sContentRaw =
            if (raw1.isNotBlank() && raw2.isNotBlank()) {
                paraphraseDetector.calculateSimilarity(raw1, raw2)
            } else {
                0f
            }
        return MergePairParaphraseBreakdown(
            maxFourWindows = fw.maxScore(),
            titleSimilarity = fw.sTitle,
            contentLeadRaw512Similarity = sContentRaw
        )
    }

    private suspend fun bestParaphraseScoreForNotePair(note1: Note, note2: Note): Float =
        fourWindowParaphraseScores(note1, note2).maxScore()

    /**
     * @param incrementalOnnx If true, ONNX only runs on pairs touching a note with
     * [Note.analyzedForSimilarity] == false; pairs of two analyzed notes come from persisted cache.
     * If every note is analyzed and the cache is empty, no ONNX runs (use refresh with false).
     * If false (manual "refresh all"), every eligible pair runs ONNX — no cache base.
     *
     * [onIncrementalMerge] receives the merged list sorted by score (after cache base loads, after each hit).
     */
    suspend fun findMergeSuggestions(
        threshold: Float = 0.40f,
        onProgress: AnalysisProgressCallback? = null,
        onIncrementalMerge: IncrementalMergeCallback? = null,
        incrementalOnnx: Boolean = true
    ): List<MergeSuggestion> {
        return try {
            val allNotes = noteDao.getAllNotes().first()

            android.util.Log.d(
                "NoteRepository",
                "merge suggestions: ${allNotes.size} notes incrementalOnnx=$incrementalOnnx"
            )

            if (allNotes.size < 2) {
                onIncrementalMerge?.invoke(emptyList())
                return emptyList()
            }

            val suggestions = mutableListOf<MergeSuggestion>()
            val n = allNotes.size

            val unanalyzedIds = allNotes.filter { !it.analyzedForSimilarity }.map { it.id }.toSet()

            if (incrementalOnnx) {
                val baseFromCache =
                    if (unanalyzedIds.isEmpty()) {
                        loadMergeSuggestionsFromCache().filter { s ->
                            noteDao.getNoteById(s.note1.id) != null && noteDao.getNoteById(s.note2.id) != null
                        }
                    } else {
                        loadMergeSuggestionsFromCache().filter { suggestion ->
                            val a = suggestion.note1.id
                            val b = suggestion.note2.id
                            noteDao.getNoteById(a) != null &&
                                noteDao.getNoteById(b) != null &&
                                a !in unanalyzedIds &&
                                b !in unanalyzedIds
                        }
                    }
                suggestions.addAll(baseFromCache)

                onIncrementalMerge?.invoke(suggestions.sortedByDescending { it.similarityScore })

                if (unanalyzedIds.isEmpty() && baseFromCache.isNotEmpty()) {
                    return suggestions.sortedByDescending { it.similarityScore }
                }
            } else {
                onIncrementalMerge?.invoke(emptyList())
            }

            fun needsOnnxPair(note1: Note, note2: Note): Boolean =
                !incrementalOnnx ||
                    !note1.analyzedForSimilarity ||
                    !note2.analyzedForSimilarity

            fun passesCheapFilters(note1: Note, note2: Note): Boolean {
                val minContentLength = minOf(note1.content.length, note2.content.length)
                val minWordCount = minOf(
                    note1.content.split(Regex("\\s+")).size,
                    note2.content.split(Regex("\\s+")).size
                )
                return minContentLength >= 28 && minWordCount >= 5
            }

            var totalOnnxPairs = 0
            for (i in allNotes.indices) {
                for (j in i + 1 until n) {
                    if (!needsOnnxPair(allNotes[i], allNotes[j])) continue
                    if (!passesCheapFilters(allNotes[i], allNotes[j])) continue
                    totalOnnxPairs++
                }
            }

            var onnxDone = 0

            if (totalOnnxPairs == 0 && incrementalOnnx) {
                onIncrementalMerge?.invoke(suggestions.sortedByDescending { it.similarityScore })
                return suggestions.sortedByDescending { it.similarityScore }
            }

            for (i in allNotes.indices) {
                for (j in i + 1 until n) {
                    val note1 = allNotes[i]
                    val note2 = allNotes[j]
                    if (!needsOnnxPair(note1, note2)) continue

                    try {
                        if (!passesCheapFilters(note1, note2)) continue

                        currentCoroutineContext().ensureActive()
                        onnxDone++
                        onProgress?.invoke(onnxDone, maxOf(1, totalOnnxPairs))

                        val breakdown = mergeParaphraseBreakdownForPair(note1, note2)
                        val pairScore = breakdown.maxFourWindows
                        val titleSimilarity = breakdown.titleSimilarity
                        val contentLeadSim = breakdown.contentLeadRaw512Similarity

                        val meetsThreshold = pairScore >= threshold &&
                            titleSimilarity >= 0.12f &&
                            contentLeadSim >= 0.28f

                        val displayScore = maxOf(
                            pairScore,
                            titleSimilarity * 0.35f + contentLeadSim * 0.65f
                        )

                        if (meetsThreshold) {
                            suggestions.add(
                                MergeSuggestion(
                                    note1 = note1,
                                    note2 = note2,
                                    similarityScore = displayScore.coerceIn(0f, 1f)
                                )
                            )
                            onIncrementalMerge?.invoke(
                                suggestions.sortedByDescending { it.similarityScore }
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e("NoteRepository", "Error comparing notes for merge", e)
                    }
                }
            }

            suggestions.sortedByDescending { it.similarityScore }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("NoteRepository", "Error finding merge suggestions", e)
            throw e
        }
    }

    suspend fun mergeNotes(note1: Note, note2: Note, customTitle: String? = null): Long {
        val mergedNote = Note(
            title = customTitle ?: if (note1.title.length >= note2.title.length) note1.title else note2.title,
            content = "${note1.content}\n\n---\n\n${note2.content}",
            createdAt = minOf(note1.createdAt, note2.createdAt),
            updatedAt = System.currentTimeMillis(),
            analyzedForSimilarity = false
        )

        val mergedId = noteDao.insertNote(mergedNote)
        deleteNote(note1)
        deleteNote(note2)
        return mergedId
    }

    fun getAllGroups(): Flow<List<NoteGroup>> = noteGroupDao.getAllGroups()

    suspend fun insertGroup(group: NoteGroup): Long = noteGroupDao.insertGroup(group)

    suspend fun updateGroup(group: NoteGroup) = noteGroupDao.updateGroup(group)

    suspend fun deleteGroup(group: NoteGroup) = noteGroupDao.deleteGroup(group)

    suspend fun assignNoteToGroup(noteId: Long, groupId: Long) {
        val note = noteDao.getNoteById(noteId)
        note?.let {
            noteDao.updateNote(it.copy(groupId = groupId))
        }
    }

    private fun normalizeNotePair(id1: Long, id2: Long): Pair<Long, Long> =
        if (id1 < id2) id1 to id2 else id2 to id1
}

data class MergeSuggestion(
    val note1: Note,
    val note2: Note,
    val similarityScore: Float
)
