package com.vm.vector

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.InvalidationTracker
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vm.core.models.VectorDatabase
import com.vm.core.models.ActivityCompletion
import com.vm.core.models.ActivityLabel
import com.vm.core.models.BrainTag
import com.vm.core.models.FoodEntry
import com.vm.core.models.WorkoutSet
import com.vm.vector.data.BrainFileStore
import com.vm.vector.data.BrainRepository
import com.vm.vector.data.BrainSyncWorker
import com.vm.vector.data.DailySyncWorker
import com.vm.vector.data.DatabaseBackupManager
import com.vm.vector.data.DatabaseBackupScheduler
import com.vm.vector.data.DriveService
import com.vm.vector.data.FoodRepository
import com.vm.vector.data.GoogleDriveAuthManager
import com.vm.vector.data.PreferenceManager
import com.vm.vector.data.PresetStorage
import com.vm.vector.data.WearActivityStore
import com.vm.vector.data.WearMessageReceiver
import com.vm.vector.data.WorkoutRepository
import com.vm.vector.data.sync.BrainImportGuard
import com.vm.vector.data.sync.DailyImportGuard
import com.vm.vector.motion.MotionDigestWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

data class WearActivityUpdate(
    val date: String,
    val activityId: String,
    val completion: ActivityCompletion,
)

data class WearSupermarketUpdate(
    val index: Int,
    val title: String,
    val isChecked: Boolean,
)

/**
 * v22: Self gains affect scales, feeling tags, social context, intention review
 * and structured journal prompts.
 */
private val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE self_entry ADD COLUMN energy INTEGER")
        db.execSQL("ALTER TABLE self_entry ADD COLUMN stress INTEGER")
        db.execSQL("ALTER TABLE self_entry ADD COLUMN emotionTags TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("ALTER TABLE self_entry ADD COLUMN socialContext TEXT")
        db.execSQL("ALTER TABLE self_entry ADD COLUMN intention TEXT")
        db.execSQL("ALTER TABLE self_entry ADD COLUMN intentionMet INTEGER")
        db.execSQL("ALTER TABLE self_entry ADD COLUMN highlight TEXT")
        db.execSQL("ALTER TABLE self_entry ADD COLUMN lowlight TEXT")
        db.execSQL("ALTER TABLE self_entry ADD COLUMN gratitude TEXT")
    }
}

/**
 * v23: Self settles on twelve 1–5 scales; feeling tags, social context and the
 * intention review drop out. Rebuilds the table because SQLite cannot drop columns.
 */
private val MIGRATION_22_23 = object : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS self_entry_new (
                date TEXT NOT NULL PRIMARY KEY,
                mood INTEGER,
                energy INTEGER,
                stress INTEGER,
                cognition INTEGER,
                motivation INTEGER,
                physical INTEGER,
                sleepQuality INTEGER,
                social INTEGER,
                selfEsteem INTEGER,
                actions INTEGER,
                external INTEGER,
                future INTEGER,
                intention TEXT,
                highlight TEXT,
                lowlight TEXT,
                gratitude TEXT,
                journalText TEXT,
                journalAudioDriveFileId TEXT,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO self_entry_new (
                date, mood, energy, stress, intention, highlight, lowlight, gratitude,
                journalText, journalAudioDriveFileId, timestamp
            )
            SELECT
                date, mood, energy, stress, intention, highlight, lowlight, gratitude,
                journalText, journalAudioDriveFileId, timestamp
            FROM self_entry
            """.trimIndent()
        )
        db.execSQL("DROP TABLE self_entry")
        db.execSQL("ALTER TABLE self_entry_new RENAME TO self_entry")
    }
}

/**
 * v24: Internal libraries for reusable exercise sessions, meals, and named routine presets.
 */
private val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS exercise_library (
                id TEXT NOT NULL PRIMARY KEY,
                type TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                payloadJson TEXT NOT NULL,
                typeKey TEXT NOT NULL,
                nameKey TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_exercise_library_type_name ON exercise_library(typeKey, nameKey)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS meal_library (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                kcal REAL NOT NULL,
                protein REAL NOT NULL,
                carbs REAL NOT NULL,
                fats REAL NOT NULL,
                amount REAL NOT NULL,
                unit TEXT NOT NULL,
                nameKey TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_meal_library_name ON meal_library(nameKey)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS routine_presets (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                payloadJson TEXT NOT NULL,
                nameKey TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_routine_presets_name ON routine_presets(nameKey)"
        )
    }
}

/**
 * v26: Brain (Memory / Model / Motion) tables for notes, learning tiles, assets, audio progress, tasks.
 */
private val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS brain_node (
                id TEXT NOT NULL PRIMARY KEY,
                parentId TEXT,
                section TEXT NOT NULL,
                name TEXT NOT NULL,
                colorArgb INTEGER NOT NULL,
                sortOrder INTEGER NOT NULL,
                driveFolderId TEXT,
                isSystem INTEGER NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_brain_node_parentId ON brain_node (parentId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_brain_node_section ON brain_node (section)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS brain_asset (
                id TEXT NOT NULL PRIMARY KEY,
                localRelativePath TEXT NOT NULL,
                mimeType TEXT NOT NULL,
                sizeBytes INTEGER NOT NULL,
                driveFileId TEXT,
                syncState TEXT NOT NULL,
                fileName TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_brain_asset_syncState ON brain_asset (syncState)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memory_note (
                id TEXT NOT NULL PRIMARY KEY,
                nodeId TEXT,
                kind TEXT NOT NULL,
                title TEXT NOT NULL,
                body TEXT NOT NULL,
                assetId TEXT,
                url TEXT,
                tags TEXT NOT NULL,
                pinned INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_note_nodeId ON memory_note (nodeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_memory_note_kind ON memory_note (kind)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS learning_tile (
                id TEXT NOT NULL PRIMARY KEY,
                nodeId TEXT,
                title TEXT NOT NULL,
                summary TEXT NOT NULL,
                status TEXT NOT NULL,
                tags TEXT NOT NULL,
                sortOrder INTEGER NOT NULL,
                nextReviewAt INTEGER,
                reviewIntervalDays INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_learning_tile_nodeId ON learning_tile (nodeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_learning_tile_status ON learning_tile (status)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS learning_source (
                id TEXT NOT NULL PRIMARY KEY,
                tileId TEXT NOT NULL,
                kind TEXT NOT NULL,
                title TEXT NOT NULL,
                url TEXT,
                assetId TEXT,
                isSystem INTEGER NOT NULL,
                sortOrder INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_learning_source_tileId ON learning_source (tileId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_learning_source_kind ON learning_source (kind)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS audio_progress (
                assetId TEXT NOT NULL PRIMARY KEY,
                positionMs INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                speed REAL NOT NULL,
                completed INTEGER NOT NULL,
                bookmarks TEXT NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS motion_task (
                id TEXT NOT NULL PRIMARY KEY,
                nodeId TEXT,
                title TEXT NOT NULL,
                notes TEXT NOT NULL,
                priority TEXT NOT NULL,
                dueAt INTEGER,
                leadMinutes INTEGER NOT NULL,
                recurrence TEXT NOT NULL,
                status TEXT NOT NULL,
                completedAt INTEGER,
                sortOrder INTEGER NOT NULL,
                linkedTileId TEXT,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_motion_task_nodeId ON motion_task (nodeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_motion_task_status ON motion_task (status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_motion_task_dueAt ON motion_task (dueAt)")
    }
}

/**
 * v25: Sleep moves to a night-keyed sleep_entry table. Each row is one episode
 * (bedtime that night + wake the following morning). daily_home keeps body + plan only.
 */
private val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sleep_entry (
                date TEXT NOT NULL PRIMARY KEY,
                plannedBedtime TEXT,
                plannedWakeUp TEXT,
                actualBedtime TEXT,
                actualWakeUp TEXT,
                fellAsleepAt TEXT,
                deepMinutes INTEGER,
                lightMinutes INTEGER,
                remMinutes INTEGER,
                awakeMinutes INTEGER,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO sleep_entry (date, plannedBedtime, plannedWakeUp, actualBedtime, actualWakeUp, timestamp)
            SELECT b.date, b.sleepStart, w.sleepEnd, b.actualSleepStart, w.actualSleepEnd, b.timestamp
            FROM daily_home b
            LEFT JOIN daily_home w ON w.date = date(b.date, '+1 day')
            WHERE b.sleepStart IS NOT NULL
               OR b.actualSleepStart IS NOT NULL
               OR w.sleepEnd IS NOT NULL
               OR w.actualSleepEnd IS NOT NULL
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS daily_home_new (
                date TEXT NOT NULL PRIMARY KEY,
                weightKg REAL,
                bodyFatPercent REAL,
                dailyPlanText TEXT,
                dailyPlanAudioPath TEXT,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO daily_home_new (date, weightKg, bodyFatPercent, dailyPlanText, dailyPlanAudioPath, timestamp)
            SELECT date, weightKg, bodyFatPercent, dailyPlanText, dailyPlanAudioPath, timestamp
            FROM daily_home
            """.trimIndent()
        )
        db.execSQL("DROP TABLE daily_home")
        db.execSQL("ALTER TABLE daily_home_new RENAME TO daily_home")
    }
}

/**
 * v27: Brain FTS index for fast full-text search across notes, tiles, sources, tasks, folders.
 */
private val MIGRATION_26_27 = object : Migration(26, 27) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE VIRTUAL TABLE IF NOT EXISTS brain_fts USING fts4(
                entityType TEXT NOT NULL,
                entityId TEXT NOT NULL,
                content TEXT NOT NULL,
                tokenize=unicode61
            )
            """.trimIndent()
        )
    }
}

/**
 * Inserts Sleep plus [ActivityLabel.DEFAULT_LABELS], skipping any whose id or name
 * (case-insensitive) already exists so user-made labels are never duplicated.
 */
private fun seedDefaultActivityLabels(db: SupportSQLiteDatabase) {
    val labels = listOf(
        ActivityLabel(
            id = ActivityLabel.SLEEP_LABEL_ID,
            name = ActivityLabel.SLEEP_LABEL_NAME,
            colorArgb = ActivityLabel.SLEEP_COLOR_ARGB,
            isSystem = true,
            createdAt = 0L,
        )
    ) + ActivityLabel.DEFAULT_LABELS
    labels.forEach { label ->
        db.execSQL(
            """
            INSERT INTO activity_labels (id, name, colorArgb, isSystem, createdAt)
            SELECT ?, ?, ?, ?, ?
            WHERE NOT EXISTS (
                SELECT 1 FROM activity_labels WHERE id = ? OR LOWER(name) = LOWER(?)
            )
            """.trimIndent(),
            arrayOf<Any>(
                label.id, label.name, label.colorArgb, if (label.isSystem) 1 else 0, label.createdAt,
                label.id, label.name,
            )
        )
    }
}

/** v28: Seeds the default routine labels once; deleting one later is permanent. */
private val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        seedDefaultActivityLabels(db)
    }
}

/** v29: User description on a recording, kept apart from the transcript body. */
private val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE brain_asset ADD COLUMN description TEXT NOT NULL DEFAULT ''")
    }
}

/** Memory defaults for migrations before v37, when `brain_tag` had no `section` column. */
private fun seedDefaultBrainTags(db: SupportSQLiteDatabase) {
    BrainTag.DEFAULT_TAGS.forEach { tag ->
        db.execSQL(
            """
            INSERT INTO brain_tag (id, name, createdAt)
            SELECT ?, ?, ?
            WHERE NOT EXISTS (
                SELECT 1 FROM brain_tag WHERE id = ? OR LOWER(name) = LOWER(?)
            )
            """.trimIndent(),
            arrayOf<Any>(tag.id, tag.name, tag.createdAt, tag.id, tag.name)
        )
    }
}

/** Seeds tags into their section's library (schema v37+). */
private fun seedSectionTags(db: SupportSQLiteDatabase, tags: List<BrainTag>) {
    tags.forEach { tag ->
        db.execSQL(
            """
            INSERT INTO brain_tag (id, name, createdAt, section)
            SELECT ?, ?, ?, ?
            WHERE NOT EXISTS (
                SELECT 1 FROM brain_tag WHERE id = ? OR (section = ? AND LOWER(name) = LOWER(?))
            )
            """.trimIndent(),
            arrayOf<Any>(tag.id, tag.name, tag.createdAt, tag.section.name, tag.id, tag.section.name, tag.name)
        )
    }
}

private fun seedAllBrainTags(db: SupportSQLiteDatabase) {
    seedSectionTags(db, BrainTag.DEFAULT_TAGS)
    seedSectionTags(db, BrainTag.MODEL_DEFAULT_TAGS)
}

/** v30: Brain tag library, seeded with learning fields; deleting one later is permanent. */
private val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS brain_tag (
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        seedDefaultBrainTags(db)
    }
}

/**
 * v31: Memory folders become flat memory tiles with tags. Nested folders are lifted to the
 * top level, and the tag library swaps retired learning-field defaults for general memory tags.
 */
private val MIGRATION_30_31 = object : Migration(30, 31) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE brain_node ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("UPDATE brain_node SET parentId = NULL WHERE section = 'MEMORY'")
        val keep = BrainTag.DEFAULT_TAGS.map { it.id }
        db.execSQL(
            "DELETE FROM brain_tag WHERE id LIKE '${BrainTag.DEFAULT_ID_PREFIX}%' " +
                "AND id NOT IN (${keep.joinToString { "?" }})",
            keep.toTypedArray(),
        )
        seedDefaultBrainTags(db)
    }
}

/** v32: Memory text sources get a short description, kept apart from the markdown body. */
private val MIGRATION_31_32 = object : Migration(31, 32) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE memory_note ADD COLUMN description TEXT NOT NULL DEFAULT ''")
    }
}

/** v33: Exercise template v2 (supersets, drop sets, endurance intervals, exercise totals). */
private val MIGRATION_32_33 = object : Migration(32, 33) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "sport TEXT",
            "stroke TEXT",
            "setType TEXT NOT NULL DEFAULT 'NORMAL'",
            "dropNumber INTEGER NOT NULL DEFAULT 0",
            "unitCadence TEXT NOT NULL DEFAULT 'rpm'",
            "targetZone INTEGER",
            "targetRpe REAL",
            "targetCadence INTEGER",
            "totalDuration REAL",
            "unitTotalDuration TEXT NOT NULL DEFAULT 'min'",
            "actualCadence INTEGER",
            "actualTotalDuration INTEGER",
        ).forEach { column ->
            db.execSQL("ALTER TABLE workout_sets ADD COLUMN $column")
        }
    }
}

/** v34: Unsaved Calendar edits per category/day (orange squares on Home). */
private val MIGRATION_33_34 = object : Migration(33, 34) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS calendar_drafts (
                category TEXT NOT NULL,
                date TEXT NOT NULL,
                payloadJson TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(category, date)
            )
            """.trimIndent()
        )
    }
}

/** v35: Routine library of single activities (replaces named bundles in `routine_presets`). */
private val MIGRATION_34_35 = object : Migration(34, 35) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS activity_library (
                id TEXT NOT NULL,
                title TEXT NOT NULL,
                startTime TEXT NOT NULL,
                endTime TEXT NOT NULL,
                labelName TEXT NOT NULL,
                type TEXT NOT NULL,
                description TEXT NOT NULL,
                nameKey TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_activity_library_name ON activity_library (nameKey)"
        )
    }
}

/**
 * v36: Learn tiles become MODEL brain nodes whose sources are memory notes. `learning_tile` keeps only
 * learning state, `learning_source` rows move into `memory_note` (text bodies are backfilled from
 * their files by [BrainRepository.backfillLearnTextBodies]), and `learning_link` stores dependencies.
 */
private val MIGRATION_35_36 = object : Migration(35, 36) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT OR IGNORE INTO brain_node
                (id, parentId, section, name, colorArgb, sortOrder, driveFolderId, isSystem, tags, createdAt)
            SELECT id, NULL, 'MODEL', title, ${0xFF001F3F.toInt()}, sortOrder, NULL, 0, tags, createdAt
            FROM learning_tile
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO memory_note
                (id, nodeId, kind, title, description, body, assetId, url, tags, pinned, createdAt, updatedAt)
            SELECT 'note_summary_' || id, id, 'TEXT', 'Summary', '', summary, NULL, NULL, '[]', 1, createdAt, updatedAt
            FROM learning_tile
            WHERE TRIM(summary) <> ''
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO memory_note
                (id, nodeId, kind, title, description, body, assetId, url, tags, pinned, createdAt, updatedAt)
            SELECT id, tileId,
                CASE kind
                    WHEN 'VIDEO_LINK' THEN 'LINK'
                    WHEN 'PODCAST_LINK' THEN 'LINK'
                    WHEN 'VIDEO_FILE' THEN 'VIDEO'
                    ELSE kind
                END,
                title, '', '', assetId, url, '[]', isSystem, createdAt, updatedAt
            FROM learning_source
            WHERE tileId IN (SELECT id FROM learning_tile)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS learning_tile_new (
                id TEXT NOT NULL,
                status TEXT NOT NULL,
                lastActivityAt INTEGER NOT NULL,
                learnedAt INTEGER,
                reviewStep INTEGER NOT NULL,
                nextReviewAt INTEGER,
                lastReviewScore REAL,
                lastReviewedAt INTEGER,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO learning_tile_new
                (id, status, lastActivityAt, learnedAt, reviewStep, nextReviewAt,
                 lastReviewScore, lastReviewedAt, createdAt, updatedAt)
            SELECT id, status, updatedAt,
                CASE WHEN status = 'LEARNED' THEN updatedAt ELSE NULL END,
                0, nextReviewAt, NULL, NULL, createdAt, updatedAt
            FROM learning_tile
            """.trimIndent()
        )
        db.execSQL("DROP TABLE learning_tile")
        db.execSQL("ALTER TABLE learning_tile_new RENAME TO learning_tile")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_learning_tile_status ON learning_tile (status)")
        db.execSQL("DROP TABLE IF EXISTS learning_source")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS learning_link (
                fromNodeId TEXT NOT NULL,
                toNodeId TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                PRIMARY KEY(fromNodeId, toNodeId)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_learning_link_toNodeId ON learning_link (toNodeId)")
        db.execSQL("DELETE FROM brain_fts WHERE entityType IN ('TILE', 'SOURCE')")
    }
}

/** v37: separate tag libraries for Memory and Learn tiles; Learn gets knowledge-field defaults. */
private val MIGRATION_36_37 = object : Migration(36, 37) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE brain_tag ADD COLUMN section TEXT NOT NULL DEFAULT 'MEMORY'")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_brain_tag_section ON brain_tag (section)")
        seedSectionTags(db, BrainTag.MODEL_DEFAULT_TAGS)
    }
}

/**
 * v38: Motion lists. Priorities P1–P4 become HIGH/MEDIUM/LOW/NONE, items get points, numeric
 * targets and recurrence links, the `motion_score_event` ledger is added, and open tasks without a
 * list move to a "To-do" list (unfiled tasks now mean Inbox captures).
 */
private val MIGRATION_37_38 = object : Migration(37, 38) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE motion_task ADD COLUMN points INTEGER NOT NULL DEFAULT 5")
        db.execSQL("ALTER TABLE motion_task ADD COLUMN pointsEdited INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE motion_task ADD COLUMN firstDueAt INTEGER")
        db.execSQL("ALTER TABLE motion_task ADD COLUMN target REAL")
        db.execSQL("ALTER TABLE motion_task ADD COLUMN progress REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE motion_task ADD COLUMN unit TEXT")
        db.execSQL("ALTER TABLE motion_task ADD COLUMN earnedPoints INTEGER")
        db.execSQL("ALTER TABLE motion_task ADD COLUMN spawnedTaskId TEXT")
        db.execSQL(
            """
            UPDATE motion_task SET priority = CASE priority
                WHEN 'P1' THEN 'HIGH'
                WHEN 'P2' THEN 'MEDIUM'
                WHEN 'P3' THEN 'LOW'
                WHEN 'P4' THEN 'NONE'
                ELSE priority
            END
            """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE motion_task SET
                points = CASE priority WHEN 'HIGH' THEN 20 WHEN 'MEDIUM' THEN 15 WHEN 'LOW' THEN 10 ELSE 5 END,
                firstDueAt = dueAt
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS motion_score_event (
                id TEXT NOT NULL,
                dateKey TEXT NOT NULL,
                sourceType TEXT NOT NULL,
                sourceId TEXT NOT NULL,
                title TEXT NOT NULL,
                points INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_motion_score_event_dateKey ON motion_score_event (dateKey)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_motion_score_event_sourceType ON motion_score_event (sourceType)")
        val now = System.currentTimeMillis()
        db.execSQL(
            """
            INSERT OR IGNORE INTO brain_node
                (id, parentId, section, name, colorArgb, sortOrder, driveFolderId, isSystem, tags, createdAt)
            SELECT 'node_motion_todo', NULL, 'MOTION', 'To-do', ${0xFF001F3F.toInt()}, 0, NULL, 0, '[]', $now
            WHERE EXISTS (SELECT 1 FROM motion_task WHERE nodeId IS NULL AND status = 'OPEN')
            """.trimIndent()
        )
        db.execSQL(
            "UPDATE motion_task SET nodeId = 'node_motion_todo' WHERE nodeId IS NULL AND status = 'OPEN'"
        )
    }
}

/**
 * Must form an unbroken chain from [OLDEST_MIGRATABLE_VERSION] to VECTOR_DB_VERSION (see MigrationChainTest).
 * Room rejects a migration whose start version is also listed in [DESTRUCTIVE_FROM_VERSIONS].
 */
internal val VECTOR_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_21_22,
    MIGRATION_22_23,
    MIGRATION_23_24,
    MIGRATION_24_25,
    MIGRATION_25_26,
    MIGRATION_26_27,
    MIGRATION_27_28,
    MIGRATION_28_29,
    MIGRATION_29_30,
    MIGRATION_30_31,
    MIGRATION_31_32,
    MIGRATION_32_33,
    MIGRATION_33_34,
    MIGRATION_34_35,
    MIGRATION_35_36,
    MIGRATION_36_37,
    MIGRATION_37_38,
)

/** First version every later version has a migration path from. */
internal const val OLDEST_MIGRATABLE_VERSION = 21

internal val DESTRUCTIVE_FROM_VERSIONS: IntArray = (1 until OLDEST_MIGRATABLE_VERSION).toList().toIntArray()

/** Tables carried in the Brain sync state; edits to them schedule a sync. */
private val BRAIN_SYNC_TABLES = arrayOf(
    "brain_node",
    "memory_note",
    "brain_asset",
    "learning_tile",
    "learning_link",
    "brain_tag",
    "motion_task",
    "motion_score_event",
)

/** Tables carried in the daily shards (docs/daily-sync-spec.md); edits to them schedule a daily sync. */
private val DAILY_SYNC_TABLES = arrayOf(
    "food_logs",
    "activity_logs",
    "sleep_entry",
    "workout_sets",
    "self_entry",
    "daily_home",
    "habit_logs",
    "habit_day_config",
    "activity_labels",
    "habit_definitions",
    "meal_library",
    "exercise_library",
    "activity_library",
)

/**
 * Seeds defaults on fresh create, and after a destructive wipe.
 *
 * Room 2.7 calls [RoomDatabase.Callback.onDestructiveMigration] after dropAllTables
 * but *before* createAllTables — inserting there crashes with "no such table".
 * Flag the wipe and seed from [onOpen] once tables exist.
 */
private val seedLabelsCallback = object : RoomDatabase.Callback() {
    @Volatile
    private var seedAfterDestructive = false

    override fun onCreate(db: SupportSQLiteDatabase) {
        seedDefaultActivityLabels(db)
        seedAllBrainTags(db)
    }

    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
        seedAfterDestructive = true
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        if (!seedAfterDestructive) return
        seedAfterDestructive = false
        seedDefaultActivityLabels(db)
        seedAllBrainTags(db)
    }
}

class VectorApplication : Application() {
    val database: VectorDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            VectorDatabase::class.java,
            "vector_database"
        )
            .addMigrations(*VECTOR_MIGRATIONS)
            .addCallback(seedLabelsCallback)
            // There is no 20 -> 21 migration, so only installs older than v21 may be wiped. Anything newer
            // without a migration path fails loudly instead of silently erasing data.
            .fallbackToDestructiveMigrationFrom(true, *DESTRUCTIVE_FROM_VERSIONS)
            .build()
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Pending workout sets received from Wear; merged when user loads that date in Calendar. */
    private val pendingWearData = ConcurrentHashMap<String, MutableList<WorkoutSet>>()

    /**
     * In-memory workout sets by date, kept in sync by CalendarViewModel.
     * Used when the Wear app requests workouts so it gets what the user sees on mobile (saved or not).
     */
    private val workoutSetsForWearRequest = ConcurrentHashMap<String, List<WorkoutSet>>()

    fun setWorkoutSetsForWearRequest(date: String, sets: List<WorkoutSet>) {
        workoutSetsForWearRequest[date] = sets
    }

    /**
     * Returns workout sets for the given date if the Calendar has loaded/displayed that date, else null.
     */
    fun getWorkoutSetsForWearRequest(date: String): List<WorkoutSet>? = workoutSetsForWearRequest[date]

    fun addPendingWearData(date: String, sets: List<WorkoutSet>) {
        if (sets.isEmpty()) return
        val mergedSnapshot = run {
            val list = pendingWearData.getOrPut(date) { mutableListOf() }
            val byId = list.associateBy { it.id }.toMutableMap()
            sets.forEach { byId[it.id] = it }
            list.clear()
            list.addAll(byId.values)
            list.toList()
        }
        applicationScope.launch {
            _wearDataReceived.emit(date to mergedSnapshot)
        }
    }

    /** Persists merged watch workout to Room (same merge as Calendar) so it survives without tapping Save Changes. */
    fun persistWearWorkoutCompletion(date: String, sets: List<WorkoutSet>) {
        if (sets.isEmpty()) return
        applicationScope.launch(Dispatchers.IO) {
            val result = workoutRepository.mergeWearCompletionAndSave(date, sets)
            if (result.isSuccess) {
                pendingWearData.remove(date)
                _wearWorkoutPersistedForDate.tryEmit(date)
            }
        }
    }

    fun getAndClearPendingWearData(date: String): List<WorkoutSet> {
        return pendingWearData.remove(date) ?: emptyList()
    }

    /** Call after merging wear data into the current view so it is not applied again when switching dates. */
    fun clearPendingWearData(date: String) {
        pendingWearData.remove(date)
    }

    private val _wearDataReceived = MutableSharedFlow<Pair<String, List<WorkoutSet>>>(replay = 1, extraBufferCapacity = 32)
    val wearDataReceived: kotlinx.coroutines.flow.Flow<Pair<String, List<WorkoutSet>>> = _wearDataReceived

    /** Emitted after a watch workout is merged and saved to Room so Calendar can reload from DB (avoids stale in-memory state). */
    private val _wearWorkoutPersistedForDate = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val wearWorkoutPersistedForDate: SharedFlow<String> = _wearWorkoutPersistedForDate.asSharedFlow()

    private val _wearWorkoutReceivedMessage = MutableStateFlow<String?>(null)
    val wearWorkoutReceivedMessage: StateFlow<String?> = _wearWorkoutReceivedMessage.asStateFlow()

    fun setWearWorkoutReceivedMessage(message: String) {
        _wearWorkoutReceivedMessage.value = message
    }

    fun clearWearWorkoutReceivedMessage() {
        _wearWorkoutReceivedMessage.value = null
    }

    /** Emitted when the watch sets food eaten/skipped so Calendar can patch in-memory entries. */
    private val _wearFoodUpdated = MutableSharedFlow<Pair<String, FoodEntry>>(extraBufferCapacity = 32)
    val wearFoodUpdated: SharedFlow<Pair<String, FoodEntry>> = _wearFoodUpdated.asSharedFlow()

    fun emitWearFoodUpdated(date: String, entry: FoodEntry) {
        _wearFoodUpdated.tryEmit(date to entry)
    }

    /** Emitted when the watch sets an activity's completion so Calendar can patch in-memory entries. */
    private val _wearActivityUpdated = MutableSharedFlow<WearActivityUpdate>(extraBufferCapacity = 32)
    val wearActivityUpdated: SharedFlow<WearActivityUpdate> = _wearActivityUpdated.asSharedFlow()

    fun emitWearActivityUpdated(date: String, activityId: String, completion: ActivityCompletion) {
        _wearActivityUpdated.tryEmit(WearActivityUpdate(date, activityId, completion))
    }

    /** Emitted when the watch toggles a Supermarket item so an open Supermarket screen doesn't autosave over it. */
    private val _wearSupermarketUpdated = MutableSharedFlow<WearSupermarketUpdate>(extraBufferCapacity = 32)
    val wearSupermarketUpdated: SharedFlow<WearSupermarketUpdate> = _wearSupermarketUpdated.asSharedFlow()

    fun emitWearSupermarketUpdated(index: Int, title: String, isChecked: Boolean) {
        _wearSupermarketUpdated.tryEmit(WearSupermarketUpdate(index, title, isChecked))
    }

    /** Emitted after [FoodRepository.resetDatabase] succeeds so UI can reload from Room. */
    private val _databaseResetEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val databaseResetEvents: SharedFlow<Unit> = _databaseResetEvents.asSharedFlow()

    fun notifyDatabaseReset() {
        _databaseResetEvents.tryEmit(Unit)
    }

    /** (category, date) whose saved data and draft were deleted from Home; Calendar reloads it. */
    private val _calendarDayCleared = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 8)
    val calendarDayCleared: SharedFlow<Pair<String, String>> = _calendarDayCleared.asSharedFlow()

    fun notifyCalendarDayCleared(category: String, date: String) {
        _calendarDayCleared.tryEmit(category to date)
    }

    val wearActivityStore: WearActivityStore by lazy { WearActivityStore(this) }

    val databaseBackupManager: DatabaseBackupManager by lazy {
        val prefManager = PreferenceManager(this)
        val authManager = GoogleDriveAuthManager(this)
        val driveService = DriveService(this, authManager)
        DatabaseBackupManager(this, database, driveService, prefManager)
    }

    val workoutRepository: WorkoutRepository by lazy {
        val prefManager = PreferenceManager(this)
        WorkoutRepository(this, database, prefManager, PresetStorage(this, prefManager))
    }

    val foodRepository: FoodRepository by lazy {
        val prefManager = PreferenceManager(this)
        FoodRepository(database, prefManager, PresetStorage(this, prefManager))
    }

    val brainRepository: BrainRepository by lazy {
        BrainRepository(database, BrainFileStore(this, database))
    }

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    BrainSyncWorker.enqueue(this)
                    DailySyncWorker.enqueue(this)
                }
                Lifecycle.Event.ON_STOP -> DatabaseBackupScheduler.requestDebounced(this)
                else -> Unit
            }
        })
        applicationScope.launch(Dispatchers.IO) {
            database.invalidationTracker.addObserver(object : InvalidationTracker.Observer(BRAIN_SYNC_TABLES) {
                override fun onInvalidated(tables: Set<String>) {
                    if (!BrainImportGuard.isQuiet) BrainSyncWorker.requestDebounced(this@VectorApplication)
                }
            })
            database.invalidationTracker.addObserver(object : InvalidationTracker.Observer(DAILY_SYNC_TABLES) {
                override fun onInvalidated(tables: Set<String>) {
                    if (!DailyImportGuard.isQuiet) DailySyncWorker.requestDebounced(this@VectorApplication)
                }
            })
        }
        BrainSyncWorker.schedulePeriodic(this)
        DailySyncWorker.schedulePeriodic(this)
        applicationScope.launch(Dispatchers.IO) {
            val prefs = PreferenceManager(this@VectorApplication)
            if (!prefs.isLearnTextBackfillDone()) {
                runCatching { brainRepository.backfillLearnTextBodies() }
                    .onSuccess { prefs.setLearnTextBackfillDone() }
            }
            brainRepository.refreshLearningStatuses(prefs.learningSettings.first())
        }
        DatabaseBackupScheduler.schedule(this)
        MotionDigestWorker.enqueueDaily(this)
        WearMessageReceiver.register(this)
    }
}
