# Vector
**The life-tracking app made by and to Victor.**

Vector is a personal engineering project designed to optimize the "Victor System." A highly personalized tool to monitor and optimize every aspect of my life.

## Features
- **Lists:** Tackle the "shopping-list-to-fridge" pipeline with LLM-generated JSON precision.
- **Calendar:** The daily command center. Log the grind—workouts, meals, deep work—and see the data reality.
- **Analysis:** Because what gets measured, gets optimized. Built for deep dives into training load and life-efficiency.
- **Sync:** Local-first speed with Google Drive storage—keeping it private, portable, and free.

## Specs
- **Engine:** Kotlin + Jetpack Compose (Material 3).
- **Brain:** Room SQLite for historical data; DataStore for the "under-the-hood" settings.
- **Connectivity:** Future-proofed for WearOS (for those mid-set adjustments).
- **Data Flow:** Speaks fluent JSON for easy external processing via Python scripts.

## Project Structure
- `:app`: The mobile command center.
- `:wear`: The companion watch app (coming soon!).
- `:core`: The shared brain containing our data models and nested JSON logic.