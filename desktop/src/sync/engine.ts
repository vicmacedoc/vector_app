// Keeps the desktop Brain in step with Data/brain/_sync/state.json. Mirrors mobile BrainStateSync.kt;
// protocol: docs/brain-sync-spec.md.
import { BRAIN_STATE_FORMAT_VERSION, normState, type BrainState, type SyncDevice } from "@/domain/models";
import * as drive from "@/drive/client";
import { accessToken, SignedOutError } from "@/drive/auth";
import { errorMessage, native } from "@/platform/native";
import { local, type Outbox, type PendingMerge } from "@/storage/local";
import { analyze, canonical, hasContent, resolve, whole, type MergeAnalysis, type RecordSet, type Resolution } from "./merge";
import { fromRecords, toRecords } from "./records";

export type SyncOutcome =
  | { kind: "notConfigured" }
  | { kind: "signedOut" }
  | { kind: "upToDate" }
  | { kind: "pushed"; rev: number }
  | { kind: "pulled"; rev: number }
  | { kind: "needsDecision"; conflicts: number }
  | { kind: "failed"; message: string };

export type MergeChoice = "KEEP_LOCAL" | "KEEP_REMOTE" | "SMART";

export interface PendingMergeView {
  analysis: MergeAnalysis;
  remoteDevice: SyncDevice | null;
  remoteWrittenAt: number;
}

/** The working Brain owned by the UI store. */
export interface LocalPort {
  get(): BrainState;
  /** Swaps in [next] only if the working state is still [expected]; false means the user edited meanwhile. */
  replace(expected: BrainState, next: BrainState): boolean;
  update(fn: (s: BrainState) => BrainState): void;
  rootFolderId(): string | null;
  device(): SyncDevice;
}

const STATE_FILE = "state.json";
const MAX_ATTEMPTS = 3;

class Retry extends Error {}

interface Location {
  brainId: string;
  syncId: string;
}

let chain: Promise<unknown> = Promise.resolve();
function locked<T>(fn: () => Promise<T>): Promise<T> {
  const run = chain.then(fn, fn);
  chain = run.catch(() => undefined);
  return run;
}

let outbox: Outbox = { trash: [], uploads: [] };
let outboxLoaded = false;

async function loadOutbox() {
  if (!outboxLoaded) {
    outbox = await local.readOutbox();
    outboxLoaded = true;
  }
}

export async function queueTrash(driveFileId: string) {
  await loadOutbox();
  if (!outbox.trash.includes(driveFileId)) outbox = { ...outbox, trash: [...outbox.trash, driveFileId] };
  await local.writeOutbox(outbox);
}

export async function queueUpload(assetId: string, localPath: string) {
  await loadOutbox();
  outbox = { ...outbox, uploads: [...outbox.uploads.filter((u) => u.assetId !== assetId), { assetId, localPath }] };
  await local.writeOutbox(outbox);
}

export async function pendingUploads(): Promise<number> {
  await loadOutbox();
  return outbox.uploads.length;
}

export class SyncEngine {
  constructor(private port: LocalPort) {}

  sync(): Promise<SyncOutcome> {
    return locked(() => this.guard(() => this.syncLocked()));
  }

  loadPending(): Promise<PendingMergeView | null> {
    return locked(async () => {
      const pending = await local.readPending();
      if (!pending) return null;
      const analysis = analyze(await this.baseRecords(), toRecords(this.port.get()), toRecords(pending.remote));
      return { analysis, remoteDevice: pending.remote.writtenBy, remoteWrittenAt: pending.remote.writtenAt };
    });
  }

  resolvePending(choice: MergeChoice, resolutions: Record<string, Resolution> = {}): Promise<SyncOutcome> {
    return locked(() => this.guard(() => this.resolveLocked(choice, resolutions)));
  }

  private async guard(fn: () => Promise<SyncOutcome>): Promise<SyncOutcome> {
    try {
      return await fn();
    } catch (e) {
      if (e instanceof SignedOutError) return { kind: "signedOut" };
      return { kind: "failed", message: errorMessage(e) };
    }
  }

  private async locate(): Promise<Location | null> {
    const root = this.port.rootFolderId();
    if (!root) return null;
    const brainId = await drive.ensureFolder(root, "brain");
    const syncId = await drive.ensureFolder(brainId, "_sync");
    return { brainId, syncId };
  }

  private async syncLocked(): Promise<SyncOutcome> {
    const location = await this.locate();
    if (!location) return { kind: "notConfigured" };
    await this.flushOutbox(location);

    for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      const remoteMeta = await drive.findChild(location.syncId, STATE_FILE, false);
      const pending = await local.readPending();
      if (pending) return this.refreshPending(pending, remoteMeta);

      const base = await local.readState("base.json");
      const snapshot = this.port.get();
      const remote = remoteMeta ? await this.download(remoteMeta.id) : null;

      const localRecords = toRecords(snapshot);
      const localChanged = base ? canonical(localRecords) !== canonical(toRecords(base)) : hasContent(localRecords);
      const remoteChanged = remote !== null && (base === null || remote.rev !== base.rev);

      try {
        if (!remote || !remoteMeta) {
          return await this.push(location, snapshot, (base?.rev ?? 0) + 1, null);
        }
        if (!localChanged && !remoteChanged) {
          await this.touchLastSync();
          return { kind: "upToDate" };
        }
        if (localChanged && !remoteChanged) {
          return await this.push(location, snapshot, remote.rev + 1, remoteMeta.version ?? null);
        }
        if (!localChanged) return await this.pull(snapshot, remote);
        if (canonical(localRecords) === canonical(toRecords(remote))) {
          await local.writeState("base.json", { ...snapshot, rev: remote.rev, writtenBy: remote.writtenBy, writtenAt: remote.writtenAt });
          await this.touchLastSync();
          return { kind: "upToDate" };
        }
        await local.writePending({ remote, remoteVersion: remoteMeta.version ?? "", detectedAt: Date.now() });
        const conflicts = analyze(await this.baseRecords(), localRecords, toRecords(remote)).conflicts.length;
        return { kind: "needsDecision", conflicts };
      } catch (e) {
        if (e instanceof Retry) continue;
        throw e;
      }
    }
    return { kind: "failed", message: "Drive kept changing during sync; will retry" };
  }

  private async refreshPending(pending: PendingMerge, remoteMeta: drive.DriveFile | null): Promise<SyncOutcome> {
    let current = pending;
    if (remoteMeta && remoteMeta.version !== pending.remoteVersion) {
      current = { ...pending, remote: await this.download(remoteMeta.id), remoteVersion: remoteMeta.version ?? "" };
      await local.writePending(current);
    }
    const conflicts = analyze(await this.baseRecords(), toRecords(this.port.get()), toRecords(current.remote)).conflicts.length;
    return { kind: "needsDecision", conflicts };
  }

  private async resolveLocked(choice: MergeChoice, resolutions: Record<string, Resolution>): Promise<SyncOutcome> {
    const pending = await local.readPending();
    if (!pending) return { kind: "upToDate" };
    const location = await this.locate();
    if (!location) return { kind: "notConfigured" };
    const remoteMeta = await drive.findChild(location.syncId, STATE_FILE, false);
    if (remoteMeta && remoteMeta.version !== pending.remoteVersion) return this.refreshPending(pending, remoteMeta);

    const snapshot = this.port.get();
    const remoteRecords = toRecords(pending.remote);
    let merged: RecordSet;
    if (choice === "KEEP_LOCAL") merged = whole(toRecords(snapshot), remoteRecords);
    else if (choice === "KEEP_REMOTE") merged = whole(remoteRecords, remoteRecords);
    else merged = resolve(analyze(await this.baseRecords(), toRecords(snapshot), remoteRecords), resolutions);

    const next = fromRecords(merged, snapshot);
    if (!this.port.replace(snapshot, next)) return { kind: "failed", message: "Brain changed while merging; try again" };
    await this.dropRemovedAssets(snapshot, next);
    const rev = pending.remote.rev + 1;
    const written = await this.writeRemote(location, next, rev);
    await local.writeState("base.json", written);
    await local.clearPending();
    await this.touchLastSync();
    return { kind: "pushed", rev };
  }

  /** Throws [Retry] when Drive moved since it was read. */
  private async push(location: Location, state: BrainState, rev: number, expectedVersion: string | null): Promise<SyncOutcome> {
    const now = await drive.findChild(location.syncId, STATE_FILE, false);
    if ((now?.version ?? null) !== expectedVersion) throw new Retry();
    const written = await this.writeRemote(location, state, rev);
    await local.writeState("base.json", written);
    await this.touchLastSync();
    return { kind: "pushed", rev };
  }

  private async pull(snapshot: BrainState, remote: BrainState): Promise<SyncOutcome> {
    const next: BrainState = { ...remote };
    if (!this.port.replace(snapshot, next)) throw new Retry();
    await this.dropRemovedAssets(snapshot, next);
    await local.writeState("base.json", next);
    await this.touchLastSync();
    return { kind: "pulled", rev: remote.rev };
  }

  private async writeRemote(location: Location, state: BrainState, rev: number): Promise<BrainState> {
    const stamped: BrainState = {
      ...state,
      formatVersion: BRAIN_STATE_FORMAT_VERSION,
      rev,
      writtenBy: this.port.device(),
      writtenAt: Date.now(),
    };
    await drive.writeText(location.syncId, STATE_FILE, JSON.stringify(stamped));
    return stamped;
  }

  private async download(fileId: string): Promise<BrainState> {
    const text = await drive.downloadText(fileId);
    let raw: unknown;
    try {
      raw = JSON.parse(text);
    } catch {
      throw new Error("Drive Brain state is unreadable");
    }
    const state = normState(raw);
    if (state.formatVersion > BRAIN_STATE_FORMAT_VERSION) throw new Error("Drive Brain state needs a newer Vector Brain");
    return state;
  }

  private async baseRecords(): Promise<RecordSet> {
    const base = await local.readState("base.json");
    return base ? toRecords(base) : new Map();
  }

  private async touchLastSync() {
    const settings = await local.readSettings();
    await local.writeSettings({ ...settings, lastSyncAt: Date.now() });
  }

  private async dropRemovedAssets(before: BrainState, after: BrainState) {
    const kept = new Set(after.assets.map((a) => a.id));
    for (const a of before.assets) if (!kept.has(a.id)) await native.cacheRemove(a.id).catch(() => undefined);
  }

  /** Trash removed files first so the phone's folder scan can't re-import them, then upload new ones. */
  private async flushOutbox(location: Location) {
    await loadOutbox();
    for (const fileId of [...outbox.trash]) {
      await drive.trash(fileId);
      outbox = { ...outbox, trash: outbox.trash.filter((t) => t !== fileId) };
      await local.writeOutbox(outbox);
    }
    for (const upload of [...outbox.uploads]) {
      await this.upload(location, upload.assetId, upload.localPath);
      outbox = { ...outbox, uploads: outbox.uploads.filter((u) => u.assetId !== upload.assetId) };
      await local.writeOutbox(outbox);
    }
  }

  private async upload(location: Location, assetId: string, localPath: string) {
    const state = this.port.get();
    const asset = state.assets.find((a) => a.id === assetId);
    if (!asset || asset.driveFileId) return;
    const node = state.nodes.find((n) => state.notes.some((note) => note.assetId === assetId && note.nodeId === n.id));
    const folderParts = asset.path.split("/").slice(0, -1);
    const parentId = node?.driveFolderId ?? (await drive.ensurePath(location.brainId, folderParts));
    const token = await accessToken();
    const uploaded = await native.driveUpload(token, parentId, asset.fileName, asset.mimeType, localPath);
    const now = Date.now();
    this.port.update((s) => ({
      ...s,
      assets: s.assets.map((a) => (a.id === assetId ? { ...a, driveFileId: uploaded.id, sizeBytes: uploaded.size, updatedAt: now } : a)),
      nodes:
        node && !node.driveFolderId && folderParts.length === 2
          ? s.nodes.map((n) => (n.id === node.id && !n.driveFolderId ? { ...n, driveFolderId: parentId } : n))
          : s.nodes,
    }));
  }
}

/** Downloads an asset into the local cache (or returns the cached copy). */
export async function ensureAssetFile(asset: { id: string; fileName: string; driveFileId: string | null }): Promise<string | null> {
  const cached = await native.cacheLookup(asset.id, asset.fileName);
  if (cached) return cached;
  if (!asset.driveFileId) return null;
  return native.driveDownload(await accessToken(), asset.driveFileId, asset.id, asset.fileName);
}
