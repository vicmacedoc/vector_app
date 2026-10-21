package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Learning state of a Learn tile. The tile itself is a [BrainNode] with section MODEL (name, tags)
 * and [id] equal to that node's id; its sources are the [MemoryNote]s pointing at the node.
 */
@Entity(
    tableName = "learning_tile",
    indices = [Index(value = ["status"])]
)
@Serializable
data class LearningTile(
    @PrimaryKey val id: String,
    val status: LearningTileStatus = LearningTileStatus.BACKLOG,
    /** Last note added/edited or "Studied today" tap; drives the On hold rule. */
    val lastActivityAt: Long = System.currentTimeMillis(),
    val learnedAt: Long? = null,
    /** Index into the review ladder used for [nextReviewAt]; equals the ladder size once mastered. */
    val reviewStep: Int = 0,
    val nextReviewAt: Long? = null,
    /** 0..1 score of the latest quiz. */
    val lastReviewScore: Float? = null,
    val lastReviewedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
