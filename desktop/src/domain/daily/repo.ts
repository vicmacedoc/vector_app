// Typed reads and pure writes over shard records (docs/daily-sync-spec.md).
import type { JsonObject } from "@/sync/merge";
import type { ShardRecords } from "@/sync/shardEngine";
import { CONFIG_SHARD, monthShard } from "@/sync/shards";

type Norm<T> = (r: Record<string, unknown>) => T;

export function dayRecords<T>(shards: Record<string, ShardRecords>, collection: string, date: string, norm: Norm<T>): T[] {
  const byKey = shards[monthShard(date)]?.[collection];
  if (!byKey) return [];
  return Object.values(byKey).filter((r) => r.date === date).map((r) => norm(r));
}

export function dayRecord<T>(shards: Record<string, ShardRecords>, collection: string, date: string, norm: Norm<T>): T | null {
  const r = shards[monthShard(date)]?.[collection]?.[date];
  return r ? norm(r) : null;
}

export function allDayRecords<T>(shards: Record<string, ShardRecords>, collection: string, norm: Norm<T>): T[] {
  const out: T[] = [];
  for (const [shard, records] of Object.entries(shards)) {
    if (!shard.startsWith("days/")) continue;
    for (const r of Object.values(records[collection] ?? {})) out.push(norm(r));
  }
  return out;
}

/** Records with date in [from, to] (inclusive, YYYY-MM-DD). */
export function rangeRecords<T extends { date: string }>(
  shards: Record<string, ShardRecords>, collection: string, from: string, to: string, norm: Norm<T>,
): T[] {
  return allDayRecords(shards, collection, norm).filter((r) => r.date >= from && r.date <= to);
}

export function configRecords<T>(shards: Record<string, ShardRecords>, collection: string, norm: Norm<T>): T[] {
  return Object.values(shards[CONFIG_SHARD]?.[collection] ?? {}).map((r) => norm(r));
}

export function configRecord<T>(shards: Record<string, ShardRecords>, collection: string, key: string, norm: Norm<T>): T | null {
  const r = shards[CONFIG_SHARD]?.[collection]?.[key];
  return r ? norm(r) : null;
}

/** Replaces every record of [collection] dated [date] with [items]. */
export function replaceForDate<T extends object>(
  records: ShardRecords, collection: string, date: string, items: T[], key: (t: T) => string,
): ShardRecords {
  const kept: Record<string, JsonObject> = {};
  for (const [k, r] of Object.entries(records[collection] ?? {})) if (r.date !== date) kept[k] = r;
  for (const item of items) kept[key(item)] = item as unknown as JsonObject;
  return { ...records, [collection]: kept };
}

export function putRecord<T extends object>(records: ShardRecords, collection: string, key: string, value: T | null): ShardRecords {
  const next = { ...(records[collection] ?? {}) };
  if (value === null) delete next[key];
  else next[key] = value as unknown as JsonObject;
  return { ...records, [collection]: next };
}

export function datesWith(shards: Record<string, ShardRecords>, collection: string, pred?: (r: JsonObject) => boolean): Set<string> {
  const out = new Set<string>();
  for (const [shard, records] of Object.entries(shards)) {
    if (!shard.startsWith("days/")) continue;
    for (const r of Object.values(records[collection] ?? {})) {
      if (typeof r.date === "string" && (!pred || pred(r))) out.add(r.date);
    }
  }
  return out;
}
