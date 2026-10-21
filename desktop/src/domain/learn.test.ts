import { describe, expect, it } from "vitest";
import { effectiveStatus, withReview, withStatus, type LearnRules } from "./learn";
import { buildLearningText, exportFileName } from "./learnContext";
import { emptyState, normNode, normNote, normTile } from "./models";

const DAY = 86_400_000;
const rules: LearnRules = { onHoldDays: 5, reviewIntervals: [2, 10] };

describe("Learn review ladder", () => {
  const now = Date.parse("2026-10-21T12:00:00Z");

  it("learned schedules the first interval", () => {
    const t = withStatus(normTile({ id: "a", status: "IN_PROGRESS" }), "LEARNED", rules, now);
    expect(t.nextReviewAt).toBe(now + 2 * DAY);
    expect(t.reviewStep).toBe(0);
  });

  it("solid climbs the ladder and masters after the last step", () => {
    const t0 = normTile({ id: "a", status: "LEARNED", reviewStep: 0, nextReviewAt: now });
    const t1 = withReview(t0, true, 0.9, rules, now);
    expect([t1.status, t1.reviewStep, t1.nextReviewAt, t1.lastReviewScore]).toEqual(["LEARNED", 1, now + 10 * DAY, 0.9]);
    const t2 = withReview(t1, true, null, rules, now);
    expect([t2.reviewStep, t2.nextReviewAt, t2.lastReviewScore]).toEqual([2, null, 0.9]);
  });

  it("shaky restarts the ladder in progress", () => {
    const t = withReview(normTile({ id: "a", status: "LEARNED", reviewStep: 1 }), false, 0.4, rules, now);
    expect([t.status, t.reviewStep, t.nextReviewAt, t.lastActivityAt]).toEqual(["IN_PROGRESS", 0, null, now]);
  });

  it("custom on-hold threshold", () => {
    const t = normTile({ id: "a", status: "IN_PROGRESS", lastActivityAt: now - 6 * DAY });
    expect(effectiveStatus(t, rules, now)).toBe("ON_HOLD");
    expect(effectiveStatus(t, { ...rules, onHoldDays: 14 }, now)).toBe("IN_PROGRESS");
  });
});

describe("Learning context", () => {
  it("lists notes, links and files like the phone", () => {
    const s = {
      ...emptyState(),
      nodes: [normNode({ id: "n1", section: "MODEL", name: "Linear algebra", createdAt: Date.parse("2026-01-02T12:00:00") }), normNode({ id: "n2", section: "MODEL", name: "Calculus" })],
      learningLinks: [{ fromNodeId: "n1", toNodeId: "n2", createdAt: 0 }],
      notes: [
        normNote({ id: "a", nodeId: "n1", kind: "TEXT", title: "Vectors", body: "Arrows.", createdAt: 1 }),
        normNote({ id: "b", nodeId: "n1", kind: "LINK", title: "Video", url: "https://x.y", createdAt: 2 }),
        normNote({ id: "c", nodeId: "n1", kind: "PDF", title: "Book", createdAt: 3 }),
      ],
    };
    const { text } = buildLearningText(s, "n1");
    expect(text).toContain("# Linear algebra\nCreated: 2026-01-02\nDepends on: Calculus");
    expect(text).toContain("## Vectors\nArrows.");
    expect(text).toContain("## Link: Video\nhttps://x.y");
    expect(text).toContain("## Files\n- Pdf: Book");
    expect(buildLearningText(s, "n1", { includePrerequisites: true }).text).toContain("PREREQUISITE\n" + "=".repeat(60) + "\n\n# Calculus");
  });

  it("export file names are safe", () => {
    expect(exportFileName(' a/b:c*  "d" ')).toBe("abc d.txt");
    expect(exportFileName("  ")).toBe("Learn tile.txt");
  });
});
