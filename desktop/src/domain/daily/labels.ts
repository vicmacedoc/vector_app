// Activity label management (ActivityRepository.createLabel/deleteLabel) as shard edits.
import type { ShardRecords } from "@/sync/shardEngine";
import { CONFIG_SHARD, D, K } from "@/sync/shards";
import { savedLabels, type ShardEdit } from "./day";
import { asJson, normLabel, type ActivityLabel } from "./models";
import { configRecords, putRecord } from "./repo";

type Shards = Record<string, ShardRecords>;

/** Before the first label write, persist the defaults the UI was showing so they don't vanish. */
function withSeed(shards: Shards, r: ShardRecords): ShardRecords {
  if (configRecords(shards, K.LABELS, normLabel).length) return r;
  return savedLabels(shards).reduce((acc, l) => putRecord(acc, K.LABELS, l.id, asJson(l)), r);
}

export function createLabelEdit(shards: Shards, name: string, colorArgb: number): { edit: ShardEdit; label: ActivityLabel } {
  const trimmed = name.trim();
  if (!trimmed) throw new Error("Label name cannot be empty");
  if (savedLabels(shards).some((l) => l.name.toLowerCase() === trimmed.toLowerCase())) throw new Error("A label with this name already exists");
  const label: ActivityLabel = { id: `label_${crypto.randomUUID()}`, name: trimmed, colorArgb: colorArgb | 0, isSystem: false, createdAt: Date.now() };
  return { label, edit: { shard: CONFIG_SHARD, fn: (r) => putRecord(withSeed(shards, r), K.LABELS, label.id, asJson(label)) } };
}

export function updateLabelEdit(shards: Shards, label: ActivityLabel, patch: { name?: string; colorArgb?: number }): ShardEdit {
  const name = patch.name?.trim() ?? label.name;
  if (!name) throw new Error("Label name cannot be empty");
  if (savedLabels(shards).some((l) => l.id !== label.id && l.name.toLowerCase() === name.toLowerCase())) {
    throw new Error("A label with this name already exists");
  }
  const next: ActivityLabel = { ...label, name, colorArgb: (patch.colorArgb ?? label.colorArgb) | 0 };
  return { shard: CONFIG_SHARD, fn: (r) => putRecord(withSeed(shards, r), K.LABELS, label.id, asJson(next)) };
}

export function activitiesWithLabel(shards: Shards, labelId: string): number {
  let n = 0;
  for (const [shard, records] of Object.entries(shards)) {
    if (!shard.startsWith("days/")) continue;
    for (const a of Object.values(records[D.ACTIVITIES] ?? {})) if (a.labelId === labelId) n++;
  }
  return n;
}

/** Deletes the label and, like the phone, every activity that uses it. System labels are kept. */
export function deleteLabelEdits(shards: Shards, label: ActivityLabel): ShardEdit[] {
  if (label.isSystem) throw new Error("System labels cannot be deleted");
  const edits: ShardEdit[] = [{ shard: CONFIG_SHARD, fn: (r) => putRecord(withSeed(shards, r), K.LABELS, label.id, null) }];
  for (const [shard, records] of Object.entries(shards)) {
    if (!shard.startsWith("days/")) continue;
    if (!Object.values(records[D.ACTIVITIES] ?? {}).some((a) => a.labelId === label.id)) continue;
    edits.push({
      shard,
      fn: (r) => ({ ...r, [D.ACTIVITIES]: Object.fromEntries(Object.entries(r[D.ACTIVITIES] ?? {}).filter(([, a]) => a.labelId !== label.id)) }),
    });
  }
  return edits;
}

export const argbToHex = (argb: number) => `#${((argb >>> 0) & 0xffffff).toString(16).padStart(6, "0")}`;
export const hexToArgb = (hex: string) => (0xff000000 | parseInt(hex.replace("#", ""), 16)) | 0;
