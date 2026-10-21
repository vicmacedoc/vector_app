import { describe, expect, it } from "vitest";
import { CONFIG_SHARD, D, K } from "@/sync/shards";
import type { ShardRecords } from "@/sync/shardEngine";
import { decodeRoutinePlan, presetActivities, rangeMinutes, cappedDoneMinutes, parseActivityClipboard } from "./activity";
import { bakeForSave, foodDayTotals, groupByTime, parseFoodPlan, parseMealClipboard, presetFoodEntries, resolveAuditStatus } from "./food";
import { selectedHabitIds, setSelectedHabitsEdits, scoreSummaries } from "./habits";
import { DEFAULT_LABELS, normActivity, normFood, normSleep, normWorkoutSet } from "./models";
import { sameFood, sameRoutine } from "./drafts";
import { draftToSession, normalizeRpe, normalizeTiming, sessionToDraft, normalizedForImport, exerciseDraftError, emptyExercise } from "./exerciseDraft";
import { applySleepEstimate, latencyMinutes, setStagePercent, totalSleepMinutes } from "./sleep";
import { applyScale, hasAnyContent, scaleFromId, writtenWordCount } from "./self";
import {
  circularMeanMinutes, estimatedOneRepMax, halfSplitDelta, linearTrend, pearson, summarizeExerciseDay,
  weekStartsMonday, longestConsecutiveTrue, trailingConsecutiveTrue,
} from "./stats";
import { formatHhMm, normalizeMealTimeOrBlank, parseHhMm } from "./time";
import {
  addedSessionRows, decodeWorkoutPlan, formatMuscles, parseClipboardSessions, parseMuscles, planToRows, rowsToSession,
} from "./workoutPlan";
import { normSelf } from "./models";

const food = (over: Record<string, unknown>) => normFood({ id: "f", date: "2026-10-19", name: "Oats", unit: "g", timestamp: 1, ...over });

describe("food", () => {
  it("resolves audit status like the phone", () => {
    expect(resolveAuditStatus(food({}))).toBe("UNPLANNED");
    const planned = food({ plannedTime: "08:00", plannedAmount: 100, time: "08:00", amount: 100 });
    expect(resolveAuditStatus(planned)).toBe("PLANNED");
    expect(resolveAuditStatus(planned, 1.5)).toBe("ADJUSTED");
    expect(resolveAuditStatus({ ...planned, time: "09:00" })).toBe("ADJUSTED");
  });

  it("bakes the multiplier on save but keeps the planned baseline", () => {
    const e = food({ plannedTime: "08:00", plannedAmount: 100, time: "08:00", amount: 100, kcal: 300, quantityMultiplier: 1.5 });
    const baked = bakeForSave(e);
    expect(baked).toMatchObject({ kcal: 450, amount: 150, quantityMultiplier: 1, status: "ADJUSTED" });
    expect(foodDayTotals([{ ...baked, isChecked: true }])).toMatchObject({ got: { kcal: 450 }, planned: { kcal: 300 } });
  });

  it("expands a weekly preset with stable ids", () => {
    const plan = parseFoodPlan(JSON.stringify({
      name: "P", weekly_plan: { monday: [{ id: "m1", time: "08:00", name: "Eggs", kcal: 200, amount: 2, unit: "un" }, { time: "12:00", name: "Rice", kcal: 300, amount: 150, unit: "g" }] },
    }));
    const rows = presetFoodEntries(plan, "2026-10-19");
    expect(rows.map((r) => r.id)).toEqual(["m1_2026-10-19", "gen_1_2026-10-19"]);
    expect(groupByTime(rows).map((g) => g.time)).toEqual(["08:00", "12:00"]);
  });

  it("parses clipboard meals per mode", () => {
    expect(parseMealClipboard('{"name":"A","amount":10,"kcal":5,"plannedTime":"7:5"}', "SINGLE")[0].time).toBe("07:05");
    expect(() => parseMealClipboard('[{"name":"A","amount":1}]', "SINGLE")).toThrow(/list/);
    expect(parseMealClipboard('{"meals":[{"name":"A","amount":1},{"name":"B","amount":2}]}', "MULTIPLE")).toHaveLength(2);
    expect(normalizeMealTimeOrBlank("25:00")).toBe("");
  });
});

describe("activities", () => {
  it("handles overnight ranges and capped done minutes", () => {
    expect(rangeMinutes("23:00", "01:00")).toBe(120);
    const a = normActivity({ startTime: "09:00", endTime: "10:00", adjustedStartTime: "09:00", adjustedEndTime: "11:00" });
    expect(cappedDoneMinutes(a)).toBe(60);
  });

  it("decodes wrapped and bare routine presets and matches labels", () => {
    const bare = decodeRoutinePlan(JSON.stringify({ monday: [{ title: "Deep work", startTime: "9:0", endTime: "11:00", label: "work" }] }));
    const rows = presetActivities(bare, "2026-10-19", DEFAULT_LABELS);
    expect(rows[0]).toMatchObject({ id: "preset_2026-10-19_0", startTime: "09:00", labelId: "label_default_work", timestamp: 0, type: "PLANNED" });
    const wrapped = decodeRoutinePlan(JSON.stringify({ name: "x", weekly_plan: { tuesday: [] } }));
    expect(wrapped.name).toBe("x");
  });

  it("parses clipboard activities", () => {
    const [d] = parseActivityClipboard('{"title":"Run","startTime":"7:00","label":"none"}', "SINGLE");
    expect(d).toMatchObject({ startTime: "07:00", endTime: "10:00", labelName: null });
  });
});

describe("sleep", () => {
  const night = normSleep({ date: "2026-10-18", actualBedtime: "23:00", fellAsleepAt: "23:20", actualWakeUp: "07:00" });
  it("computes latency and total sleep", () => {
    expect(latencyMinutes(night)).toBe(20);
    expect(totalSleepMinutes(night)).toBe(460);
    expect(setStagePercent(night, "DEEP", 25).deepMinutes).toBe(115);
  });
  it("applies AI estimates with latency and percents", () => {
    const next = applySleepEstimate(normSleep({ date: "d" }), { actualBedtime: "22:30", actualWakeUp: "06:30", latencyMinutes: 30, remPercent: 20 });
    expect(next.fellAsleepAt).toBe("23:00");
    expect(next.remMinutes).toBe(90);
  });
  it("wraps clock parsing", () => {
    expect(parseHhMm("25:30")).toBe(90);
    expect(formatHhMm(-30)).toBe("23:30");
  });
});

describe("self", () => {
  it("maps scales and content", () => {
    const e = applyScale(scaleFromId("SLEEP")!, normSelf({ date: "d" }), 9);
    expect(e.sleepQuality).toBe(5);
    expect(hasAnyContent(e)).toBe(true);
    expect(writtenWordCount(normSelf({ journalText: " a  b ", highlight: "c" }))).toBe(3);
  });
});

describe("muscles and workout rows", () => {
  it("parses weighted and legacy targets", () => {
    expect(parseMuscles("chest,triceps")).toEqual({ chest: 1, triceps: 0.5 });
    expect(parseMuscles("back:1.0,back:0.5,Front Delts:0.5")).toEqual({ back: 1.5, front_delts: 0.5 });
    expect(formatMuscles({ back: 1, biceps: 0.5 })).toBe("back:1.0,biceps:0.5");
  });

  const plan = decodeWorkoutPlan(JSON.stringify({
    weekly_plan: {
      monday: [{
        title: "Push", type: "RESISTANCE", exercises: [
          { name: "Bench", target: ["chest", "triceps"], supersetGroup: "a", sets: [{ setNumber: 1, targetReps: 8, targetLoad: 100, drops: [{ dropNumber: 1, targetReps: 6, targetLoad: 80 }] }, { setNumber: 2, targetReps: 8 }] },
        ],
      }, {
        title: "Run", type: "ENDURANCE", sport: "run", exercises: [{ name: "Intervals", intervals: [{ intervalNumber: 1, targetDuration: 60, rest: 60 }, { intervalNumber: 2, targetDuration: 60 }] }],
      }],
    },
  }));

  it("builds rows with drop and interval ids", () => {
    const rows = planToRows(plan, "2026-10-19");
    expect(rows.map((r) => r.id)).toEqual([
      "2026-10-19_ex_0_0_1", "2026-10-19_ex_0_0_1_d1", "2026-10-19_ex_0_0_2", "2026-10-19_ex_1_0_1", "2026-10-19_ex_1_0_2",
    ]);
    expect(rows[0]).toMatchObject({ setType: "DROP", groupingId: "A", targetMuscles: "chest:1.0,triceps:0.5" });
    expect(rows[1]).toMatchObject({ dropNumber: 1, targetLoad: 80, unitLoad: "kg", targetRest: null });
    expect(rows[3]).toMatchObject({ setType: "INTERVAL", sport: "RUN", totalDuration: 3 });
  });

  it("round-trips rows to a session", () => {
    const rows = planToRows(plan, "2026-10-19").filter((r) => r.exerciseId === "ex_0_0");
    const session = rowsToSession(rows);
    expect(session.exercises[0].sets[0].drops[0]).toMatchObject({ dropNumber: 1, targetReps: 6 });
    expect(session.exercises[0].target).toEqual({ chest: 1, triceps: 0.5 });
  });

  it("summarizes the day with credits and added exercises", () => {
    const rows = planToRows(plan, "2026-10-19").map((r) => (r.exerciseId === "ex_0_0" && r.setNumber === 1 ? { ...r, isCompleted: true } : r));
    const added = addedSessionRows("2026-10-19", [{ title: "Extra", description: "", type: "OTHER", sport: null, exercises: [{ ...plan.weekly_plan.monday[1].exercises[0], intervals: [], totalDuration: 20 }] }], rows);
    expect(added[0].exerciseId).toBe("ex_added_0_0");
    const summary = summarizeExerciseDay([...rows, ...added.map((a) => ({ ...a, workoutStatus: "Done" }))]);
    const other = summary.byGroup.find((g) => g.group === "OTHER")!;
    expect(other.minutes).toEqual({ done: 20, planned: 0 });
    const chest = summary.muscles.find((m) => m.muscle === "chest")!;
    expect(chest.sets).toEqual({ done: 1, planned: 2 });
  });

  it("parses clipboard sessions per mode", () => {
    const s = parseClipboardSessions('[{"name":"Curl","sets":[{"targetReps":10}]}]', "SINGLE", "lbs");
    expect(s[0].exercises[0].sets[0].unitLoad).toBe("lbs");
    expect(() => parseClipboardSessions('[{"name":"Curl"}]', "MULTIPLE", "lbs")).toThrow(/one session/);
  });

  it("estimates 1RM only for completed main sets", () => {
    const set = normWorkoutSet({ actualLoad: 100, actualReps: 3, isCompleted: true });
    expect(estimatedOneRepMax(set)).toBeCloseTo(110);
    expect(estimatedOneRepMax({ ...set, dropNumber: 1 })).toBeNull();
  });
});

describe("exercise drafts", () => {
  it("normalizes tempo and RPE", () => {
    expect(normalizeTiming("3:1:1")).toBe("3-1-1");
    expect(normalizeTiming("fast")).toBeNull();
    expect(normalizeRpe(7.3)).toBe(7.5);
  });
  it("converts sessions both ways", () => {
    const draft = sessionToDraft({ title: "", description: "", type: "OTHER", sport: null, exercises: [{ ...emptyPlan(), name: "Yoga", totalDuration: 30 }] });
    const [norm] = normalizedForImport([draft]);
    expect(norm.title).toBe("Other: Added session");
    expect(exerciseDraftError(norm.exercises[0], "OTHER")).toBeNull();
    expect(exerciseDraftError(emptyExercise(), "RESISTANCE")).toMatch(/name/);
    expect(draftToSession(norm).exercises[0]).toMatchObject({ totalDuration: 30, unitDuration: "min", sets: [] });
  });
});

function emptyPlan() {
  return decodeWorkoutPlan(JSON.stringify({ weekly_plan: { monday: [{ exercises: [{}] }] } })).weekly_plan.monday[0].exercises[0];
}

describe("drafts", () => {
  it("ignores order and timestamps", () => {
    const a = food({ id: "a", timestamp: 1 });
    const b = food({ id: "b", timestamp: 2 });
    expect(sameFood({ entries: [a, b] }, { entries: [{ ...b, timestamp: 9 }, a] })).toBe(true);
    expect(sameRoutine({ activities: [], previousNight: normSleep({ date: "d" }), tonight: null }, { activities: [], previousNight: null, tonight: null })).toBe(true);
  });
});

describe("habits", () => {
  const shards: Record<string, ShardRecords> = {
    [CONFIG_SHARD]: { [K.HABITS]: { h1: { id: "h1", name: "Read", createdAt: 1 }, h2: { id: "h2", name: "Walk", createdAt: 2 } } },
    "days/2026-10": {
      [D.HABIT_CONFIGS]: { "2026-10-01": { date: "2026-10-01", habitIdsJson: '["h1","h2","gone"]', updatedAt: 1 } },
      [D.HABIT_LOGS]: {
        hlog_2026_10_05_h2: { id: "hlog_2026-10-05_h2", date: "2026-10-05", habitId: "h2", status: "DONE", timestamp: 1 },
        x: { id: "x", date: "2026-10-02", habitId: "h1", status: "EXCEEDED", timestamp: 1 },
      },
    },
  };

  it("inherits the latest config", () => {
    expect(selectedHabitIds(shards, "2026-10-10")).toEqual(["h1", "h2"]);
    expect(selectedHabitIds(shards, "2026-09-30")).toEqual([]);
    expect(scoreSummaries(shards).get("h1")).toEqual({ habitId: "h1", score: 1.5, total: 1 });
  });

  it("drops later configs and deselected logs", () => {
    let next = { ...shards };
    for (const e of setSelectedHabitsEdits(shards, "2026-10-04", ["h1"])) next = { ...next, [e.shard]: e.fn(next[e.shard] ?? {}) };
    expect(selectedHabitIds(next, "2026-10-20")).toEqual(["h1"]);
    const logs = Object.values(next["days/2026-10"][D.HABIT_LOGS]);
    expect(logs.some((l) => l.habitId === "h2")).toBe(false);
    expect(logs.some((l) => l.id === "hlog_2026-10-04_h1")).toBe(true);
  });
});

describe("stats", () => {
  it("handles streaks, trends and correlation", () => {
    expect(longestConsecutiveTrue([true, true, false, true])).toBe(2);
    expect(trailingConsecutiveTrue([true, false, true, true])).toBe(2);
    expect(linearTrend([1, null, 3])).toEqual([1, 2, 3]);
    expect(pearson([1, 2, 3, 4, 5], [2, 4, 6, 8, 10])).toBeCloseTo(1);
    expect(pearson([1, 2, 3, 4], [1, 2, 3, 4])).toBeNull();
    expect(halfSplitDelta([1, 1, 3, 3])).toBe(2);
  });
  it("averages clock times on the circle", () => {
    expect(circularMeanMinutes([23 * 60, 60])).toBe(0);
  });
  it("lists Monday week starts", () => {
    expect(weekStartsMonday("2026-10-21", "2026-10-27")).toEqual(["2026-10-19", "2026-10-26"]);
  });
});
