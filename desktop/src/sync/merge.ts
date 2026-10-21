// 3-way merge over record sets. Port of mobile/.../data/sync/BrainMerge.kt; both pass sync-fixtures/.
// Spec: docs/brain-sync-spec.md

export type Json = null | boolean | number | string | Json[] | JsonObject;
export interface JsonObject {
  [key: string]: Json;
}
export type Records = Map<string, JsonObject>;
export type RecordSet = Map<string, Records>;

export type ConflictKind = "EDIT_EDIT" | "ADD_ADD" | "DELETE_EDIT" | "EDIT_DELETE";
export type Resolution = "LOCAL" | "REMOTE" | "BOTH";

export const C = {
  NODES: "nodes",
  NOTES: "notes",
  ASSETS: "assets",
  LEARNING_TILES: "learningTiles",
  LEARNING_LINKS: "learningLinks",
  TAGS: "tags",
  TASKS: "tasks",
  SCORE_EVENTS: "scoreEvents",
  SUPERMARKET_ITEMS: "supermarketItems",
  META: "meta",
} as const;

export const KEEP_BOTH_COLLECTIONS = new Set<string>([C.NOTES, C.TASKS]);

export type Side = "LOCAL" | "REMOTE";

/** Collection rules for one sync file. Brain and the daily shards differ only here (docs/daily-sync-spec.md). */
export interface MergePolicy {
  /** Collections offering "Keep both". */
  keepBoth: ReadonlySet<string>;
  /**
   * Collection -> stamp field. Records there never conflict: an edit beats a delete, between edits the
   * higher stamp wins (tie -> local).
   */
  newestRecordWins: Readonly<Record<string, string>>;
  /** Collections whose same-field conflicts resolve to the newer side, except the fields listed in [textFields]. */
  autoFields: ReadonlySet<string>;
  textFields: Readonly<Record<string, ReadonlySet<string>>>;
  finalize(set: RecordSet, remote: RecordSet): RecordSet;
}

/** When each side last edited the file; drives "newer side" picks. Ties favour local. */
export interface SideStamps {
  local: number;
  remote: number;
}

export interface MergeConflict {
  collection: string;
  key: string;
  id: string;
  kind: ConflictKind;
  fields: string[];
  base: JsonObject | null;
  local: JsonObject | null;
  remote: JsonObject | null;
  /** Field-merged record holding local values in `fields`; null for delete conflicts. */
  merged: JsonObject | null;
  canKeepBoth: boolean;
  /** Side that edited last; the merge dialog preselects it. */
  newer: Side;
}

export interface MergeAnalysis {
  localChanged: boolean;
  remoteChanged: boolean;
  localChanges: number;
  remoteChanges: number;
  conflicts: MergeConflict[];
  local: RecordSet;
  remote: RecordSet;
  autoMerged: RecordSet;
  order: Map<string, string[]>;
  policy: MergePolicy;
}

function orderedUnion(...lists: Iterable<string>[]): string[] {
  const seen = new Set<string>();
  for (const l of lists) for (const k of l) seen.add(k);
  return [...seen];
}

const get = (set: RecordSet, c: string): Records => set.get(c) ?? new Map();

/** Deep equality: numbers by value, a missing field equals null. */
export function sameJson(a: Json | undefined, b: Json | undefined): boolean {
  const x = a ?? null;
  const y = b ?? null;
  if (x === null || y === null) return x === null && y === null;
  if (Array.isArray(x)) {
    return Array.isArray(y) && x.length === y.length && x.every((v, i) => sameJson(v, y[i]));
  }
  if (typeof x === "object") {
    if (typeof y !== "object" || Array.isArray(y)) return false;
    return orderedUnion(Object.keys(x), Object.keys(y)).every((k) => sameJson(x[k], y[k]));
  }
  return x === y;
}

export function sameRecord(a: JsonObject | null | undefined, b: JsonObject | null | undefined): boolean {
  if (!a || !b) return !a && !b;
  return sameJson(a, b);
}

function canonicalValue(v: Json): Json {
  if (Array.isArray(v)) return v.map(canonicalValue);
  if (v && typeof v === "object") {
    const out: JsonObject = {};
    for (const k of Object.keys(v).sort()) {
      const val = v[k];
      if (val !== null && val !== undefined) out[k] = canonicalValue(val);
    }
    return out;
  }
  return v;
}

/** Sorted collections/keys/fields with null fields dropped. */
export function canonical(set: RecordSet): string {
  const root: JsonObject = {};
  for (const c of [...set.keys()].sort()) {
    const records = get(set, c);
    if (records.size === 0) continue;
    const sorted: JsonObject = {};
    for (const k of [...records.keys()].sort()) sorted[k] = canonicalValue(records.get(k)!);
    root[c] = sorted;
  }
  return JSON.stringify(root);
}

export function hasContent(set: RecordSet): boolean {
  for (const [c, records] of set) if (c !== C.META && records.size > 0) return true;
  return false;
}

function mergeFields(
  base: JsonObject | null,
  local: JsonObject,
  remote: JsonObject,
  autoNewer: Side | null,
  textFields: ReadonlySet<string> | undefined,
): [JsonObject, string[]] {
  const out: JsonObject = {};
  const conflicting: string[] = [];
  for (const f of orderedUnion(Object.keys(local), Object.keys(remote), Object.keys(base ?? {}))) {
    const bv = base?.[f];
    const lv = local[f];
    const rv = remote[f];
    let pick: Json | undefined;
    if (sameJson(lv, rv)) pick = lv;
    else if (sameJson(lv, bv)) pick = rv;
    else if (sameJson(rv, bv)) pick = lv;
    else if (autoNewer && !textFields?.has(f)) pick = autoNewer === "REMOTE" ? rv : lv;
    else {
      conflicting.push(f);
      pick = lv;
    }
    out[f] = pick ?? null;
  }
  return [out, conflicting];
}

function newerRecord(local: JsonObject | null, remote: JsonObject | null, field: string): JsonObject | null {
  if (!local) return remote;
  if (!remote) return local;
  const lt = typeof local[field] === "number" ? (local[field] as number) : 0;
  const rt = typeof remote[field] === "number" ? (remote[field] as number) : 0;
  return rt > lt ? remote : local;
}

export const BRAIN_POLICY: MergePolicy = {
  keepBoth: KEEP_BOTH_COLLECTIONS,
  newestRecordWins: { [C.SCORE_EVENTS]: "createdAt" },
  autoFields: new Set(),
  textFields: {},
  finalize: (set, remote) => finalize(set, remote),
};

export function analyze(
  base: RecordSet,
  local: RecordSet,
  remote: RecordSet,
  policy: MergePolicy = BRAIN_POLICY,
  stamps: SideStamps = { local: 0, remote: 0 },
): MergeAnalysis {
  const newer: Side = stamps.remote > stamps.local ? "REMOTE" : "LOCAL";
  const merged: RecordSet = new Map();
  const order = new Map<string, string[]>();
  const conflicts: MergeConflict[] = [];
  let localChanges = 0;
  let remoteChanges = 0;

  for (const collection of orderedUnion(local.keys(), remote.keys(), base.keys())) {
    const b = get(base, collection);
    const l = get(local, collection);
    const r = get(remote, collection);
    const out: Records = new Map();
    const keys = orderedUnion(l.keys(), r.keys(), b.keys());
    for (const key of keys) {
      const bv = b.get(key) ?? null;
      const lv = l.get(key) ?? null;
      const rv = r.get(key) ?? null;
      if (!sameRecord(lv, bv)) localChanges++;
      if (!sameRecord(rv, bv)) remoteChanges++;
      const conflict = (kind: ConflictKind, fields: string[], m: JsonObject | null) =>
        conflicts.push({
          collection, key, id: `${collection}/${key}`, kind, fields,
          base: bv, local: lv, remote: rv, merged: m,
          canKeepBoth: policy.keepBoth.has(collection),
          newer,
        });
      let pick: JsonObject | null = null;
      if (sameRecord(lv, rv)) pick = lv;
      else if (sameRecord(lv, bv)) pick = rv;
      else if (sameRecord(rv, bv)) pick = lv;
      else if (policy.newestRecordWins[collection]) pick = newerRecord(lv, rv, policy.newestRecordWins[collection]);
      else if (lv && rv) {
        const auto = policy.autoFields.has(collection) ? newer : null;
        const [fieldMerged, conflicting] = mergeFields(bv, lv, rv, auto, policy.textFields[collection]);
        if (conflicting.length === 0) pick = fieldMerged;
        else conflict(bv ? "EDIT_EDIT" : "ADD_ADD", conflicting, fieldMerged);
      } else if (!lv) conflict("DELETE_EDIT", [], null);
      else conflict("EDIT_DELETE", [], null);
      if (pick) out.set(key, pick);
    }
    merged.set(collection, out);
    order.set(collection, keys);
  }

  return {
    localChanged: canonical(local) !== canonical(base),
    remoteChanged: canonical(remote) !== canonical(base),
    localChanges,
    remoteChanges,
    conflicts,
    local,
    remote,
    autoMerged: merged,
    order,
    policy,
  };
}

function withFields(target: JsonObject, fields: string[], source: JsonObject | null): JsonObject {
  const out = { ...target };
  for (const f of fields) out[f] = source?.[f] ?? null;
  return out;
}

function copyKey(key: string, taken: Set<string>): string {
  let candidate = `${key}_copy`;
  let n = 2;
  while (taken.has(candidate)) candidate = `${key}_copy${n++}`;
  return candidate;
}

/** Smart merge: auto-merged records plus one resolution per conflict id (`fallback` when missing). */
export function resolve(
  analysis: MergeAnalysis,
  resolutions: Record<string, Resolution>,
  fallback: Resolution = "LOCAL",
): RecordSet {
  const byId = new Map(analysis.conflicts.map((c) => [c.id, c]));
  const out: RecordSet = new Map();
  for (const [collection, keys] of analysis.order) {
    const auto = get(analysis.autoMerged, collection);
    const records: Records = new Map();
    const copies: [string, JsonObject][] = [];
    for (const key of keys) {
      const conflict = byId.get(`${collection}/${key}`);
      if (!conflict) {
        const v = auto.get(key);
        if (v) records.set(key, v);
        continue;
      }
      let choice = resolutions[conflict.id] ?? fallback;
      if (choice === "BOTH" && !conflict.canKeepBoth) choice = "LOCAL";
      switch (conflict.kind) {
        case "EDIT_EDIT":
        case "ADD_ADD": {
          const merged = conflict.merged;
          if (!merged) break;
          if (choice === "REMOTE") records.set(key, withFields(merged, conflict.fields, conflict.remote));
          else records.set(key, merged);
          if (choice === "BOTH" && conflict.remote) copies.push([key, conflict.remote]);
          break;
        }
        case "DELETE_EDIT":
          if (choice !== "LOCAL" && conflict.remote) records.set(key, conflict.remote);
          break;
        case "EDIT_DELETE":
          if (choice !== "REMOTE" && conflict.local) records.set(key, conflict.local);
          break;
      }
    }
    for (const [key, record] of copies) {
      const taken = new Set([...records.keys(), ...keys]);
      const k = copyKey(key, taken);
      records.set(k, "id" in record ? { ...record, id: k } : record);
    }
    out.set(collection, records);
  }
  return analysis.policy.finalize(out, analysis.remote);
}

/** "Keep this device" / "Keep the other device": one side as-is, then the clean-up passes. */
export function whole(chosen: RecordSet, remote: RecordSet, policy: MergePolicy = BRAIN_POLICY): RecordSet {
  return policy.finalize(chosen, remote);
}

const text = (v: Json | undefined): string | null => (v === null || v === undefined || typeof v === "object" ? null : String(v));

export function finalize(set: RecordSet, remote: RecordSet): RecordSet {
  const out: RecordSet = new Map();
  for (const [c, records] of set) out.set(c, new Map(records));
  dedupeDriveFiles(out, remote);
  enforceIntegrity(out);
  return out;
}

function minKey(keys: string[]): string {
  return keys.reduce((a, b) => (b < a ? b : a));
}

function dedupeDriveFiles(out: RecordSet, remote: RecordSet) {
  const assets = out.get(C.ASSETS);
  if (!assets) return;
  const remoteAssets = get(remote, C.ASSETS);
  const groups = new Map<string, string[]>();
  for (const [key, asset] of assets) {
    const drive = text(asset.driveFileId);
    if (!drive) continue;
    groups.set(drive, [...(groups.get(drive) ?? []), key]);
  }
  const replaced = new Map<string, string>();
  for (const keys of groups.values()) {
    if (keys.length < 2) continue;
    const keep = keys.find((k) => remoteAssets.has(k)) ?? minKey(keys);
    for (const k of keys) if (k !== keep) replaced.set(k, keep);
  }
  if (replaced.size === 0) return;
  for (const k of replaced.keys()) assets.delete(k);

  const notes = out.get(C.NOTES);
  if (!notes) return;
  for (const [key, note] of [...notes]) {
    const target = replaced.get(text(note.assetId) ?? "");
    if (target) notes.set(key, { ...note, assetId: target });
  }
  const remoteNotes = get(remote, C.NOTES);
  const kept = new Set(replaced.values());
  const noteGroups = new Map<string, string[]>();
  for (const [key, note] of notes) {
    const assetId = text(note.assetId);
    if (!assetId || !kept.has(assetId)) continue;
    const g = JSON.stringify([assetId, text(note.nodeId), text(note.kind)]);
    noteGroups.set(g, [...(noteGroups.get(g) ?? []), key]);
  }
  for (const keys of noteGroups.values()) {
    if (keys.length < 2) continue;
    const keep = keys.find((k) => remoteNotes.has(k)) ?? minKey(keys);
    for (const k of keys) if (k !== keep) notes.delete(k);
  }
}

function enforceIntegrity(out: RecordSet) {
  const nodeIds = new Set(get(out, C.NODES).keys());
  const clearMissing = (collection: string, ...fields: string[]) => {
    const records = out.get(collection);
    if (!records) return;
    for (const [key, record] of [...records]) {
      const stale = fields.filter((f) => {
        const v = text(record[f]);
        return v !== null && !nodeIds.has(v);
      });
      if (stale.length === 0) continue;
      const updated = { ...record };
      for (const f of stale) updated[f] = null;
      records.set(key, updated);
    }
  };
  clearMissing(C.NOTES, "nodeId");
  clearMissing(C.TASKS, "nodeId", "linkedTileId");
  const tiles = out.get(C.LEARNING_TILES);
  if (tiles) for (const k of [...tiles.keys()]) if (!nodeIds.has(k)) tiles.delete(k);
  const links = out.get(C.LEARNING_LINKS);
  if (links) {
    for (const [k, link] of [...links]) {
      const from = text(link.fromNodeId);
      const to = text(link.toNodeId);
      if (!from || !to || !nodeIds.has(from) || !nodeIds.has(to)) links.delete(k);
    }
  }
}
