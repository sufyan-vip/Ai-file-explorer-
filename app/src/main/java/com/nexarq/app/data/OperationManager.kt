package com.nexarq.app.data

import com.nexarq.app.core.OperationProgress
import com.nexarq.app.core.OperationRecord
import com.nexarq.app.core.OperationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/**
 * Central operation queue. Active operations surface live progress; finished
 * operations are persisted as history so users can review what happened.
 */
class OperationManager(private val store: JsonStore) {

    private val _active = MutableStateFlow<List<OperationProgress>>(emptyList())
    val active: StateFlow<List<OperationProgress>> = _active.asStateFlow()

    private val historySerializer = HistoryFile.serializer()

    fun report(progress: OperationProgress) {
        _active.update { list ->
            val without = list.filterNot { it.operationId == progress.operationId }
            if (progress.status == OperationStatus.RUNNING) {
                without + progress
            } else {
                recordFinished(progress)
                without
            }
        }
    }

    private fun recordFinished(progress: OperationProgress) {
        val history = history()
        val record = OperationRecord(
            id = progress.operationId,
            kind = progress.kind,
            label = progress.label,
            status = progress.status,
            error = progress.error,
        )
        val updated = (listOf(record) + history).take(200)
        store.write("operations.json", historySerializer, HistoryFile(updated))
    }

    fun history(): List<OperationRecord> = store.read("operations.json", historySerializer, HistoryFile()).items

    fun clearHistory() = store.write("operations.json", historySerializer, HistoryFile())
}

@Serializable
private data class HistoryFile(val items: List<OperationRecord> = emptyList())
