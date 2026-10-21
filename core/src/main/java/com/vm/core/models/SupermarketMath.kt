package com.vm.core.models

/** Stock level computed from remaining/quantity. Only the watch still renders it (as a dot color). */
enum class SupermarketUrgency { URGENT, LOW, STOCKED }

object SupermarketMath {
    fun urgency(item: VectorItem): SupermarketUrgency {
        val qty = item.quantity
        val remaining = item.remaining
        if (qty == null || qty <= 0.0 || remaining == null) {
            return if (item.isChecked) SupermarketUrgency.STOCKED else SupermarketUrgency.URGENT
        }
        val ratio = remaining / qty
        return when {
            ratio <= 0.25 -> SupermarketUrgency.URGENT
            ratio <= 0.50 -> SupermarketUrgency.LOW
            else -> SupermarketUrgency.STOCKED
        }
    }

    fun needsBuy(item: VectorItem): Boolean = urgency(item) != SupermarketUrgency.STOCKED

    /**
     * User-defined order. Legacy lists stored `order` per aisle, so colliding values fall back to
     * the aisle route until the first drag assigns a global order.
     */
    fun sortedByOrder(list: VectorList): List<VectorItem> {
        val orders = list.items.mapNotNull { it.order }
        if (orders.size != orders.toSet().size) return sortedForRoute(list)
        val routeIndex = sortedForRoute(list).withIndex().associate { it.value.id to it.index }
        return list.items.sortedWith(
            compareBy<VectorItem> { it.order ?: Int.MAX_VALUE }
                .thenBy { routeIndex[it.id] ?: Int.MAX_VALUE }
        )
    }

    fun sortedForRoute(list: VectorList): List<VectorItem> {
        val sectionIndex = list.sections.withIndex().associate { it.value to it.index }
        return list.items.sortedWith(
            compareBy<VectorItem> { sectionIndex[it.category ?: ""] ?: Int.MAX_VALUE }
                .thenBy { it.order ?: Int.MAX_VALUE }
                .thenBy { it.title.lowercase() }
        )
    }

    fun groupedByAisle(list: VectorList): List<Pair<String, List<VectorItem>>> {
        val sections = list.sections.ifEmpty {
            list.items.mapNotNull { it.category }.distinct()
        }
        val byCat = list.items.groupBy { it.category ?: "Other" }
        val ordered = mutableListOf<Pair<String, List<VectorItem>>>()
        for (s in sections) {
            val items = byCat[s]?.sortedBy { it.order ?: Int.MAX_VALUE }.orEmpty()
            if (items.isNotEmpty()) ordered += s to items
        }
        for ((cat, items) in byCat) {
            if (cat !in sections) {
                ordered += cat to items.sortedBy { it.order ?: Int.MAX_VALUE }
            }
        }
        return ordered
    }
}
