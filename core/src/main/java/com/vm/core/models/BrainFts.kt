package com.vm.core.models

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions

/**
 * Unified FTS index for Brain notes, tasks, and nodes (memory and Learn tiles).
 * Maintained by [com.vm.vector.data.BrainRepository] on write; not a source of truth.
 */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "brain_fts")
data class BrainFts(
    val entityType: String,
    val entityId: String,
    val content: String,
) {
    companion object {
        const val TYPE_NOTE = "NOTE"
        const val TYPE_TASK = "TASK"
        const val TYPE_NODE = "NODE"
    }
}
