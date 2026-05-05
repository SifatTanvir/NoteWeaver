package com.mindforge.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.nio.LongBuffer
import java.util.Locale
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** ONNX BERT + WordPiece vocab (assets/vocab.txt). CLS-cosine if hidden tensors exist else pair-softmax logits. */
class ParaphraseDetector(private val context: Context) {
    private var tokenizer: BertWordpieceTokenizer? = null
    private var session: OrtSession? = null
    private var ortEnvironment: OrtEnvironment? = null
    private var sessionLoaded = false
    private enum class Inference { LEX_ONLY, SOFTMAX_PAIR, CLS_EMBED }
    private var inferenceKind = Inference.LEX_ONLY

    companion object {
        private const val TAG = "ParaphraseDetector"
        private const val MODEL = "bert_pruned_quantized.onnx"
        private const val SEQ_LEN = 128
        private const val CLIP = 512
        private const val DUP_TRIM = 0.75f
        private const val LEXICAL_WEIGHT = 0.32f
        private const val CLS_DIM_MIN = 64
    }

    fun isOnnxModelLoaded(): Boolean = sessionLoaded

    suspend fun initialize() = withContext(Dispatchers.IO) {
        tokenizer = BertWordpieceTokenizer(context)
        val tz = tokenizer
        if (tz == null || !tz.isVocabLoaded) {
            Log.e(TAG, "vocab.txt missing — lexical only")
            closeSessionOnly()
            inferenceKind = Inference.LEX_ONLY
            sessionLoaded = false
            return@withContext
        }
        try {
            ortEnvironment = OrtEnvironment.getEnvironment()
            val blob = context.assets.open(MODEL).readBytes()
            Log.d(TAG, "ONNX MiB=" + (blob.size / (1024 * 1024)).toString())
            val opts = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            session = ortEnvironment!!.createSession(blob, opts)
            sessionLoaded = true
            val probeTok = tz.encodePair("hello alpha sentence", "hello beta sentence", SEQ_LEN)
            inferenceKind = session!!.runSessionOnnx(ortEnvironment!!, probeTok).use { probeOutputs(it) }
            Log.i(TAG, "mode=" + inferenceKind.name)
        } catch (_: OutOfMemoryError) {
            Log.e(TAG, "OOM ONNX")
            closeSessionOnly(); sessionLoaded = false; inferenceKind = Inference.LEX_ONLY
        } catch (e: Exception) {
            Log.e(TAG, "onnx", e)
            closeSessionOnly(); sessionLoaded = false; inferenceKind = Inference.LEX_ONLY
        }
    }

    private fun probeOutputs(results: OrtSession.Result): Inference {
        var hid = false
        var logitTensor = false
        results.forEach { e ->
            if (e.value !is OnnxTensor) return@forEach
            val onnxT = e.value as OnnxTensor
            val rawOnnxValue = onnxT.value
            val nk = e.key?.lowercase(Locale.US) ?: ""
            if (nk.contains("hidden")) hid = true
            matrixTokenRows(rawOnnxValue)?.let { rows ->
                if (rows.size in 2..SEQ_LEN + 16 && rows[0].size >= CLS_DIM_MIN) hid = true
            }
            if (flattenParaphraseLogits(rawOnnxValue) != null) logitTensor = true
        }
        return when { hid -> Inference.CLS_EMBED; logitTensor -> Inference.SOFTMAX_PAIR; else -> Inference.SOFTMAX_PAIR }
    }

    suspend fun calculateSimilarity(tA: String, tB: String): Float = withContext(Dispatchers.Default) {
        val aTrim = tA.trim()
        val bTrim = tB.trim()
        if (aTrim.isEmpty() || bTrim.isEmpty()) return@withContext 0f
        val lex = lexicalMix(aTrim, bTrim)
        val tz = tokenizer
        val sess = session
        val env = ortEnvironment
        if (!sessionLoaded || tz == null || sess == null || env == null || !tz.isVocabLoaded || inferenceKind == Inference.LEX_ONLY)
            return@withContext lex
        val neuralOnnx = kotlin.runCatching {
            when (inferenceKind) {
                Inference.CLS_EMBED -> cosineFromCls(sess, env, tz, aTrim, bTrim)
                Inference.SOFTMAX_PAIR -> paraphraseFromPair(sess, env, tz, aTrim, bTrim)
                Inference.LEX_ONLY -> lex
            }
        }.getOrElse { err -> Log.e(TAG, "infer", err); lex }
        max(neuralOnnx, lex * LEXICAL_WEIGHT)
    }

    suspend fun areDuplicates(x: String, y: String): Boolean = calculateSimilarity(x, y) >= DUP_TRIM

    private fun OrtSession.runSessionOnnx(environmentBlock: OrtEnvironment, coded: TokenizedInput): OrtSession.Result {
        val ioTensors = mutableListOf<OnnxTensor>()
        fun wrapLongBuffer(rowSlice: LongArray): OnnxTensor {
            val onnxLong = OnnxTensor.createTensor(environmentBlock, LongBuffer.wrap(rowSlice.copyOf(SEQ_LEN)), longArrayOf(1L, SEQ_LEN.toLong()))
            ioTensors += onnxLong
            return onnxLong
        }
        val feedMap = mutableMapOf("input_ids" to wrapLongBuffer(coded.inputIds), "attention_mask" to wrapLongBuffer(coded.attentionMask))
        if ("token_type_ids" in inputNames) feedMap["token_type_ids"] = wrapLongBuffer(coded.tokenTypeIds)
        return try { run(feedMap) } finally { ioTensors.forEach { kt -> kotlin.runCatching { kt.close() } } }
    }

    private fun paraphraseFromPair(sess: OrtSession, envBlock: OrtEnvironment, tz: BertWordpieceTokenizer, a: String, b: String): Float {
        val codedPair = tz.encodePair(a.take(CLIP), b.take(CLIP), SEQ_LEN)
        return sess.runSessionOnnx(envBlock, codedPair).use { resObj -> softmaxPickIndex1(resObj) }
    }

    private fun softmaxPickIndex1(resolved: OrtSession.Result): Float {
        val logits1dRow = logitsFirstFromResult(resolved)
        val soft = softmaxVector(logits1dRow)
        val v = when { soft.size >= 2 -> soft[1]; soft.isEmpty() -> 0f; else -> soft.maxOrNull() ?: 0f }
        return v.coerceIn(0f, 1f)
    }

    private fun logitsFirstFromResult(resolved: OrtSession.Result): FloatArray {
        val keysOrder = sequenceOf("logits", "output_logits", "prediction_scores")
        keysOrder.forEach { wantKey -> resolved.forEach { ent ->
            if (!ent.key.equals(wantKey, ignoreCase = true)) return@forEach
            flattenParaphraseLogits((ent.value as OnnxTensor).value)?.let { return it }
        } }
        resolved.forEach { ent -> flattenParaphraseLogits((ent.value as? OnnxTensor)?.value)?.let { return it } }
        throw IllegalStateException("logits")
    }

    private fun cosineFromCls(sess: OrtSession, envBlock: OrtEnvironment, tz: BertWordpieceTokenizer, a: String, b: String): Float {
        val ea = pooledNormCls(sess, envBlock, tz, a)
        val eb = pooledNormCls(sess, envBlock, tz, b)
        if (ea == null || eb == null) return lexicalMix(a, b)
        val span = min(ea.size, eb.size)
        var dot = 0f
        for (i in 0 until span) dot += ea[i] * eb[i]
        val cos = dot.coerceIn(-1f, 1f)
        return ((cos + 1f) * 0.5f).coerceIn(0f, 1f)
    }

    private fun pooledNormCls(sess: OrtSession, envBlock: OrtEnvironment, tz: BertWordpieceTokenizer, textBody: String): FloatArray? {
        val singleTok = tz.encodeSingle(textBody.take(CLIP), SEQ_LEN)
        var outVec: FloatArray? = null
        sess.runSessionOnnx(envBlock, singleTok).use { r -> matrixTokenRows(pickHiddenValue(r))?.getOrNull(0)?.clone()?.let { row -> l2NormalizeVector(row); outVec = row } }
        return outVec
    }

    private fun pickHiddenValue(r: OrtSession.Result): Any? {
        val namesPrefer = sequenceOf("last_hidden_state", "hidden_states")
        namesPrefer.forEach { nk -> r.forEach { ent ->
            if (ent.key.equals(nk, ignoreCase = true)) return (ent.value as OnnxTensor).value
        } }
        r.forEach { ent ->
            val ot = ent.value as? OnnxTensor ?: return@forEach
            if (matrixTokenRows(ot.value) != null) return ot.value
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun matrixTokenRows(rawOnnx: Any?): Array<FloatArray>? = try {
        val batch = rawOnnx as Array<*>
        val seq = batch[0] as Array<FloatArray>
        Array(seq.size) { i -> seq[i].clone() }
    } catch (_: Throwable) { null }

    private fun flattenParaphraseLogits(v: Any?): FloatArray? = when (v) {
        is FloatArray -> if (v.size in 2..12) v.clone() else null
        is Array<*> -> { val z = v.getOrNull(0) as? FloatArray ?: return null; if (z.size in 2..12) z.clone() else null }
        else -> null
    }

    private fun softmaxVector(v: FloatArray): FloatArray {
        val mx = v.maxOrNull() ?: 0f
        val ex = FloatArray(v.size) { i -> exp((v[i] - mx).toDouble()).toFloat() }
        val s = ex.sum().coerceAtLeast(1e-9f)
        for (i in ex.indices) ex[i] /= s
        return ex
    }

    private fun l2NormalizeVector(vec: FloatArray) {
        var s = 0.0; for (x in vec) s += x * x.toDouble()
        val inv = sqrt(s).toFloat().coerceAtLeast(1e-9f)
        for (i in vec.indices) vec[i] /= inv
    }

    private fun lexicalMix(sa: String, sb: String): Float {
        val w1 = sa.lowercase(Locale.US).split(Regex("\\W+")).filter { it.length > 1 }.toSet()
        val w2 = sb.lowercase(Locale.US).split(Regex("\\W+")).filter { it.length > 1 }.toSet()
        val jac = when {
            w1.isEmpty() || w2.isEmpty() -> 0f
            else -> { val ix = w1.intersect(w2).size; val uni = w1.union(w2).size; if (uni > 0) ix.toFloat() / uni else 0f }
        }
        val tri = trigramDiceScore(sa, sb)
        val minW = min(w1.size.coerceAtLeast(triApprox(sa)), w2.size.coerceAtLeast(triApprox(sb)))
        val pen = when { minW <= 2 -> 0.55f; minW <= 4 -> 0.72f; minW <= 6 -> 0.88f; else -> 1f }
        val lr = min(sa.length, sb.length).toFloat() / max(sa.length, sb.length).toFloat()
        val blend = max(jac * 0.85f + lr * 0.15f, tri * 0.92f + lr * 0.08f)
        return (blend * pen).coerceIn(0f, 1f)
    }

    private fun triApprox(txt: String): Int = alphanumericStrip(txt).length.coerceAtLeast(3) / 4

    private fun alphanumericStrip(s: String) = buildString(s.length) { for (ch in s.lowercase(Locale.US)) if (ch.isLetterOrDigit()) append(ch) }

    private fun trigramDiceScore(sa: String, sb: String): Float {
        fun grams(t: String): Set<String> {
            val c = alphanumericStrip(t)
            if (c.length < 3) return emptySet()
            val g = HashSet<String>(c.length - 2)
            for (i in 0..c.length - 3) g.add(c.substring(i, i + 3))
            return g
        }
        val A = grams(sa); val B = grams(sb)
        if (A.isEmpty() || B.isEmpty()) return 0f
        var inter = 0; val Sm = if (A.size <= B.size) A else B; val La = if (A.size <= B.size) B else A
        for (g in Sm) if (La.contains(g)) inter++
        return 2f * inter / (A.size + B.size)
    }

    private fun closeSessionOnly() { kotlin.runCatching { session?.close() }; session = null; sessionLoaded = false }

    fun cleanup() { closeSessionOnly(); kotlin.runCatching { ortEnvironment?.close() }; ortEnvironment = null; tokenizer = null; inferenceKind = Inference.LEX_ONLY }

}