# Analysis: database schema and access

This document describes Room tables used by the Analysis feature, how rows are produced in the app workflow, and how to add a new dashboard.

## Tables overview

| Table | Entity | Primary key | Typical writer |
|-------|--------|-------------|----------------|
| `routine_logs` | `RoutineEntry` | `id` | Calendar → Routine → Save Changes (`replaceEntriesForDate`) |
| `diet_logs` | `DietEntry` | `id` | Calendar → Nutrition → Save Changes |
| `workout_sets` | `WorkoutSet` | `id` | Calendar → Save; Wear merge (`mergeWearCompletionAndSave`) |
| `daily_home` | `DailyHomeEntry` | `date` | Home → Save; Settings sleep target |
| `diary_entry` | `DiaryEntry` | `date` | Calendar → Diary → Save |
| `diary_collection` | `DiaryCollection` | `id` | Diary albums (Drive-backed) |
| `diary_collection_image` | `DiaryCollectionImage` | `id` | Photo sync |

Date columns use `yyyy-MM-dd` strings; range filters use `WHERE date BETWEEN :start AND :end` (lexicographic order matches chronological order).

## N/A conventions (Analysis)

- **Daily Plan (`daily_home`)**: A day is **N/A** if there is **no row** for that date. If a row exists, `dailyPlanCompletionPercent` (0–100) is always a defined value.
- **Routine / Nutrition / Workout (per day)**: **N/A** if there are **no rows** for that date in the corresponding table. Otherwise compute **completion %** from those rows:
  - **Routine**: `completed = count(DONE || (NUMERICAL && isGoalMet))`, `total = count(rows)`, `% = 100 * completed / total`.
  - **Nutrition**: `completed = count(isChecked && !notEaten)`, same total/%.
  - **Workout**: `completed = count(isCompleted)`, `total = count(sets)`, `% = 100 * completed / total`.
- **Diet macros**: **N/A** if no `diet_logs` rows that day; else sum `kcal`, `protein`, `carbs`, `fats` (post-save scaled values).
- **Daily Info** (`daily_home`): **N/A** if no row or missing fields (e.g. sleep times absent for duration).
- **Diary mood**: **N/A** if no `diary_entry` row or `mood == null`.
- **Training volume**: Uses `WorkoutSet.targetMuscles` (comma-separated tags from preset `target` arrays). Empty `targetMuscles` = legacy row; excluded from muscle dropdown unless you add backfill.

## DAO access patterns

Prefer `*Dao.get*BetweenSync(start, end)` (or Flow variants) for analysis ranges. Examples:

```kotlin
// Pseudocode — see RoutineEntryDao, DietEntryDao, etc.
routineEntryDao().getEntriesBetweenSync(start, end)
dietEntryDao().getEntriesBetweenSync(start, end)
workoutSetDao().getSetsBetweenSync(start, end)
dailyHomeEntryDao().getEntriesBetweenSync(start, end)
diaryEntryDao().getEntriesBetweenSync(start, end)
```

Aggregations (streaks, weekly buckets, distinct habits) live in `AnalysisRepository` so composables stay thin.

## How to add a new dashboard

1. Add an entry to `AnalysisDashboard` (see `com.vm.vector.ui.analysis.AnalysisDashboard.kt`).
2. Create `NewThingDashboard.kt` under `ui/analysis/dashboards/`, following `DailyPlanDashboard` (stats row + `AnalysisLineChart` or custom layout).
3. In `AnalysisViewModel`, load any new series in `refresh()` or lazy-load when the dashboard is selected; expose a stable `StateFlow` or data class field.
4. In `AnalysisScreen`, add a branch in `when (dashboard)` to show your composable.
5. If you need new queries, extend the appropriate DAO and `AnalysisRepository` only—avoid embedding SQL in composables.

## Analysis DTOs (Kotlin)

Shared models such as `LineSeries`, `TripleStat`, `CountMaxStreak`, and `RoutineHabitKey` live in
`mobile/src/main/java/com/vm/vector/data/analysis/AnalysisModels.kt` (same package `com.vm.vector.data.analysis`).

## Related app files

- Repositories: `mobile/src/main/java/com/vm/vector/data/`
- Room entities/DAOs: `core/src/main/java/com/vm/core/models/`
- Analysis UI: `mobile/src/main/java/com/vm/vector/ui/analysis/`
