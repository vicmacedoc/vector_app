// Cases ported from mobile/src/test/.../MotionScoringTest.kt plus Supermarket and Learn rules.
import { describe, expect, it } from "vitest";
import { addMonthsClamped, dayOfWeek } from "./dates";
import { wouldCycle } from "./learn";
import { basePoints, normItem, parsePriority } from "./models";
import { nextOccurrence, pointsAt, streak, supermarketCycle, supermarketPoints } from "./motionScoring";
import { needsBuy, sortedIndices, urgency } from "./supermarketMath";

const millis = (date: string, hour = 12) => Date.parse(`${date}T${String(hour).padStart(2, "0")}:00:00Z`);
const points = (base: number, created: string, due: string | null, firstDue: string | null, done: string) =>
  pointsAt(base, millis(created), due ? millis(due) : null, firstDue ? millis(firstDue) : null, millis(done), true);

describe("MotionScoring", () => {
  it("base points follow priority", () => {
    expect([basePoints("NONE"), basePoints("LOW"), basePoints("MEDIUM"), basePoints("HIGH")]).toEqual([5, 10, 15, 20]);
  });

  it("maps legacy priority names", () => {
    expect(["P1", "P2", "P3", "P4", "medium"].map(parsePriority)).toEqual(["HIGH", "MEDIUM", "LOW", "NONE", "MEDIUM"]);
  });

  it("early bonus per day, capped at seven", () => {
    expect(points(15, "2026-10-01", "2026-10-05", "2026-10-05", "2026-10-05")).toBe(15);
    expect(points(15, "2026-10-01", "2026-10-05", "2026-10-05", "2026-10-02")).toBe(18);
    expect(points(15, "2026-09-01", "2026-10-30", "2026-10-30", "2026-10-02")).toBe(22);
  });

  it("pushing a deadline out never adds bonus", () => {
    expect(points(10, "2026-10-01", "2026-10-20", "2026-10-05", "2026-10-03")).toBe(12);
    expect(points(10, "2026-10-01", "2026-10-20", "2026-10-05", "2026-10-10")).toBe(10);
  });

  it("late penalty floors at half base", () => {
    expect(points(20, "2026-10-01", "2026-10-05", "2026-10-05", "2026-10-07")).toBe(18);
    expect(points(20, "2026-10-01", "2026-10-05", "2026-10-05", "2026-11-30")).toBe(10);
    expect(points(5, "2026-10-01", "2026-10-05", "2026-10-05", "2026-10-30")).toBe(3);
  });

  it("no deadline ages five per week, capped at twice base", () => {
    expect(points(10, "2026-10-01", null, null, "2026-10-07")).toBe(10);
    expect(points(10, "2026-10-01", null, null, "2026-10-08")).toBe(15);
    expect(points(10, "2026-01-01", null, null, "2026-10-08")).toBe(30);
  });

  it("first supermarket cycle is the next due day", () => {
    expect(supermarketCycle(null, "2026-10-07", "SUNDAY")).toEqual({ dueDate: "2026-10-11", open: true });
  });

  it("cycle stays closed after a trip until the next window", () => {
    expect(supermarketCycle("2026-10-11", "2026-10-09", "SUNDAY")).toEqual({ dueDate: "2026-10-18", open: false });
    expect(supermarketCycle("2026-10-11", "2026-10-12", "SUNDAY").open).toBe(true);
  });

  it("late trips lose five per day and skipped weeks roll", () => {
    const cycle = supermarketCycle("2026-10-04", "2026-10-13", "SUNDAY");
    expect(cycle.dueDate).toBe("2026-10-11");
    expect(supermarketPoints(cycle.dueDate, "2026-10-13")).toBe(10);
    expect(supermarketPoints(cycle.dueDate, "2026-10-17")).toBe(0);
    expect(supermarketPoints(cycle.dueDate, "2026-10-08")).toBe(20);
  });

  it("streak counts consecutive days meeting the target", () => {
    const byDate = new Map([["2026-10-10", 20], ["2026-10-09", 60], ["2026-10-08", 50], ["2026-10-07", 10]]);
    expect(streak(byDate, "2026-10-10", 50)).toBe(2);
    byDate.set("2026-10-10", 55);
    expect(streak(byDate, "2026-10-10", 50)).toBe(3);
  });

  it("monthly recurrence clamps to the month end like Calendar.add", () => {
    const jan31 = new Date(2026, 0, 31, 9, 0).getTime();
    expect(new Date(nextOccurrence(jan31, "MONTHLY")).getDate()).toBe(28);
    expect(new Date(addMonthsClamped(jan31, 2)).getMonth()).toBe(2);
    expect(dayOfWeek("2026-10-04")).toBe("SUNDAY");
  });
});

describe("SupermarketMath", () => {
  const item = (o: Partial<Parameters<typeof normItem>[0]>) => normItem({ title: "x", ...o });

  it("urgency from remaining over quantity", () => {
    expect(urgency(item({ quantity: 4, remaining: 1 }))).toBe("URGENT");
    expect(urgency(item({ quantity: 4, remaining: 2 }))).toBe("LOW");
    expect(urgency(item({ quantity: 4, remaining: 3 }))).toBe("STOCKED");
    expect(needsBuy(item({ quantity: null, remaining: null, isChecked: true }))).toBe(false);
  });

  it("colliding legacy orders fall back to the aisle route", () => {
    const list = {
      name: "S",
      sections: ["Produce", "Dairy"],
      items: [
        item({ title: "Milk", category: "Dairy", order: 0 }),
        item({ title: "Apples", category: "Produce", order: 0 }),
      ],
    };
    expect(sortedIndices(list)).toEqual([1, 0]);
    list.items[0].order = 0;
    list.items[1].order = 1;
    expect(sortedIndices(list)).toEqual([0, 1]);
  });
});

describe("Learn", () => {
  it("refuses links that would close a cycle", () => {
    const links = [{ fromNodeId: "a", toNodeId: "b" }, { fromNodeId: "b", toNodeId: "c" }];
    expect(wouldCycle(links, "c", "a")).toBe(true);
    expect(wouldCycle(links, "a", "c")).toBe(false);
    expect(wouldCycle(links, "a", "a")).toBe(true);
  });
});
