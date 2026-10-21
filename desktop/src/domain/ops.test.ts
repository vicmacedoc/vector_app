import { describe, expect, it } from "vitest";
import { emptyState } from "./models";
import * as ops from "./ops";

describe("ops", () => {
  it("check spawns the next occurrence and uncheck removes it with the points", () => {
    const due = new Date(2026, 9, 5, 9).getTime();
    const [s1, id] = ops.createTask(emptyState(), { title: "Gym", priority: "MEDIUM", dueAt: due, recurrence: "WEEKLY" }, null);
    const s2 = ops.checkTask(s1, id, due);
    expect(s2.tasks).toHaveLength(2);
    const done = s2.tasks.find((t) => t.id === id)!;
    expect(done.status).toBe("DONE");
    expect(done.earnedPoints).toBe(15);
    expect(s2.scoreEvents.map((e) => e.id)).toEqual([`task_${id}`]);
    const spawned = s2.tasks.find((t) => t.id === done.spawnedTaskId)!;
    expect(new Date(spawned.dueAt!).getDate()).toBe(12);

    const s3 = ops.uncheckTask(s2, id);
    expect(s3.tasks).toHaveLength(1);
    expect(s3.scoreEvents).toHaveLength(0);
    expect(s3.tasks[0].status).toBe("OPEN");
  });

  it("numeric items check at their target", () => {
    const [s1, id] = ops.createTask(emptyState(), { title: "Read", target: 30, unit: "pages" }, null);
    expect(ops.setProgress(s1, id, 10).tasks[0].status).toBe("OPEN");
    const s2 = ops.setProgress(s1, id, 30);
    expect(s2.tasks[0].status).toBe("DONE");
    expect(ops.setProgress(s2, id, 12).tasks[0]).toMatchObject({ status: "OPEN", progress: 12 });
  });

  it("deleting a tile removes its sources, assets, Learn row and links", () => {
    let [s, a] = ops.createNode(emptyState(), "MODEL", "Algebra");
    let b: string;
    [s, b] = ops.createNode(s, "MODEL", "Calculus");
    s = ops.addLearningLink(s, b, a)!;
    expect(ops.addLearningLink(s, a, b)).toBeNull();
    [s] = ops.createAssetNote(s, { assetId: "asset_1", fileName: "notes.pdf", mimeType: "application/pdf", sizeBytes: 3 }, a);
    expect(s.assets[0].path).toBe(`Model/${a}/notes.pdf`);
    expect(s.learningTiles.find((t) => t.id === a)!.status).toBe("IN_PROGRESS");
    s = ops.deleteTile(s, a);
    expect(s.notes).toHaveLength(0);
    expect(s.assets).toHaveLength(0);
    expect(s.learningLinks).toHaveLength(0);
    expect(s.learningTiles.map((t) => t.id)).toEqual([b]);
  });

  it("logs one supermarket trip per cycle", () => {
    const now = new Date(2026, 9, 4, 12).getTime(); // a Sunday, the default due day
    const first = ops.recordSupermarketTrip(emptyState(), now)!;
    expect(first[1]).toBe(20);
    expect(ops.recordSupermarketTrip(first[0], now)).toBeNull();
  });
});
