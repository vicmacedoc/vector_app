# Vector - Data Schemas

## 1. Routine (Habits + Activities)
- **Habits:** Global catalog (`habit_definitions`) + day selection snapshot (`habit_day_config`) + per-day status logs (`habit_logs`). Statuses: None, Not Done, Partial, Done, Exceeded, N/A.
- **Activities:** Label catalog (`activity_labels`, fixed Sleep; Room v28 seeds deletable defaults from `ActivityLabel.DEFAULT_LABELS` once, so deleted defaults stay gone) + timed logs (`activity_logs`) with title, original start/end, optional adjusted start/end, completion, type (Planned/New), description.
- **Weekly Routine preset (`WeeklyRoutinePlan`, Settings → Preset Settings → Routine):** weekday keys `monday`…`sunday`, each a list of `{ title, startTime "HH:mm", endTime "HH:mm", label, type "PLANNED"|"NEW", description }`. May be wrapped as `{ "name", "weekly_plan": { ... } }` like the Food/Workout presets, or bare (weekdays at top level). Labels match `activity_labels` by name (case-insensitive); unknown names are created on apply. A day with no saved activities and no draft shows that weekday's preset in memory (ids `preset_{date}_{index}`, type PLANNED) until saved.
- **Libraries (Room v24):** Reusable catalogs separate from weekly JSON planners. `exercise_library` stores workout sessions (`type` + `name`, payload = `WorkoutDaySession`). `meal_library` stores individual meals (not full weekly diets). `activity_library` (Room v35) stores individual activities (`title`, `startTime`, `endTime`, `labelName`, `type`, `description`), upserted by lowercased title (`nameKey`) from the weekly Routine preset (on apply and on load) and from every confirmed Calendar add. `routine_presets` is legacy: old named collections are flattened into `activity_library` once and deleted.
- **Sleep (`sleep_entry`, Room v25):** One row per night, keyed by the date you went to bed. Holds planned/actual bedtime and wake-up, `fellAsleepAt`, and stage minutes (deep/light/REM/awake). Percentages and latency are derived (`SleepMath`); unset stages are null. Calendar day D shows last night from row D−1 and tonight's bedtime from row D. Settings defaults live in DataStore and seed planned times; changing a day's planned time does not update the global default.
- **24h donut:** Planned (inner, original times) vs Done (outer, effective/adjusted times); center shows capped planned-done hours and % of planned duration completed. Overlapping arcs: latest-added activity draws on top. Sleep on day D is two arcs: `00:00 → wake` from `sleep_entry(D−1)` and `bedtime → 24:00` from `sleep_entry(D)` (evening bedtime at/after noon). Naps in `activity_logs` are not part of the Sleep section duration.

## 2. Food (Daily Response)
- Structure: Day -> Meal Time (Header) -> Food Components (Checkable Items with Sliders and Weights) -> Nutrients (p, c, f, kcal).
- Items must track AuditStatus (PLANNED, ADJUSTED, UNPLANNED) based on whether they match the preset name and quantity multiplier.
- Calendar Food entries must be grouped by planned/effective time.

## 3. Exercise / Workout (Daily Response) — template v2, Room v33
- Structure: Session -> Exercises -> Sets (+ drops) or Intervals. Calendar category display name is **Exercise**; Room entity remains `WorkoutSet` (one row per main set, drop, interval, or OTHER exercise).
- **Session** (`WorkoutDaySession`): `type` RESISTANCE | ENDURANCE | OTHER; ENDURANCE adds `sport` RUN | BIKE | SWIM.
- **Exercise** (`WorkoutExercisePlan`): every exercise has `totalDuration` + `unitDuration` (planned minutes incl. rests). Legacy `targetDuration` (pre-v2 ENDURANCE/OTHER) is read as a fallback; when `unitDuration` is omitted, a v2 `totalDuration` means minutes and a legacy `targetDuration` means seconds.
  - RESISTANCE: weighted `target` map (`{"back":1.0,"biceps":0.5}` = volume credit per set; legacy ordered lists read first = 1.0, rest = 0.5), `timing`, `supersetGroup` (letter; legacy key `groupingId`), `sets`.
  - ENDURANCE: `stroke` (swims: FREE | BACK | BREAST | FLY | IM | CHOICE | DRILL | KICK) and `intervals`.
  - OTHER: only `totalDuration` (+ optional `rest`/`unitRest` after it for sequenced drills, which are **separate exercises**).
- **Set** (`WorkoutSetPlan`): `setType` NORMAL | DROP; DROP sets carry `drops[]` (`dropNumber`, `targetLoad`, `unitLoad` (null = inherit the set's), `targetReps`, `targetRir`), done straight after the set with no rest; the set's `rest` comes after its last drop.
- **Interval** (`WorkoutIntervalPlan`): `intervalNumber`, either `targetDuration`/`unitDuration` or `targetDistance`/`unitDistance`, `targetZone` (1–5), decimal `targetRpe` (0.5 steps), `targetCadence`/`unitCadence`, `rest`/`unitRest`, `description`.
- **Rows** (`WorkoutPlanRows`): exercise id `{prefix}_{sessionIndex}_{exerciseIndex}`; row id `{date}_{exerciseId}_{setNumber}`, drop rows append `_d{dropNumber}` (`dropNumber > 0`, same `setNumber` as their main set, `setType` DROP on main and drops), interval rows use `setType` INTERVAL with `setNumber = intervalNumber`, OTHER is one row. Every row copies `sport`, `stroke`, `groupingId` (superset letter), `totalDuration` (minutes) and `targetMuscles` (`back:1.0,biceps:0.5`).
- **Actuals:** `actualDuration` is always seconds; `actualTotalDuration` is the watch-measured seconds for the whole exercise (same value on each of its rows); `actualCadence`, decimal `actualRpe`.
- **Completion:** a unit counts as done only via `isCompleted`. Exercise status: Done when every main set/interval is concluded, Partial when some are. Calendar credit: once any unit of an exercise is `isCompleted`, each unit's own flag counts; otherwise the status decides (Done/Exceeded = 1, Partial = 0.5).
- **Day summary** (`ExerciseDayMath`): done minutes per exercise = `actualTotalDuration`, else planned minutes × completion share; buckets Resistance / Run / Bike / Swim / Endurance / Other. Muscle volume = weight × credit over main resistance sets only.
- **Volume rules** (`TrainingVolumeMath`): drop rows add tonnage but no set volume and are excluded from e1RM; non-resistance rows add no muscle volume.

## 4. Self (Scales + Journal + Collections)
- Twelve 1–5 scales defined once in `SelfScale` (id, label, poles): mood (sad→happy), energy (lethargic→wired), stress (overwhelmed→relaxed), cognition (scattered→focused), motivation (apathetic→driven), physical (exhausted→fresh), sleep quality (restless→restorative), social (withdrawn→connected), self-esteem (insecure→confident), actions (shameful→proud), external (resentful→grateful), future (pessimistic→optimistic).
- Every scale runs worst to best, so all of them share `moodColorForLevel` (1 = red, 5 = green). Keep new scales oriented that way rather than adding a per-scale colour flip.
- Intention: `intention` text (no review flag).
- Journal: `highlight` (what fuelled me), `lowlight` (what drained me), `gratitude`, free text, journal audio (Drive).
- A day counts as logged when any of the above is set (`SelfEntry.hasAnyContent`); week dots and Home/Overview completion use that rule.
- Analysis: Self dashboard owns the affect trend, the per-scale day grid with averages and half-split trend pills, the mood calendar, the correlation of each scale against mood (`AnalysisStats.pearson`, min 5 paired days), cross-domain splits (sleep/training/habits, plus rated sleep quality against measured hours), intention and reflection stats including per-prompt coverage. Body keeps composition. Sleep dashboard owns duration, stages, efficiency, consistency and cross-metric correlations.
- Collections: Drive albums under `collections/`; system albums `front`/`left`/`back`/`right` for body scan (non-deletable).

## 5. Brain (Memory / Model / Motion / Inbox) — Room v38
- **Tabs:** Memory, Model, Motion, Inbox. Inbox is shared by every section: quick captures (text, link, voice, photo/video, file) land there with `nodeId = null` until filed. Quick-capture **To-do** adds several items at once (name, deadline, priority, points) as unfiled `MotionTask`s; the Inbox shows them in a "To-dos" group and the badge counts notes + to-dos.
- **BrainNode:** Memory rows are flat **memory tiles** (`parentId` always null) with `tags` (JSON list of tag names, Room v31). A tile's sources are `MemoryNote`s with that `nodeId`. Motion rows (`section = MOTION`) are **to-do list tiles**. Room v31 flattens old nested Memory folders.
- **BrainAsset:** Local file mirror (`localRelativePath`, `mimeType`, `driveFileId`, `syncState`: PENDING | WIFI_ONLY | UPLOADED | FAILED | DELETE_PENDING, `description`). Videos use WIFI_ONLY. `description` is the play-screen note, separate from a voice-note transcript in `MemoryNote.body`. Room v29 adds the column.
- **MemoryNote:** Memory sources and Inbox captures (`kind`: TEXT | AUDIO | IMAGE | LINK | PDF | VIDEO | FILE, derived from MIME on import). `nodeId` null = Inbox. Audio recordings carry no tags. `description` (Room v32) is an optional short summary shown on the source card; TEXT notes keep their markdown content in `body`, edited on a full-screen markdown editor.
- **BrainTag:** Tag libraries (`brain_tag`: `id`, `name`, `createdAt`, `section` MEMORY | MODEL, Room v37), no colors. Memory and Learn tiles each pick from their own library (names unique per section, case-insensitive) and store tag names in `BrainNode.tags`; deleting a library tag leaves tiles that use it untouched. Memory defaults (`BrainTag.DEFAULT_TAGS`, ids `tag_default_…`) are general: Ideas, Reminders, Fun Facts, Quotes, To Learn, Goals, … Learn defaults (`BrainTag.MODEL_DEFAULT_TAGS`, ids `tag_model_…`) are knowledge fields: Philosophy, Law, Mathematics, Physics, Chemistry, Engineering, Software, AI, Economics, … Room v31 drops old default ids not in the new list and seeds the new ones; v37 adds `section` (existing rows → MEMORY) and seeds the Learn defaults; user-created tags are kept. Tag filters match tiles that carry **all** selected tags.
- **Learn tiles (Model tab, Room v36):** a Learn tile is a `BrainNode` with `section = MODEL` (name + tags, same UI as memory tiles). Its sources are `MemoryNote`s with that `nodeId`, exactly like Memory. Room v36 migrated old tiles into `brain_node`, old `learning_source` rows into `memory_note` (VIDEO_LINK/PODCAST_LINK → LINK, VIDEO_FILE → VIDEO, system `AI.txt` → pinned) and a non-empty summary into a pinned "Summary" TEXT note; a one-time Kotlin backfill (DataStore flag `learn_text_backfill_done`) copies migrated `.txt` files into `body` and rebuilds FTS.
- **LearningTile:** learning state keyed by the node id (`id` = `BrainNode.id`): `status` (stored by name), `lastActivityAt`, `learnedAt`, `reviewStep`, `nextReviewAt`, `lastReviewScore` (0..1), `lastReviewedAt`.
  - The Learn list has three chips, each with two collapsible sections (first one listed first): **Backlog** = PRIORITY "Priority" + BACKLOG "General"; **In Progress** = IN_PROGRESS "Active" + ON_HOLD "On Hold"; **Learned** = TO_REVIEW "Review" + LEARNED "Done". PRIORITY only exists inside Backlog.
  - Activity (note created/saved/moved in/body updated/transcribed, a quiz) sets `lastActivityAt` and moves BACKLOG/PRIORITY/ON_HOLD → IN_PROGRESS. Other changes (Priority, Learned, …) are manual from the status pill or a card long-press.
  - `refreshLearningStatuses` (app start, Brain screen open): LEARNED with `nextReviewAt <= now` → TO_REVIEW; IN_PROGRESS idle longer than the on-hold threshold → ON_HOLD.
  - Set to Learned: `reviewStep = 0`, `nextReviewAt = now + interval[0]`. Review "Still solid": step + 1 and the next interval; after the last interval `nextReviewAt = null` (mastered, no more reviews). "Needs more": back to IN_PROGRESS, ladder reset.
  - Learning Settings (DataStore): `learn_on_hold_days` (14), `learn_review_intervals` ("3,7,21,60,180"), `learn_quiz_size` (5), `learn_pass_percent` (80; only suggests the review outcome).
- **LearningLink:** `learning_link` (`fromNodeId`, `toNodeId`, `createdAt`; composite PK) = "from depends on to"; the tile screen shows "Depends on" and "Used in". Self links and cycles are refused. Deleting a tile deletes its links.
- **Learn export / Gemini:** `LearningContextBuilder` renders a tile (status, dates, tags, links, notes, link URLs, file names; optional prerequisites; PDFs ≤ 10 MB attached for Gemini). Export writes one `.txt` (save via document picker or share via FileProvider `cache/learn_exports`). Gemini (on demand only): suggest links (+ new Backlog prerequisites), draft notes (saved as TEXT "AI draft: …", description "Generated by Gemini, verify"), quiz (MCQ graded locally, open answers graded one call each).
- **AudioProgress:** Playback checkpoint per asset (`positionMs`, `speed`, bookmarks). A cold start of a recording at least 45s long resumes 15s earlier; shorter notes resume at the saved position; finishing (or stopping within 2s of the end) starts over.
- **MotionTask:** To-do items: `title`, `notes` (description), `nodeId` (list tile; null = Inbox capture), `priority` NONE | LOW | MEDIUM | HIGH (Room v38 maps legacy P1→HIGH, P2→MEDIUM, P3→LOW, P4→NONE; the Wear payload sends the new names, the watch still accepts P1–P4), `points` (base: NONE 5, LOW 10, MEDIUM 15, HIGH 20; follows priority unless `pointsEdited`), `dueAt`, `firstDueAt` (deadline before any snooze/edit), `leadMinutes`, recurrence, optional numeric `target` + `progress` + `unit` (auto-checks when progress ≥ target), `earnedPoints` (frozen at check time), `spawnedTaskId` (next recurrence, deleted on uncheck if still open), status OPEN/DONE/CANCELLED. Room v38 moves open unfiled tasks into a "To-do" list (`node_motion_todo`).
- **Motion scoring (`MotionScoring`, "guarded"):** worth = base + early bonus (+1 per spare day before `min(dueAt, firstDueAt)`, max +7) or late penalty (−1 per day late, floor 50% of base); no deadline = +5 per week since creation, capped at 2× base. Checking freezes the worth into `earnedPoints` and writes a ledger row; unchecking deletes it.
- **MotionScoreEvent (`motion_score_event`, Room v38):** points ledger (`id`, `dateKey` yyyy-MM-dd, `sourceType` TASK | SUPERMARKET, `sourceId`, `title`, `points`, `createdAt`). Task rows use id `task_<taskId>`; trips use `supermarket_<dueDate>` with `sourceId` = cycle due date. Daily score, 7-day bars and streak (days ≥ target) are sums by `dateKey`; deleting a list keeps its ledger rows.
- **Motion tab:** score card (today vs daily target, streak, last 7 days), pinned Supermarket tile (to-buy count, trip status, worth now), then list tiles (open / overdue / today / this week counts). FAB = New list. A list screen groups Overdue / Today / This week / Later / No deadline / Done today; its + offers New item, Merge to-dos (Inbox captures, all checked by default) and From text (Gemini `parseTodos` → same checkbox picker; date-only deadlines become 18:00).
- **Motion Settings (DataStore):** `motion_daily_target` (50, 5..1000), `motion_supermarket_day` (SUNDAY).
- **Supermarket trip:** worth 20 on/before the due day, −5 per day late (min 0). The cycle is due on the next Supermarket day; it opens 6 days before and, once a trip is logged, the next cycle starts after that due date (skipped weeks roll forward). Saving the list asks "Did you go to the supermarket?" only when some `remaining` went up versus the last explicit save/refresh (baseline `filesDir/brain/Motion/.supermarket_baseline.json`, untouched by watch toggles), no item is at 0, and the cycle is open. Yes logs the trip; the list is saved either way.
- **Drive mirror:** Metadata in Room (DB backup). Files under `Data/brain/Memory/<tile name>/…`, `Data/brain/Model/<tileId>/…`, `Data/brain/Inbox/…`. Motion tasks are Room-only; `Data/brain/Motion/` holds only `Supermarket.json`. **Two-way pull:** after uploads, `BrainDrivePullSync` turns each top-level Drive Memory folder into a memory tile (nested subfolders import into that same tile), matches each Model folder to a Learn tile (by Drive folder id, then folder name = tile id, else a new tile) and imports new files into Room as notes (does not delete local-only items).
- **FTS (`brain_fts`, Room v27):** Unified unicode61 index over notes/tasks/nodes (Learn tiles are nodes since v36); maintained on write; `searchAll` uses MATCH with LIKE fallback.
- **Optional Gemini transcription:** Voice notes can be transcribed on demand (never auto after record) via Settings Gemini key → transcript into the note body. A custom title is kept; a generated title (`Voice note …`, `Watch voice …`, or the file name) is replaced by the transcript title.
- **Supermarket JSON (`Data/brain/Motion/Supermarket.json`, local `filesDir/brain/Motion/Supermarket.json`):** opened from the Motion tab. Local file is primary; saves upload to Drive; Refresh replaces the local copy and, if brain/Motion has no file yet, moves the legacy `lists/Supermarket.json` there. Other `lists/` JSON files are no longer supported. Item `title`, `category` (aisle label, optional → null), `quantity` (total / slider max, defaults to 1), `remaining` (left, defaults to total, clamped 0..total) + global `order` (set by long-press drag on the phone; the `items` array itself is never reordered because the watch toggles by index). Legacy per-aisle `order` collisions fall back to `sections` route order. Phone filters: All / To buy (remaining/quantity ≤ 50%) / Stocked; no urgency tags or manual priority. The watch payload still carries a computed `urgency` (≤25% Urgent, ≤50% Low, else Stocked) for its dot color.

## 6. Wear protocol (MessageClient + ChannelClient)
Paths live in `core/.../wear/WearProtocol.kt` under `/vector/…`. Phone entry: `VectorWearableListenerService` (`MESSAGE_RECEIVED` + `CHANNEL_EVENT`).

### Workouts
| Path | Direction | Payload |
|------|-----------|---------|
| `/vector/workouts_request` | Watch → Phone | UTF-8 date `yyyy-MM-dd` |
| `/vector/workouts_response` | Phone → Watch | JSON `WorkoutsForDatePayload` |
| `/vector/workout_completed` | Watch → Phone | JSON `WorkoutCompletedPayload` |

- The watch sends every row of the session back. Concluded sets/intervals have `isCompleted = true`; each exercise's rows carry `workoutStatus` (Done / Partial / null) and `actualTotalDuration`. The phone overlays non-null actuals and never treats touched values on an unfinished row as done.
- RESISTANCE runs blocks: one exercise, or consecutive exercises sharing `groupingId` as a superset in round-robin order (A1, A2, A1, …). A step is a main set plus its drops on one screen, editable during rest. Superset time is split between members by planned `totalDuration`.
- ENDURANCE and OTHER run intervals: timed intervals count down, distance intervals run a stopwatch until Done, rest counts down, and an RPE log at the end applies to the exercise's concluded intervals. OTHER is one interval of `totalDuration`.
- Swiping back (or the back button) from an active exercise asks Keep / Discard / Stay. Keep keeps concluded units and reverts unfinished ones to their planned values; Discard reverts every row of the exercise (or superset).

### Motion to-dos
| Path | Direction | Payload |
|------|-----------|---------|
| `/vector/motion_request` | Watch → Phone | empty |
| `/vector/motion_response` | Phone → Watch | JSON `MotionForWearPayload` (`WearMotionTask`: id, title, priority, dueAt, bucket OVERDUE/TODAY/UPCOMING/SOMEDAY) |
| `/vector/motion_complete` | Watch → Phone | JSON `MotionCompletePayload` → phone runs `BrainRepository.completeTask`, replies with refreshed list |

### Nutrition (today's food)
| Path | Direction | Payload |
|------|-----------|---------|
| `/vector/food_request` | Watch → Phone | UTF-8 date |
| `/vector/food_response` | Phone → Watch | JSON `FoodDayForWearPayload` (`WearFoodItem` + `WearFoodState` NONE/EATEN/SKIPPED) |
| `/vector/food_status_set` | Watch → Phone | JSON `FoodStatusSetPayload` → persists `isChecked`/`notEaten` (materializes preset into Room if needed), emits `wearFoodUpdated` for Calendar patch, replies with day |

### Routine (24h activities)
| Path | Direction | Payload |
|------|-----------|---------|
| `/vector/activities_request` | Watch → Phone | UTF-8 date |
| `/vector/activities_response` | Phone → Watch | JSON `ActivitiesForWearPayload` (`WearRoutineActivity`: effective start/end, label color, completion; read-only `isSleep` arcs `00:00 → wake` from night D−1 and `bedtime → 00:00` from night D) |
| `/vector/activity_completion_set` | Watch → Phone | JSON `ActivityCompletionSetPayload` → `ActivityRepository.updateCompletion`, emits `wearActivityUpdated` for Calendar patch, replies with day |

### Supermarket
| Path | Direction | Payload |
|------|-----------|---------|
| `/vector/supermarket_request` | Watch → Phone | empty |
| `/vector/supermarket_response` | Phone → Watch | JSON `SupermarketForWearPayload`: to-buy + in-cart items in aisle order |
| `/vector/supermarket_toggle` | Watch → Phone | JSON `SupermarketTogglePayload` → sets `isChecked`, saves via `SupermarketRepository` (local + Drive), emits `wearSupermarketUpdated` for an open Supermarket screen, replies with list |
- List item ids are regenerated on every local load, so the watch addresses items by file **index** with **title** as a guard (falls back to first title match).

### Voice → Brain Inbox
- **ChannelClient** path prefix `/vector/audio/<filename.m4a>` (watch streams via `getOutputStream`, phone reads via `getInputStream`).
- Phone writes `brain/Inbox/<file>`, creates AUDIO `MemoryNote` with `nodeId = null` (Inbox), enqueues `BrainSyncWorker`.
- Home surfaces unseen events via `WearActivityStore` ("From your watch" card): AUDIO + WORKOUT from the last 48h.

### Legacy routine paths
`/vector/routine_*` remain as no-ops for older watches.
