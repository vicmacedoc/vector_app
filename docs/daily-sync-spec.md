# Daily sync protocol (v1)

Everything outside Brain (Calendar, Home, habits, libraries, presets and shared settings) syncs through
small JSON **shards** on Google Drive. Each shard follows the Brain protocol in `brain-sync-spec.md`
(base / local / remote, the decision table, the per-record 3-way merge, pushes guarded by the Drive
`version`). Only the collection policy differs, and both-sides-changed shards merge **silently**
unless a text field really conflicts.

Implementations: `mobile/.../data/sync/ShardSync.kt` + `DailySnapshotStore.kt` and
`desktop/src/sync/shards.ts` + `desktop/src/sync/shardEngine.ts`. Shared cases live in
`sync-fixtures/daily/`.

## Files

| Where | What |
|---|---|
| Drive `Data/sync/config.json` | Shard `config`: labels, habits, libraries, presets, shared settings |
| Drive `Data/sync/days/YYYY-MM.json` | Shard `days/YYYY-MM`: every date-keyed record whose `date` is in that month |
| Device-local base per shard | The last shard content this device and Drive agreed on, plus the Drive `version` it had |
| Device-local pending per shard | Remote shard waiting for a merge decision (only when a text field conflicts) |
| Device-local dirty marks | `shard -> last local edit (epoch ms)`; cleared when the shard is pushed or found equal |

Calendar drafts (`calendar_drafts`), the device-local `DailyHomeEntry.dailyPlanAudioPath`, Self photo albums and
audio binaries are **not** in shards. Journal audio is referenced by `SelfEntry.journalAudioDriveFileId`.

## Shard file

```json
{
  "formatVersion": 1,
  "shard": "days/2026-10",
  "rev": 7,
  "writtenBy": { "deviceId": "uuid", "name": "Pixel 9", "platform": "android" },
  "writtenAt": 1759600000000,
  "records": {
    "food": { "<id>": { "id": "...", "date": "2026-10-21", "...": "..." } },
    "sleep": { "2026-10-20": { "date": "2026-10-20", "...": "..." } }
  }
}
```

Records are written **complete** (every entity field, explicit `null`s, enums by name), exactly as the
kotlinx-serialization of the Room entity in `core/src/main/java/com/vm/core/models`.

### `days/YYYY-MM` collections

| Collection | Entity | Key |
|---|---|---|
| `food` | `FoodEntry` | `id` |
| `activities` | `ActivityEntry` | `id` |
| `sleep` | `SleepEntry` (night of `date`) | `date` |
| `workoutSets` | `WorkoutSet` | `id` |
| `self` | `SelfEntry` | `date` |
| `home` | `DailyHomeEntry` without `dailyPlanAudioPath` | `date` |
| `habitLogs` | `HabitLog` | `id` |
| `habitConfigs` | `HabitDayConfig` | `date` |

### `config` collections

| Collection | Content | Key |
|---|---|---|
| `labels` | `ActivityLabel` | `id` |
| `habits` | `HabitDefinition` | `id` |
| `mealLibrary` | `MealLibraryItem` | `nameKey` |
| `exerciseLibrary` | `ExerciseLibraryItem` | `typeKey + "|" + nameKey` |
| `activityLibrary` | `ActivityLibraryItem` | `nameKey` |
| `presets` | `{ category, displayName, content, updatedAt }`, `category` in `Workout`, `Food`, `Routine`; a removed preset is a missing record | `category` |
| `settings` | one record `shared` (below) | `"shared"` |

```json
{
  "sleepTargetBedtimeMinutes": 1320, "sleepTargetWakeupMinutes": 360,
  "filloutReminderMinutes": 1200,
  "learnOnHoldDays": 14, "learnReviewIntervals": [3, 7, 21, 60, 180],
  "learnQuizSize": 5, "learnPassPercent": 80
}
```

Motion settings stay in Brain `state.json`.

## When to sync

- **Local first.** Every save writes locally and sets `dirty[shard] = now`.
- **Push fast.** Desktop: 3 s after a save. Phone: expedited work 5 s after a save.
- **Pull cheap.** One `files.list` of `Data/sync` and `Data/sync/days` returns `name` and `version` for every shard.
  A shard needs work when it is dirty, has no base, or its Drive `version` differs from the base's.
  Desktop: on focus and every 60 s. Phone: on foreground, every 60 s while open, and hourly in the background.
- A periodic **full scan** (phone: the hourly job; desktop: start-up) also compares every local shard
  with its base, catching edits that missed a dirty mark.

## Per-shard decision

Same as Brain, plus the silent tier:

| local changed | remote changed | action |
|---|---|---|
| no | no | nothing (clear the dirty mark) |
| yes | no | push `rev = remote.rev + 1` (re-check `version` first) |
| no | yes | apply remote locally, base = remote |
| yes | yes | analyze with the daily policy; **no conflicts -> apply the merge locally and push** (toast "Merged N changes"); conflicts -> store pending and ask |

`localChanged` compares canonical(local) with canonical(base) (no base: local has any record).
`remoteChanged` = remote exists and (no base, or `remote.rev != base.rev`).

## Daily merge policy

The record/field rules of `brain-sync-spec.md` apply, with these collection policies:

- **Side stamps.** `local` = the shard's dirty mark (last local edit), `remote` = the remote file's `writtenAt`.
  The **newer side** is `REMOTE` when `remote > local`, otherwise `LOCAL`.
- **Newest record wins** (never conflicts; an edit beats a delete; between edits the higher stamp field wins,
  tie -> local): `mealLibrary`, `exerciseLibrary`, `activityLibrary` (`createdAt`), `presets`, `habitConfigs` (`updatedAt`).
- **Auto fields.** In every other collection, a field changed differently on both sides takes the newer side's
  value, **except text fields**, which become conflicts:

| Collection | Text fields |
|---|---|
| `food` | `name`, `unit` |
| `activities` | `title`, `description` |
| `workoutSets` | `sessionTitle`, `sessionDescription`, `exerciseName`, `exerciseDescription`, `description`, `timing` |
| `self` | `intention`, `highlight`, `lowlight`, `gratitude`, `journalText` |
| `home` | `dailyPlanText` |
| `labels`, `habits` | `name` |
| `sleep`, `habitLogs`, `settings` | none |

- Delete-vs-edit is always a conflict. **Keep both** is never offered.
- Conflicts carry `newer`; the merge dialog preselects that side.
- No clean-up passes after resolving.

## Applying a shard locally

Replace, in one transaction, every row of the shard's collections that belongs to the shard (date in the
month for `days/*`; the whole table for `config`) with the shard's records. Imports must not mark the shard dirty.

## Fixtures

`sync-fixtures/daily/*.json` use the Brain fixture format plus `"stamps": { "local": n, "remote": n }`
and are run with the daily policy. `resolvedLocal` / `resolvedRemote` resolve the remaining conflicts.
