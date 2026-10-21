package com.vm.vector.audio

import android.os.Bundle
import androidx.media3.session.SessionCommand

object BrainPlayerCommands {
    const val SEEK_BACK_15 = "com.vm.vector.brain.SEEK_BACK_15"
    const val SEEK_FORWARD_30 = "com.vm.vector.brain.SEEK_FORWARD_30"
    const val SET_SPEED = "com.vm.vector.brain.SET_SPEED"
    const val ADD_BOOKMARK = "com.vm.vector.brain.ADD_BOOKMARK"
    const val DELETE_BOOKMARK = "com.vm.vector.brain.DELETE_BOOKMARK"
    const val SAVE_CHECKPOINT = "com.vm.vector.brain.SAVE_CHECKPOINT"
    const val CLOSE = "com.vm.vector.brain.CLOSE"

    const val ARG_SPEED = "speed"
    const val ARG_LABEL = "label"
    const val ARG_POSITION_MS = "position_ms"
    const val ARG_BOOKMARK_ID = "bookmark_id"

    val COMMAND_SEEK_BACK_15 = SessionCommand(SEEK_BACK_15, Bundle.EMPTY)
    val COMMAND_SEEK_FORWARD_30 = SessionCommand(SEEK_FORWARD_30, Bundle.EMPTY)
    val COMMAND_SAVE_CHECKPOINT = SessionCommand(SAVE_CHECKPOINT, Bundle.EMPTY)
    val COMMAND_CLOSE = SessionCommand(CLOSE, Bundle.EMPTY)

    fun argsSetSpeed(speed: Float): Bundle =
        Bundle().apply { putFloat(ARG_SPEED, speed) }

    fun argsAddBookmark(label: String, positionMs: Long): Bundle =
        Bundle().apply {
            putString(ARG_LABEL, label)
            putLong(ARG_POSITION_MS, positionMs)
        }

    fun argsDeleteBookmark(id: String): Bundle =
        Bundle().apply { putString(ARG_BOOKMARK_ID, id) }

    val COMMAND_SET_SPEED = SessionCommand(SET_SPEED, Bundle.EMPTY)
    val COMMAND_ADD_BOOKMARK = SessionCommand(ADD_BOOKMARK, Bundle.EMPTY)
    val COMMAND_DELETE_BOOKMARK = SessionCommand(DELETE_BOOKMARK, Bundle.EMPTY)
}
