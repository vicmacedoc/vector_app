# Vector - Data Schemas (Multi-Level Nesting)

## 1. Routine (Daily Response)
- Items can be Categorical (Exceeded, Done, Partial, Not Done, N/A) or Numerical (Double).
- Nesting: Categories can contain sub-tasks (e.g., "Study" -> ["Math", "Physics"]).

## 2. Diet (Daily Response)
- Structure: Day -> Meals -> Food Items -> Nutrients (p, c, f, kcal).

## 3. Workout (Daily Response)
- Structure: Session -> Exercises -> Sets -> Reps/Load/Metric data.
- Metrics: Support velocity, RPE, and time-under-tension.

## 4. Journaling
- Fields: Raw text transcript, sentiment (Double), summary (String).