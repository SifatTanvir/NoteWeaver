package com.mindforge.app

import android.app.Application
import com.mindforge.app.data.local.MindforgeDatabase
import com.mindforge.app.data.repository.NoteRepository
import com.mindforge.app.ml.ParaphraseDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MindforgeApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    val database by lazy { MindforgeDatabase.getDatabase(this) }
    val paraphraseDetector by lazy { ParaphraseDetector(this) }
    val repository by lazy {
        NoteRepository(
            database.noteDao(),
            database.noteGroupDao(),
            database.analysisCacheDao(),
            paraphraseDetector
        )
    }
    
    override fun onCreate() {
        super.onCreate()

        // Initialize ML model in background
        applicationScope.launch {
            paraphraseDetector.initialize()
        }
    }
}
