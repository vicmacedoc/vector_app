import { describe, expect, it } from "vitest";
import { CONFIG_SHARD, D, K } from "@/sync/shards";
import type { ShardRecords } from "@/sync/shardEngine";
import { loadBody, loadFood, loadRoutine, loadSelf, loadSleep, loadTraining, resolveDateRange } from "./analysis";
import { normWorkoutSet } from "./models";

const shards: Record<string, ShardRecords> = {
  [CONFIG_SHARD]: {
    [K.HABITS]: { h1: { id: "h1", name: "Read", createdAt: 1 } },
  },
  "days/2026-10": {
    [D.HOME]: {
      "2026-10-05": { date: "2026-10-05", weightKg: 80, bodyFatPercent: 20, timestamp: 1 },
      "2026-10-07": { date: "2026-10-07", weightKg: 79, bodyFatPercent: 19, timestamp: 1 },
    },
    [D.FOOD]: {
      f1: { id: "f1", date: "2026-10-05", name: "Eggs", kcal: 200, protein: 20, carbs: 0, fats: 10, time: "08:00", isChecked: true, status: "PLANNED", timestamp: 1 },
      f2: { id: "f2", date: "2026-10-05", name: "Rice", kcal: 400, protein: 5, carbs: 80, fats: 2, time: "13:30", isChecked: true, quantityMultiplier: 1.5, status: "ADJUSTED", timestamp: 1 },
      f3: { id: "f3", date: "2026-10-06", name: "Cake", kcal: 500, protein: 5, carbs: 60, fats: 25, notEaten: true, status: "PLANNED", timestamp: 1 },
    },
    [D.SLEEP]: {
      "2026-10-04": { date: "2026-10-04", plannedBedtime: "23:00", plannedWakeUp: "07:00", actualBedtime: "23:30", actualWakeUp: "07:00", deepMinutes: 90, timestamp: 1 },
    },
    [D.SELF]: {
      "2026-10-05": { date: "2026-10-05", mood: 4, energy: 3, intention: "Ship it", journalText: "good day today", timestamp: 1 },
      "2026-10-06": { date: "2026-10-06", mood: 2, timestamp: 1 },
    },
    [D.HABIT_CONFIGS]: { "2026-10-01": { date: "2026-10-01", habitIdsJson: '["h1"]', updatedAt: 1 } },
    [D.HABIT_LOGS]: { a: { id: "a", date: "2026-10-05", habitId: "h1", status: "DONE", timestamp: 1 } },
  },
};

describe("analysis loaders", () => {
  it("resolves fixed and custom windows", () => {
    const [s, e] = resolveDateRange(shards, "DAYS_7");
    expect(e > s).toBe(true);
    expect(resolveDateRange(shards, "CUSTOM", "2026-10-09", "2026-10-01")).toEqual(["2026-10-01", "2026-10-09"]);
  });

  it("sums only eaten food with the quantity multiplier", () => {
    const f = loadFood(shards, "2026-10-05", "2026-10-06");
    expect(f.kcal).toEqual([800, 0]);
    expect(f.daysLogged).toBe(2);
    expect(f.mealTimes).toEqual([{ date: "2026-10-05", minutes: [480, 810] }]);
    expect(f.avgEatingWindowHours).toBeCloseTo(5.5);
    expect(f.adherence.map((s) => s.name)).toEqual(["Planned", "Adjusted", "Skipped"]);
  });

  it("attributes a night to the morning it ended", () => {
    const s = loadSleep(shards, "2026-10-05", "2026-10-06");
    expect(s.sleepHours).toEqual([7.5, null]);
    expect(s.sleepTargetHours).toBe(8);
    expect(s.stageSeries.find((x) => x.name === "Deep")!.values).toEqual([1.5, 0]);
  });

  it("derives lean mass and deltas", () => {
    const b = loadBody(shards, "2026-10-05", "2026-10-07");
    expect(b.leanMass[0]).toBeCloseTo(64);
    expect(b.weightDelta).toBe(-1);
    expect(b.latestBodyFat).toBe(19);
  });

  it("builds habit rows and the Sleep label", () => {
    const r = loadRoutine(shards, "2026-10-05", "2026-10-06");
    expect(r.habits[0].name).toBe("Read");
    expect(r.habits[0].cells[0]).toBe("DONE");
    expect(r.labels.some((l) => l.name === "Sleep")).toBe(true);
  });

  it("splits mood by intention and fills the week grid", () => {
    const s = loadSelf(shards, "2026-10-05", "2026-10-06");
    expect(s.loggedDays).toBe(2);
    expect(s.moodWithIntention).toBe(4);
    expect(s.moodWithoutIntention).toBe(2);
    expect(s.moodRows[0].cells.slice(0, 2)).toEqual([4, 2]);
    expect(s.moodDistribution).toEqual([0, 1, 0, 1, 0]);
  });

  it("charts e1RM for the most-logged exercise", () => {
    const set = (date: string, load: number) =>
      normWorkoutSet({ id: date, date, exerciseName: "Squat", exerciseType: "RESISTANCE", actualLoad: load, actualReps: 5, isCompleted: true, targetMuscles: "quads:1", setNumber: 1 });
    const t = loadTraining([set("2026-10-05", 100), set("2026-10-07", 110)], "2026-10-05", "2026-10-11", null, null);
    expect(t.selectedExercise).toBe("Squat");
    expect(t.e1rmDates).toEqual(["2026-10-05", "2026-10-07"]);
    expect(t.e1rmDelta!).toBeGreaterThan(0);
    expect(t.muscleRanking[0].name).toBe("quads");
    expect(t.completionPercent).toBe(100);
  });
});
