package com.mindforge.app.ml

import android.content.Context
import android.util.Log
import java.text.Normalizer
import java.util.Locale

/**
 * BERT (`bert-base-uncased`-style) WordPiece tokenizer backed by **`vocab.txt`** in assets.
 * Use the same vocabulary the ONNX export was trained with (typically `bert-base-uncased` `vocab.txt`).
 */
class BertWordpieceTokenizer(context: Context) {

    private val vocabToId = HashMap<String, Int>(33792)
    private var padToken: String = padLiteral()
    private var unkToken: String = unkLiteral()
    private var clsToken: String = "[CLS]"
    private var sepToken: String = "[SEP]"

    private val padId: Int get() = vocabToId[padToken] ?: 0
    private val unkId get() = vocabToId[unkToken] ?: UNK_FALLBACK_INDEX
    private val clsId get() = vocabToId[clsToken] ?: CLS_FALLBACK_INDEX
    private val sepId get() = vocabToId[sepToken] ?: SEP_FALLBACK_INDEX

    var isVocabLoaded: Boolean = false
        private set

    private val maxInputsCharsPerWord = 100

    init {
        runCatching {
            context.assets.open(VOCAB_ASSET_NAME).bufferedReader().useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val t = line.trimEnd('\n', '\r')
                    if (t.isNotEmpty()) vocabToId[t] = index
                }
            }
            unkToken = unkLiteral().takeIf { it in vocabToId } ?: unkToken
            padToken = padLiteral().takeIf { it in vocabToId } ?: padToken

            isVocabLoaded = vocabToId.size >= MIN_VOCAB_SIZE
            if (isVocabLoaded) {
                Log.d(TAG, "WordPiece vocab loaded: ${vocabToId.size} tokens ([CLS]==$clsId, [SEP]==$sepId)")
            } else Log.e(TAG, "vocab.txt too small or missing")
        }.onFailure { e ->
            Log.e(TAG, "Failed to load $VOCAB_ASSET_NAME", e)
            isVocabLoaded = false
        }
    }

    private fun wordToId(wp: String): Long = (vocabToId[wp] ?: unkId).toLong()

    fun encodePair(segmentA: String, segmentB: String, maxSeqLength: Int = 128): TokenizedInput {
        val wpA = basicTokenizeAndWordpiece(segmentA)
        val wpB = basicTokenizeAndWordpiece(segmentB)
        val (piecesA, piecesB) = truncatePair(wpA, wpB, maxSeqLength)

        val inputIds = LongArray(maxSeqLength) { padId.toLong() }
        val mask = LongArray(maxSeqLength) { 0L }
        val types = LongArray(maxSeqLength) { 0L }

        var i = 0
        inputIds[i] = clsId.toLong()
        mask[i] = 1L
        types[i] = 0L
        i++

        for (p in piecesA) {
            if (i >= maxSeqLength) break
            inputIds[i] = wordToId(p)
            mask[i] = 1L
            types[i] = 0L
            i++
        }
        inputIds[i] = sepId.toLong()
        mask[i] = 1L
        types[i] = 0L
        i++

        for (p in piecesB) {
            if (i >= maxSeqLength - 1) break
            inputIds[i] = wordToId(p)
            mask[i] = 1L
            types[i] = 1L
            i++
        }
        inputIds[i] = sepId.toLong()
        mask[i] = 1L
        types[i] = 1L

        return TokenizedInput(inputIds, mask, types)
    }

    fun encodeSingle(text: String, maxSeqLength: Int = 128): TokenizedInput {
        val wp = basicTokenizeAndWordpiece(text)
        val maxPieces = (maxSeqLength - 2).coerceAtLeast(0)
        val pieces = wp.take(maxPieces)

        val inputIds = LongArray(maxSeqLength) { padId.toLong() }
        val mask = LongArray(maxSeqLength) { 0L }
        val types = LongArray(maxSeqLength) { 0L }

        var i = 0
        inputIds[i] = clsId.toLong()
        mask[i] = 1L
        i++
        for (p in pieces) {
            if (i >= maxSeqLength - 1) break
            inputIds[i] = wordToId(p)
            mask[i] = 1L
            i++
        }
        inputIds[i] = sepId.toLong()
        mask[i] = 1L
        return TokenizedInput(inputIds, mask, types)
    }

    private fun truncatePair(wpA: List<String>, wpB: List<String>, maxSeqLen: Int): Pair<List<String>, List<String>> {
        val maxPieces = maxSeqLen - 3
        val a = wpA.toMutableList()
        val b = wpB.toMutableList()
        while (a.size + b.size > maxPieces) {
            if (a.size > b.size && a.isNotEmpty()) a.removeAt(a.lastIndex)
            else if (b.isNotEmpty()) b.removeAt(b.lastIndex)
            else break
        }
        return a to b
    }

    private fun basicTokenizeAndWordpiece(text: String): List<String> {
        val stripped = stripAccents(text.lowercase(Locale.US))
        val out = mutableListOf<String>()
        var idx = 0
        while (idx < stripped.length) {
            val c = stripped[idx]
            when {
                c.isWhitespace() -> idx++
                c.isLetterOrDigit() -> {
                    var j = idx + 1
                    while (j < stripped.length && (stripped[j].isLetterOrDigit() || stripped[j] == '-')) j++
                    wordpiece(stripped.substring(idx, j), out)
                    idx = j
                }
                else -> {
                    wordpiece(c.toString(), out)
                    idx++
                }
            }
        }
        return out
    }

    private fun wordpiece(token: String, output: MutableList<String>) {
        if (token.isEmpty()) return
        if (token.length > maxInputsCharsPerWord) {
            output.add(unkToken)
            return
        }
        var start = 0
        while (start < token.length) {
            var end = token.length
            var curPiece: String? = null
            while (start < end) {
                var substr = token.substring(start, end)
                if (start > 0) substr = "##$substr"
                if (substr in vocabToId) {
                    curPiece = substr
                    break
                }
                end--
            }
            if (curPiece == null) {
                output.add(unkToken)
                return
            }
            output.add(curPiece)
            start += curPiece.removePrefix("##").length
        }
    }

    companion object {
        private const val TAG = "BertWordpieceTokenizer"
        const val VOCAB_ASSET_NAME = "vocab.txt"
        private const val MIN_VOCAB_SIZE = 28000
        private const val UNK_FALLBACK_INDEX = 100
        private const val CLS_FALLBACK_INDEX = 101
        private const val SEP_FALLBACK_INDEX = 102

        /** Characters assembled so tooling does not strip BERT vocab tokens as markup. */
        private fun unkLiteral() = buildString { append('<').append('u').append('n').append('k').append('>') }
        private fun padLiteral() = buildString { append('<').append('p').append('a').append('d').append('>') }
    }
}

private fun stripAccents(text: String): String {
    val n = Normalizer.normalize(text, Normalizer.Form.NFD)
    val sb = StringBuilder(n.length)
    for (cp in n.codePoints()) {
        if (Character.getType(cp) != Character.NON_SPACING_MARK.toInt()) {
            sb.appendCodePoint(cp)
        }
    }
    return sb.toString()
}

data class TokenizedInput(
    val inputIds: LongArray,
    val attentionMask: LongArray,
    val tokenTypeIds: LongArray
)
