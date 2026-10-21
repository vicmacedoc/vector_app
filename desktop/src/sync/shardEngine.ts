// Keeps the daily shards (Data/sync/config.json, Data/sync/days/YYYY-MM.json) in step with Drive.
// Protocol: docs/daily-sync-spec.md; mirrors mobile ShardSync.kt.
import type { SyncDevice } from "@/domain/models";
import * as drive from "@/drive/client";
import { SignedOutError } from "@/drive/auth";
import { errorMessage, native } from "@/platform/native";
import { analyze, canonical, hasContent, resolve, whole, type MergeAnalysis, type Resolution } from "./merge";
import {
  CONFIG_SHARD, DAILY_POLICY, SHARD_FORMAT_VERSION, emptyShard, fromRecordSet, shardFileName, toRecordSet,
  type ShardFile,
} from "./shards";
import type { MergeChoice } from "./engine";

export type ShardRecords = ShardFile["records"];

export interface ShardBase extends ShardFile {
  driveVersion: string | null;
}

export interface ShardPending {
  remote: ShardFile;
  remoteVersion: string;
  detectedAt: number;
}

export interface ShardMergeView {
  shard: string;
  analysis: MergeAnalysis;
  remoteDevice: SyncDevice | null;
  remoteWrittenAt: number;
}

export interface ShardSyncReport {
  kind: "ok" | "notConfigured" | "signedOut" | "failed";
  pushed: number;
  pulled: number;
  /** Changes from the other device folded in silently. */
  merged: number;
  /** Shards waiting for a merge decision. */
  conflicts: string[];
  message?: string;
}

/** The working shards owned by the UI store. */
export interface ShardPort {
  get(shard: string): ShardRecords;
  /** Swaps in [next] only if the shard is still [expected]; false means the user edited meanwhile. */
  replace(shard: string, expected: ShardRecords, next: ShardRecords): boolean;
  knownShards(): string[];
  /** shard -> last local edit; absent means clean. */
  dirty(): Record<string, number>;
  clearDirty(shard: string, ifAt: number): void;
  rootFolderId(): string | null;
  device(): SyncDevice;
}

const fileKey = (shard: string) => shard.replace("/", "_");
export const shardStoreName = (shard: string) => `shard_${fileKey(shard)}.json`;
const baseName = (shard: string) => `shardbase_${fileKey(shard)}.json`;
const pendingName = (shard: string) => `shardpending_${fileKey(shard)}.json`;

async function readJson<T>(name: string): Promise<T | null> {
  const text = await native.storeRead(name);
  if (!text) return null;
  try {
    return JSON.parse(text) as T;
  } catch {
    return null;
  }
}

export const shardFiles = {
  readBase: (shard: string) => readJson<ShardBase>(baseName(shard)),
  writeBase: (shard: string, base: ShardBase) => native.storeWrite(baseName(shard), JSON.stringify(base)),
  readPending: (shard: string) => readJson<ShardPending>(pendingName(shard)),
  writePending: (shard: string, p: ShardPending) => native.storeWrite(pendingName(shard), JSON.stringify(p)),
  clearPending: (shard: string) => native.storeDelete(pendingName(shard)),
  async clearAll(shards: string[]) {
    for (const s of shards) {
      await native.storeDelete(baseName(s));
      await native.storeDelete(pendingName(s));
    }
  },
};

class Retry extends Error {}

interface Folders {
  syncId: string;
  daysId: string;
}

let chain: Promise<unknown> = Promise.resolve();
function locked<T>(fn: () => Promise<T>): Promise<T> {
  const run = chain.then(fn, fn);
  chain = run.catch(() => undefined);
  return run;
}

const MAX_ATTEMPTS = 3;

export class ShardEngine {
  constructor(private port: ShardPort) {}

  /** [fullScan] compares every known shard with its base even when nothing looks changed. */
  sync(fullScan = false): Promise<ShardSyncReport> {
    return locked(() => this.guard(() => this.syncLocked(fullScan)));
  }

  pendingShards(): Promise<string[]> {
    return locked(async () => {
      const out: string[] = [];
      for (const s of this.port.knownShards()) if (await shardFiles.readPending(s)) out.push(s);
      return out;
    });
  }

  loadPending(shard: string): Promise<ShardMergeView | null> {
    return locked(async () => {
      const pending = await shardFiles.readPending(shard);
      if (!pending) return null;
      return {
        shard,
        analysis: await this.analysis(shard, pending.remote),
        remoteDevice: pending.remote.writtenBy,
        remoteWrittenAt: pending.remote.writtenAt,
      };
    });
  }

  resolvePending(shard: string, choice: MergeChoice, resolutions: Record<string, Resolution> = {}): Promise<ShardSyncReport> {
    return locked(() => this.guard(() => this.resolveLocked(shard, choice, resolutions)));
  }

  private async guard(fn: () => Promise<ShardSyncReport>): Promise<ShardSyncReport> {
    try {
      return await fn();
    } catch (e) {
      if (e instanceof SignedOutError) return report("signedOut");
      return { ...report("failed"), message: errorMessage(e) };
    }
  }

  private async folders(): Promise<Folders | null> {
    const root = this.port.rootFolderId();
    if (!root) return null;
    const syncId = await drive.ensureFolder(root, "sync");
    const daysId = await drive.ensureFolder(syncId, "days");
    return { syncId, daysId };
  }

  private async remoteIndex(folders: Folders): Promise<Map<string, drive.DriveFile>> {
    const index = new Map<string, drive.DriveFile>();
    for (const f of await drive.listChildren(folders.syncId)) if (f.name === "config.json") index.set(CONFIG_SHARD, f);
    for (const f of await drive.listChildren(folders.daysId)) {
      const m = f.name.match(/^(\d{4}-\d{2})\.json$/);
      if (m) index.set(`days/${m[1]}`, f);
    }
    return index;
  }

  private async syncLocked(fullScan: boolean): Promise<ShardSyncReport> {
    const folders = await this.folders();
    if (!folders) return report("notConfigured");
    const index = await this.remoteIndex(folders);
    const out = report("ok");
    const shards = new Set([...this.port.knownShards(), ...index.keys()]);
    for (const shard of [...shards].sort()) {
      const dirtyAt = this.port.dirty()[shard];
      const base = await shardFiles.readBase(shard);
      const meta = index.get(shard) ?? null;
      const remoteMoved = meta ? meta.version !== base?.driveVersion : base !== null && base.driveVersion !== null;
      const pending = await shardFiles.readPending(shard);
      if (!fullScan && dirtyAt === undefined && base && !remoteMoved && !pending) continue;
      await this.syncShard(shard, folders, meta, out);
    }
    return out;
  }

  private async syncShard(shard: string, folders: Folders, firstMeta: drive.DriveFile | null, out: ShardSyncReport) {
    const parentId = shard === CONFIG_SHARD ? folders.syncId : folders.daysId;
    let meta = firstMeta;
    for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      const pending = await shardFiles.readPending(shard);
      if (pending) {
        await this.refreshPending(shard, pending, meta);
        out.conflicts.push(shard);
        return;
      }
      const dirtyAt = this.port.dirty()[shard];
      const base = await shardFiles.readBase(shard);
      const local = this.port.get(shard);
      const remote = meta ? (meta.version === base?.driveVersion ? base : await download(meta.id)) : null;

      const localSet = toRecordSet(local);
      const localChanged = base ? canonical(localSet) !== canonical(toRecordSet(base.records)) : hasContent(localSet);
      const remoteChanged = remote !== null && (base === null || remote.rev !== base.rev);

      try {
        if (!remote || !meta) {
          if (!localChanged) {
            if (dirtyAt !== undefined) this.port.clearDirty(shard, dirtyAt);
            return;
          }
          await this.push(shard, parentId, local, (base?.rev ?? 0) + 1, null);
          if (dirtyAt !== undefined) this.port.clearDirty(shard, dirtyAt);
          out.pushed++;
          return;
        }
        if (!localChanged && !remoteChanged) {
          if (dirtyAt !== undefined) this.port.clearDirty(shard, dirtyAt);
          if (base && base.driveVersion !== meta.version) await shardFiles.writeBase(shard, { ...base, driveVersion: meta.version ?? null });
          return;
        }
        if (localChanged && !remoteChanged) {
          await this.push(shard, parentId, local, remote.rev + 1, meta.version ?? null);
          if (dirtyAt !== undefined) this.port.clearDirty(shard, dirtyAt);
          out.pushed++;
          return;
        }
        if (!localChanged) {
          if (!this.port.replace(shard, local, remote.records)) throw new Retry();
          await shardFiles.writeBase(shard, { ...remote, driveVersion: meta.version ?? null });
          out.pulled++;
          return;
        }
        if (canonical(localSet) === canonical(toRecordSet(remote.records))) {
          await shardFiles.writeBase(shard, { ...remote, driveVersion: meta.version ?? null });
          if (dirtyAt !== undefined) this.port.clearDirty(shard, dirtyAt);
          return;
        }
        const analysis = analyze(
          base ? toRecordSet(base.records) : new Map(),
          localSet,
          toRecordSet(remote.records),
          DAILY_POLICY,
          { local: dirtyAt ?? Date.now(), remote: remote.writtenAt },
        );
        if (analysis.conflicts.length > 0) {
          await shardFiles.writePending(shard, { remote, remoteVersion: meta.version ?? "", detectedAt: Date.now() });
          out.conflicts.push(shard);
          return;
        }
        const merged = fromRecordSet(resolve(analysis, {}));
        if (!this.port.replace(shard, local, merged)) throw new Retry();
        await this.push(shard, parentId, merged, remote.rev + 1, meta.version ?? null);
        if (dirtyAt !== undefined) this.port.clearDirty(shard, dirtyAt);
        out.merged += analysis.remoteChanges;
        out.pushed++;
        return;
      } catch (e) {
        if (!(e instanceof Retry)) throw e;
        meta = await drive.findChild(parentId, shardFileName(shard), false);
      }
    }
    throw new Error("Drive kept changing during sync; will retry");
  }

  private async refreshPending(shard: string, pending: ShardPending, meta: drive.DriveFile | null) {
    if (meta && meta.version !== pending.remoteVersion) {
      await shardFiles.writePending(shard, { ...pending, remote: await download(meta.id), remoteVersion: meta.version ?? "" });
    }
  }

  private async analysis(shard: string, remote: ShardFile): Promise<MergeAnalysis> {
    const base = await shardFiles.readBase(shard);
    return analyze(
      base ? toRecordSet(base.records) : new Map(),
      toRecordSet(this.port.get(shard)),
      toRecordSet(remote.records),
      DAILY_POLICY,
      { local: this.port.dirty()[shard] ?? 0, remote: remote.writtenAt },
    );
  }

  private async resolveLocked(shard: string, choice: MergeChoice, resolutions: Record<string, Resolution>): Promise<ShardSyncReport> {
    const pending = await shardFiles.readPending(shard);
    if (!pending) return report("ok");
    const folders = await this.folders();
    if (!folders) return report("notConfigured");
    const parentId = shard === CONFIG_SHARD ? folders.syncId : folders.daysId;
    const meta = await drive.findChild(parentId, shardFileName(shard), false);
    if (meta && meta.version !== pending.remoteVersion) {
      await this.refreshPending(shard, pending, meta);
      return { ...report("ok"), conflicts: [shard] };
    }
    const local = this.port.get(shard);
    const remoteSet = toRecordSet(pending.remote.records);
    const merged =
      choice === "KEEP_LOCAL" ? whole(toRecordSet(local), remoteSet, DAILY_POLICY)
        : choice === "KEEP_REMOTE" ? whole(remoteSet, remoteSet, DAILY_POLICY)
          : resolve(await this.analysis(shard, pending.remote), resolutions);
    const next = fromRecordSet(merged);
    if (!this.port.replace(shard, local, next)) return { ...report("failed"), message: "Calendar changed while merging; try again" };
    const dirtyAt = this.port.dirty()[shard];
    await this.write(shard, parentId, next, pending.remote.rev + 1);
    await shardFiles.clearPending(shard);
    if (dirtyAt !== undefined) this.port.clearDirty(shard, dirtyAt);
    return { ...report("ok"), pushed: 1 };
  }

  /** Throws [Retry] when Drive moved since it was read. */
  private async push(shard: string, parentId: string, records: ShardRecords, rev: number, expectedVersion: string | null) {
    const now = await drive.findChild(parentId, shardFileName(shard), false);
    if ((now?.version ?? null) !== expectedVersion) throw new Retry();
    await this.write(shard, parentId, records, rev);
  }

  private async write(shard: string, parentId: string, records: ShardRecords, rev: number) {
    const file: ShardFile = {
      formatVersion: SHARD_FORMAT_VERSION,
      shard,
      rev,
      writtenBy: this.port.device(),
      writtenAt: Date.now(),
      records,
    };
    const written = await drive.writeText(parentId, shardFileName(shard), JSON.stringify(file));
    await shardFiles.writeBase(shard, { ...file, driveVersion: written.version ?? null });
  }
}

function report(kind: ShardSyncReport["kind"]): ShardSyncReport {
  return { kind, pushed: 0, pulled: 0, merged: 0, conflicts: [] };
}

async function download(fileId: string): Promise<ShardFile> {
  const text = await drive.downloadText(fileId);
  let raw: Partial<ShardFile>;
  try {
    raw = JSON.parse(text);
  } catch {
    throw new Error("A Drive calendar shard is unreadable");
  }
  if ((raw.formatVersion ?? 1) > SHARD_FORMAT_VERSION) throw new Error("Drive calendar data needs a newer Vector");
  return { ...emptyShard(raw.shard ?? ""), ...raw, records: raw.records ?? {} };
}
