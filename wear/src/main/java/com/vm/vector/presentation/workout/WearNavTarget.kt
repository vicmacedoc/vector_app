package com.vm.vector.presentation.workout

/**
 * Navigation targets for the Wear workout app.
 */
sealed class WearNavTarget {
    data object List : WearNavTarget()
    /** ENDURANCE and OTHER sessions. */
    data class IntervalSession(val session: SessionItem) : WearNavTarget()
    data class ResistanceSession(val session: SessionItem) : WearNavTarget()
}
