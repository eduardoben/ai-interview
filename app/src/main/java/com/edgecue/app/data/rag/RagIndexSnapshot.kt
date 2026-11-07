package com.edgecue.app.data.rag

import com.edgecue.app.data.rag.db.RagChunkEntity
import kotlin.math.ln

/**
 * Pre-computed, immutable in-memory lexical index snapshot.
 * Caches term frequencies, document frequencies, Robertson-Spärck Jones IDF,
 * bigrams, and average document length for zero-allocation retrieval.
 */
data class IndexedChunk(
    val entity: RagChunkEntity,
    val tokens: List<String>,
    val termFreqs: Map<String, Int>,
    val bigrams: Set<String>,
    val docLength: Int
)

class RagIndexSnapshot(
    val chunks: List<IndexedChunk>,
    val avgdl: Double,
    val idf: Map<String, Double>
) {
    val totalChunks: Int = chunks.size

    val cvChunks: List<IndexedChunk> = chunks.filter { it.entity.sourceType == "CURRICULUM" }
    val jdChunks: List<IndexedChunk> = chunks.filter { it.entity.sourceType == "JOB_DESCRIPTION" }

    companion object {
        val EMPTY = RagIndexSnapshot(emptyList(), 0.0, emptyMap())

        fun build(entities: List<RagChunkEntity>, chunker: RagDocumentChunker): RagIndexSnapshot {
            if (entities.isEmpty()) return EMPTY

            val indexedList = ArrayList<IndexedChunk>(entities.size)
            val docFreqs = HashMap<String, Int>()
            var totalLength = 0L

            for (entity in entities) {
                val tokens = if (entity.tokensCsv.isNotBlank()) {
                    entity.tokensCsv.split(',').filter { it.isNotBlank() }
                } else {
                    chunker.tokenizeText(entity.content)
                }

                val tfMap = HashMap<String, Int>()
                for (token in tokens) {
                    tfMap[token] = (tfMap[token] ?: 0) + 1
                }

                for (token in tfMap.keys) {
                    docFreqs[token] = (docFreqs[token] ?: 0) + 1
                }

                val bigramSet = HashSet<String>()
                for (i in 0 until tokens.size - 1) {
                    bigramSet.add("${tokens[i]}_${tokens[i + 1]}")
                }

                val docLength = tokens.size
                totalLength += docLength

                indexedList.add(
                    IndexedChunk(
                        entity = entity,
                        tokens = tokens,
                        termFreqs = tfMap,
                        bigrams = bigramSet,
                        docLength = docLength
                    )
                )
            }

            val n = entities.size.toDouble()
            val avgdl = if (entities.isNotEmpty()) totalLength.toDouble() / n else 0.0

            // Robertson-Spärck Jones IDF
            val idfMap = HashMap<String, Double>(docFreqs.size)
            for ((term, df) in docFreqs) {
                val score = ln(1.0 + (n - df + 0.5) / (df + 0.5))
                idfMap[term] = score.coerceAtLeast(0.05)
            }

            return RagIndexSnapshot(indexedList, avgdl, idfMap)
        }
    }
}
