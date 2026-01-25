package com.vm.core.models

import kotlinx.serialization.Serializable

@Serializable
data class VectorList(
    val id: String,
    val name: String,
    val items: List<VectorItem>
)

@Serializable
data class VectorItem(
    val id: String,
    val title: String,
    val isChecked: Boolean = false,
    val quantity: Double? = null,
    val remaining: Double? = null,
    val unit: String? = null,
    val price: Double? = null,
    @Serializable(with = PrioritySerializer::class)
    val priority: String? = null,
    val category: String? = null,
    val dueDate: Long? = null
)