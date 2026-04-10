<p align="left">
  <img src="assets/logo.png" alt="Vector Logo" width="280"/>
</p>


# Vector
A life-tracking app made by Victor to Victor. 

Designed to provide life with directions for changes of large magnitude! It strictly follows preset rules for the Victor' Space, which comprises my Diet, Exercises, Routine and Diary.

Vector is a personal project designed to optimize the "Victor System". A highly personalized tool to monitor, store and optimize every aspect of my life.

## Features
- **Lists:** Tackle the "shopping-list-to-fridge" or "daily tasks" pipeline with LLM-generated JSON files for simplicity.
- **Calendar:** The daily input center. Seamless log my workouts, meals, routine and feelings each day, check past reports or edit future goals.
- **Analysis:** Adaptative real time dashboards for my most relevant metrics such as study time, training load, caloric intake or sentiment heatmaps.
- **Sync:** Personally managed with Google Drive storage—keeping it private, portable, and free.

## Specs
- **Engine:** Kotlin + Jetpack Compose (Material 3).
- **Brain:** Room SQLite for historical data; DataStore for the "under-the-hood" settings.
- **Connectivity:** Workout feature connected with WearOS (for a seamless training tracking).
- **Data Flow:** Lots of JSON files with the SQLite database for easy backup and storage, with computer-based Python scripts for more advanced analysis.

## Project Structure
- `:app`: The mobile command center.
- `:wear`: The companion watch app (coming soon!).
- `:core`: The shared brain containing our data models and nested JSON logic.
- `:python`: The place for python scripts to access the generated data and provide in-depth analysis.

## License
Vector is licensed under the [MIT License](LICENSE).  

> **Personal project, open ideas.**
