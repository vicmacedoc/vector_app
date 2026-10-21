package com.vm.core.models

import kotlinx.serialization.Serializable

@Serializable
data class VectorList(
    val id: String = "",
    val name: String,
    /** Aisle / store-route order for supermarket lists. Empty for generic checklists. */
    val sections: List<String> = emptyList(),
    val items: List<VectorItem>
)

@Serializable
data class VectorItem(
    val id: String = "",
    val title: String,
    val isChecked: Boolean = false,
    val quantity: Double? = null,
    val remaining: Double? = null,
    val unit: String? = null,
    val price: Double? = null,
    @Serializable(with = PrioritySerializer::class)
    val priority: String? = null,
    /** Aisle name for supermarket lists (matches an entry in [VectorList.sections]). */
    val category: String? = null,
    /** Position within the aisle for supermarket route order. */
    val order: Int? = null,
    val dueDate: Long? = null
)