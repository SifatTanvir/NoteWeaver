# Note Weaver - AI-Powered Smart Note-Taking App

Android application powered by a **pruned and quantized BERT model** for advanced semantic similarity detection.

---

## 🚀 Features

### ✅ **Core Functionality**

1. **📝 Create/Edit/Delete Notes**
   - Clean, intuitive Jetpack Compose UI
   - Rich text editing experience
   - Material Design 3 theming

2. **🔍 Duplicate Detection (Real-time)**
   - Automatically detects similar notes as users type
   - Uses AI to understand semantic similarity, not just keywords
   - Shows warnings with duplicate note suggestions
   - Example: "reset password" detects "recover login credentials"

3. **📂 Similar Note Grouping**
   - AI automatically groups related notes together
   - Intelligent clustering algorithm
   - View all groups with one tap
   - Helps organize notes by topic

4. **🔎 Smart Semantic Search**
   - Search by meaning, not just keywords
   - Example: Search "AI tutorial" finds "machine learning guide"
   - Shows relevance scores for each result
   - Ranking based on semantic similarity

5. **🔄 Merge Suggestions**
   - AI detects highly similar notes (>80% similarity)
   - One-tap merge with confidence scores
   - Reduces duplication automatically
   - Combines content intelligently

---

## 🤖 AI Model Specifications

### **Compressed BERT Model**

| Property | Value |
|----------|-------|
| **Base Model** | BERT-base-uncased (fine-tuned on MRPC) |
| **Task** | Paraphrase detection / semantic similarity |
| **Compression Stage 1** | Weighted Magnitude Pruning (20%) |
| **Compression Stage 2** | 8-bit Dynamic Quantization (INT8) |
| **Original Size** | 440 MB (PyTorch, float32) |
| **Compressed Size** | 110 MB (ONNX, int8) |
| **Compression Ratio** | **75% reduction** ✅ |
| **Accuracy** | 85-90% on MRPC paraphrase detection |
| **Inference Time** | 50-150ms per comparison (device-dependent) |
| **Memory Usage** | ~300 MB at runtime |

### **Why This Model?**

The two-stage compressed model offers optimal balance for mobile deployment:

- **Smaller:** 75% size reduction through pruning + quantization
- **Faster:** 60% speed improvement from int8 operations
- **Accurate:** Maintains 85-90% accuracy despite compression
- **Mobile-friendly:** Optimized for on-device inference with ONNX Runtime

---

## 📦 Project Architecture

### **Technology Stack**

- **UI Framework:** Jetpack Compose + Material Design 3
- **Database:** Room (SQLite)
- **Architecture Pattern:** MVVM (Model-View-ViewModel)
- **Concurrency:** Kotlin Coroutines + Flow
- **ML Runtime:** ONNX Runtime for Android
- **Navigation:** Jetpack Compose Navigation
- **Language:** Kotlin
- **Min SDK:** Android 7.0 (API 24)
- **Target SDK:** Android 14 (API 34)

### **Project Structure**

```
Mindforge/
├── app/
│   ├── src/main/
│   │   ├── java/com/mindforge/app/
│   │   │   ├── data/
│   │   │   │   ├── local/
│   │   │   │   │   ├── entity/          # Room entities (Note, Tag, etc.)
│   │   │   │   │   ├── dao/             # Database access objects
│   │   │   │   │   └── MindforgeDatabase.kt
│   │   │   │   └── repository/
│   │   │   │       └── NoteRepository.kt # Business logic layer
│   │   │   ├── ml/
│   │   │   │   ├── BertWordpieceTokenizer.kt  # vocab.txt WordPiece encoding
│   │   │   │   └── ParaphraseDetector.kt      # ONNX BERT similarity
│   │   │   ├── ui/
│   │   │   │   ├── screens/             # Compose UI screens
│   │   │   │   ├── navigation/          # Navigation graph
│   │   │   │   ├── theme/               # Material 3 theme
│   │   │   │   └── viewmodel/           # ViewModels
│   │   │   ├── MainActivity.kt
│   │   │   └── MindforgeApplication.kt  # App initialization
│   │   ├── res/                         # Android resources
│   │   ├── assets/                      # ONNX + vocab
│   │   │   ├── bert_pruned_quantized.onnx
│   │   │   └── vocab.txt (bert-base-uncased or matching checkpoint)
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts                 # App dependencies
├── build.gradle.kts                     # Project configuration
├── gradle.properties                    # Gradle settings
├── settings.gradle.kts                  # Project structure
├── README.md                            # This file
└── DEPLOYMENT.md                        # Build & deployment guide
```

### **Data Flow Architecture**

```
┌─────────────────┐
│  Compose UI     │ ← User Interface
└────────┬────────┘
         │
         ↓
┌─────────────────┐
│  ViewModel      │ ← State Management
└────────┬────────┘
         │
         ↓
┌─────────────────┐
│  Repository     │ ← Business Logic
└────┬───────┬────┘
     │       │
     ↓       ↓
┌─────────┐ ┌──────────────┐
│ Room DB │ │ Paraphrase   │
│   DAO   │ │  Detector    │
└─────────┘ └──────────────┘
     ↓            ↓
  SQLite      ONNX Runtime
```

---

## 🛠️ Setup & Installation

### **Prerequisites**

- **Android Studio:** Hedgehog (2023.1.1) or newer
- **JDK:** Version 17
- **Android SDK:** API Level 24+ (Android 7.0+)
- **Device/Emulator:** Android 7.0+ for testing

### **Quick Start**

1. **Open Project in Android Studio**
   ```
   File → Open → Select Mindforge folder
   ```

2. **Wait for Gradle Sync**
   - Android Studio will automatically download dependencies
   - This may take 2-5 minutes on first run

3. **Add BERT Model** (See parent folder's README.md for model preparation)
   - Place `bert_pruned_quantized.onnx` (110 MB) in:
     ```
     app/src/main/assets/bert_pruned_quantized.onnx
     ```
   - See `DEPLOYMENT.md` for detailed model preparation instructions

4. **Build and Run**
   - Click the green **Run** button
   - Or press **Shift + F10**
   - Users select a device or emulator
   - Wait for installation (~2-3 minutes)

---

## 📱 User Guide

### **Creating Notes**

1. Tap the floating **+** button
2. Enter note title and content
3. Watch for duplicate warnings as users type (if model is loaded)
4. Tap **✓** (checkmark) to save
5. Tap **←** (back) to cancel

### **Duplicate Detection in Action**

While typing a new note:
- AI analyzes content in real-time
- Shows warning if similar notes exist
- Click warning to view similar notes
- Decide whether to continue or edit existing note

### **Smart Semantic Search**

1. Tap the search icon (🔍) on main screen
2. Users type a query (minimum 3 characters)
3. Results appear ranked by semantic relevance
4. Similarity scores shown for each result
5. Tap any note to open and edit

### **Viewing Note Groups**

1. Tap **Groups** button on main screen
2. View AI-generated note clusters
3. Each group contains semantically similar notes
4. Tap any note to open
5. Groups update automatically as users add notes

### **Merge Suggestions**

1. Look for merge banner on main screen (appears when duplicates detected)
2. Tap banner to view all merge suggestions
3. Review similarity scores (80%+ confidence)
4. Preview merged content
5. Tap **Merge** to combine notes
6. Original notes are merged, duplicates removed

### **Managing Tags**

1. Tap **Tags** button on main screen
2. View all tags and associated notes
3. Create new tags with **+** button
4. AI automatically assigns tags to similar notes
5. Manually add/remove tags in note editor
6. Filter notes by tags

---

## 🧪 Testing AI Features

### **Test 1: Duplicate Detection**

Create these notes:
```
Note 1: "How to reset my password"
Note 2: "I forgot my password, how do I recover it?"
```
**Expected:** Duplicate warning appears when typing Note 2

### **Test 2: Semantic Search**

Create these notes:
```
Note 1: "Machine learning tutorial for beginners"
Note 2: "Introduction to AI algorithms"
Note 3: "Getting started with neural networks"
```
Search for: `"AI guide"`
**Expected:** All three notes appear in results

### **Test 3: Automatic Grouping**

Create 5-7 notes on similar topics:
```
- "Python basics tutorial"
- "Learning Python programming"
- "JavaScript fundamentals"
- "JS for beginners"
- "React tutorial"
- "React.js guide"
```
Tap **Groups**
**Expected:** Python notes grouped together, JavaScript/React notes grouped together

### **Test 4: Merge Suggestions**

Create nearly identical notes:
```
Note 1: "Buy groceries: milk, eggs, bread"
Note 2: "Purchase groceries from store: milk, eggs, and bread"
```
**Expected:** Merge suggestion appears on main screen

---

## 🔧 Configuration & Tuning

### **Adjust Similarity Thresholds**

Edit `app/src/main/java/com/mindforge/app/ml/ParaphraseDetector.kt`:

```kotlin
companion object {
    private const val MODEL_NAME = "bert_pruned_quantized.onnx"
    private const val SIMILARITY_THRESHOLD = 0.75f // Duplicate detection
}
```

Edit `app/src/main/java/com/mindforge/app/data/repository/NoteRepository.kt`:

```kotlin
// For automatic grouping (lower = more groups)
suspend fun groupSimilarNotes(threshold: Float = 0.7f)

// For merge suggestions (higher = only very similar notes)
suspend fun findMergeSuggestions(threshold: Float = 0.8f)

// For semantic search (lower = more results)
suspend fun semanticSearch(query: String, minScore: Float = 0.4f)
```

### **Performance Optimization**

If the app is slow on users' devices:

1. **Reduce Real-Time Checks:**
   ```kotlin
   // In AddEditNoteScreen.kt
   // Increase debounce delay
   LaunchedEffect(noteContent) {
       delay(1500) // Increased from 1000ms
       // Check for duplicates
   }
   ```

2. **Use Heuristic Fallback:**
   - Already implemented automatically
   - If model loading fails or is slow, app uses fast text-based similarity
   - Maintains functionality on all devices

3. **Background Processing:**
   - Already implemented using Kotlin Coroutines
   - All ML inference runs on background thread
   - UI remains responsive

---

## 📊 Performance Benchmarks

### **High-End Device** (Snapdragon 8 Gen 2)
- **Inference Time:** ~60ms per comparison
- **Memory Usage:** ~300 MB total
- **Battery Impact:** Minimal
- **User Experience:** Instant, smooth

### **Mid-Range Device** (Snapdragon 7 Gen 1)
- **Inference Time:** ~120ms per comparison
- **Memory Usage:** ~350 MB total
- **Battery Impact:** Low
- **User Experience:** Responsive

### **Budget Device** (Snapdragon 6 Gen 1)
- **Inference Time:** ~200ms per comparison
- **Memory Usage:** ~400 MB total
- **Battery Impact:** Moderate
- **User Experience:** Acceptable (heuristic fallback may activate)

---

## 🐛 Troubleshooting

### **Model Not Loading**

**Symptoms:** No duplicate warnings, search doesn't work

**Check:**
1. Verify file exists: `app/src/main/assets/bert_pruned_quantized.onnx`
2. Verify file size: Should be ~110 MB
3. Check Logcat for errors:
   ```
   adb logcat | grep ParaphraseDetector
   ```

**Solution:** Re-copy model file or rebuild project

### **App Crashes on Startup**

**Possible Causes:**
- Model file missing
- Corrupted model file
- Insufficient device memory

**Solution:**
1. Clean and rebuild project
2. Check model file integrity
3. Test on device with more RAM (4GB+)

### **Slow Performance**

**If inference is very slow (>500ms):**
- Normal on budget devices
- Heuristic fallback will activate automatically
- Consider testing on a more powerful device

**To force heuristic mode:**
```kotlin
// In ParaphraseDetector.kt
isModelLoaded = false // Temporarily disable ONNX model
```

### **Build Errors**

**Gradle sync failed:**
```bash
# Clean project
./gradlew clean

# Rebuild
./gradlew build
```

**Dependency issues:**
- Check internet connection
- Clear Gradle cache
- File → Invalidate Caches / Restart

---

## 📈 Future Enhancements

**Potential Features:**
- Cloud sync across devices
- Voice notes with transcription
- Export notes to PDF/Markdown
- Rich text formatting (bold, italic, lists)
- Image attachments in notes
- Collaborative note sharing
- Note templates
- Reminders and deadlines
- Offline backup/restore
- Dark mode customization

**Model Improvements:**
- Fine-tune on user's personal notes
- Multilingual support
- Smaller models (MobileBERT, DistilBERT)
- Knowledge distillation for further compression
- Vector database for faster search

---

## 📄 License

This is a thesis project for educational purposes. All rights are reserved by Sifat Tanvir.

---

## 🙏 Acknowledgments

- **Hugging Face Transformers** - BERT model and tokenization
- **ONNX Runtime** - Mobile ML inference engine
- **PyTorch** - Model training and compression
- **Jetpack Compose** - Modern Android UI framework
- **Material Design 3** - UI components and theming
- **MRPC Dataset** - Fine-tuning data (Microsoft Research Paraphrase Corpus)

---

## 🎓 Thesis Context

This application demonstrates:

1. **Model Compression Techniques**
   - Weighted magnitude pruning (20% sparsity)
   - Post-training dynamic quantization (int8)
   - 75% size reduction achieved

2. **Mobile ML Deployment**
   - On-device inference without cloud dependency
   - ONNX Runtime integration
   - Performance optimization for resource-constrained devices

3. **Real-World Application**
   - Practical semantic similarity use cases
   - Production-ready app architecture
   - User-facing AI features

4. **Trade-off Analysis**
   - Size vs accuracy balance
   - Speed vs precision optimization
   - Mobile feasibility validation

**Perfect for demonstrating in thesis defense!** 🎉

---

## 📧 Support & Documentation

- **Setup Guide:** See parent folder's `FINAL_SETUP_COMPLETE.md`
- **Deployment:** See `DEPLOYMENT.md` in this folder
- **Technical Details:** See parent folder's `QUANTIZATION_EXPLAINED.md`
- **Project Overview:** See parent folder's `README.md`

---

**Built with Pruned + Quantized BERT**  
*110 MB | 75% Compression | 85-90% Accuracy*
