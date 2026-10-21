package com.vm.core.models

import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable

/** Learn tile [fromNodeId] depends on (builds on) Learn tile [toNodeId]. */
@Entity(
    tableName = "learning_link",
    primaryKeys = ["fromNodeId", "toNodeId"],
    indices = [Index(value = ["toNodeId"])]
)
@Serializable
data class LearningLink(
    val fromNodeId: String,
    val toNodeId: String,
    val createdAt: Long = System.currentTimeMillis()
)
