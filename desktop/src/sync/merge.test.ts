import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { emptyState, normState } from "@/domain/models";
import { analyze, canonical, resolve, type JsonObject, type RecordSet, type Resolution } from "./merge";
import { fromRecords, toRecords } from "./records";
import { DAILY_POLICY } from "./shards";

const FIXTURES = join(__dirname, "..", "..", "..", "sync-fixtures");

function recordSet(value: unknown): RecordSet {
  const out: RecordSet = new Map();
  if (!value || typeof value !== "object") return out;
  for (const [c, records] of Object.entries(value as Record<string, Record<string, JsonObject>>)) {
    out.set(c, new Map(Object.entries(records)));
  }
  return out;
}

describe("shared sync fixtures", () => {
  const files = readdirSync(FIXTURES).filter((f) => f.endsWith(".json")).sort();
  it("finds fixtures", () => expect(files.length).toBeGreaterThan(0));

  for (const file of files) {
    it(file, () => {
      const fx = JSON.parse(readFileSync(join(FIXTURES, file), "utf8"));
      const analysis = analyze(recordSet(fx.base), recordSet(fx.local), recordSet(fx.remote));
      expect(analysis.localChanged).toBe(fx.expect.localChanged);
      expect(analysis.remoteChanged).toBe(fx.expect.remoteChanged);

      const conflicts = analysis.conflicts
        .map((c) => ({ id: c.id, kind: c.kind, fields: [...c.fields].sort() }))
        .sort((a, b) => a.id.localeCompare(b.id));
      const expected = (fx.expect.conflicts as { id: string; kind: string; fields: string[] }[])
        .map((c) => ({ ...c, fields: [...c.fields].sort() }))
        .sort((a, b) => a.id.localeCompare(b.id));
      expect(conflicts).toEqual(expected);

      expect(canonical(resolve(analysis, {}, "LOCAL"))).toBe(canonical(recordSet(fx.expect.resolvedLocal)));
      expect(canonical(resolve(analysis, {}, "REMOTE"))).toBe(canonical(recordSet(fx.expect.resolvedRemote)));
      if (fx.expect.resolvedBoth) {
        const both: Record<string, Resolution> = {};
        for (const c of analysis.conflicts) both[c.id] = "BOTH";
        expect(canonical(resolve(analysis, both))).toBe(canonical(recordSet(fx.expect.resolvedBoth)));
      }
    });
  }
});

describe("shared daily sync fixtures", () => {
  const dir = join(FIXTURES, "daily");
  const files = readdirSync(dir).filter((f) => f.endsWith(".json")).sort();
  it("finds fixtures", () => expect(files.length).toBeGreaterThan(0));

  for (const file of files) {
    it(file, () => {
      const fx = JSON.parse(readFileSync(join(dir, file), "utf8"));
      const analysis = analyze(recordSet(fx.base), recordSet(fx.local), recordSet(fx.remote), DAILY_POLICY, fx.stamps);
      expect(analysis.localChanged).toBe(fx.expect.localChanged);
      expect(analysis.remoteChanged).toBe(fx.expect.remoteChanged);
      const conflicts = analysis.conflicts
        .map((c) => ({ id: c.id, kind: c.kind, fields: [...c.fields].sort() }))
        .sort((a, b) => a.id.localeCompare(b.id));
      expect(conflicts).toEqual(fx.expect.conflicts);
      expect(canonical(resolve(analysis, {}, "LOCAL"))).toBe(canonical(recordSet(fx.expect.resolvedLocal)));
      expect(canonical(resolve(analysis, {}, "REMOTE"))).toBe(canonical(recordSet(fx.expect.resolvedRemote)));
    });
  }
});

describe("records", () => {
  it("round-trips a state and keys duplicate supermarket titles", () => {
    const state = normState({
      ...emptyState(),
      nodes: [{ id: "node_1", section: "MEMORY", name: "Books", createdAt: 1 }],
      notes: [{ id: "note_1", nodeId: "node_1", kind: "TEXT", createdAt: 1, updatedAt: 2 }],
      supermarket: {
        name: "Supermarket",
        sections: ["Dairy"],
        items: [{ title: "Milk", quantity: 2, category: "Dairy" }, { title: "milk ", quantity: 1 }],
      },
    });
    const records = toRecords(state);
    expect([...records.get("supermarketItems")!.keys()]).toEqual(["milk", "milk#2"]);
    const back = fromRecords(records, state);
    expect(canonical(toRecords(back))).toBe(canonical(records));
    expect(back.supermarket.items.map((i) => i.title)).toEqual(["Milk", "milk "]);
  });

  it("fills Kotlin defaults so a sparse record equals the phone's full one", () => {
    const sparse = normState({ tasks: [{ id: "t", title: "x", priority: "P1", createdAt: 5, updatedAt: 5 }] });
    expect(sparse.tasks[0]).toMatchObject({ priority: "HIGH", points: 20, status: "OPEN", firstDueAt: null, progress: 0 });
  });
});
