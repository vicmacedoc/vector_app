package com.vm.vector.data.backup

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields

/**
 * Grandfather-father-son retention: newest snapshot of each of the last [days] days,
 * of each of the last [weeks] ISO weeks, and of each of the last [months] months.
 */
object BackupRetention {
    const val DAYS = 7
    const val WEEKS = 4
    const val MONTHS = 12

    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
    private val DATE_IN_NAME = Regex("""(\d{4}-\d{2}-\d{2})(?:_(\d{4}))?""")

    fun stamp(time: LocalDateTime): String = time.format(STAMP)

    /** Parses the first `yyyy-MM-dd[_HHmm]` in a backup file name. */
    fun parseStamp(name: String): LocalDateTime? {
        val match = DATE_IN_NAME.find(name) ?: return null
        val date = runCatching { LocalDate.parse(match.groupValues[1]) }.getOrNull() ?: return null
        val hhmm = match.groupValues[2]
        return if (hhmm.length == 4) {
            date.atTime(hhmm.take(2).toInt().coerceIn(0, 23), hhmm.drop(2).toInt().coerceIn(0, 59))
        } else {
            date.atStartOfDay()
        }
    }

    /** Returns the subset of [snapshots] to keep. */
    fun <T> keep(
        snapshots: List<T>,
        timeOf: (T) -> LocalDateTime,
        days: Int = DAYS,
        weeks: Int = WEEKS,
        months: Int = MONTHS,
    ): Set<T> {
        val newestFirst = snapshots.sortedByDescending(timeOf)
        val keep = LinkedHashSet<T>()
        fun keepNewestPer(limit: Int, bucket: (LocalDateTime) -> Any) {
            val seen = LinkedHashSet<Any>()
            for (snapshot in newestFirst) {
                val key = bucket(timeOf(snapshot))
                if (key in seen) continue
                if (seen.size >= limit) break
                seen += key
                keep += snapshot
            }
        }
        keepNewestPer(days) { it.toLocalDate() }
        keepNewestPer(weeks) { it.get(IsoFields.WEEK_BASED_YEAR) to it.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) }
        keepNewestPer(months) { it.year to it.monthValue }
        return keep
    }
}
