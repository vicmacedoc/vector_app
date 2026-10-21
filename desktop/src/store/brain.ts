import { create } from "zustand";
import { emptyState, newId, type BrainState, type SyncDevice } from "@/domain/models";
import * as ops from "@/domain/ops";
import { supermarketKey } from "@/sync/records";
import * as auth from "@/drive/auth";
import { detectDataFolders, forgetFolders } from "@/drive/client";
import { errorMessage, native } from "@/platform/native";
import { local, WORKING, type DesktopSettings } from "@/storage/local";
import {
  ensureAssetFile, queueTrash, queueUpload, SyncEngine,
  type MergeChoice, type PendingMergeView, type SyncOutcome,
} from "@/sync/engine";
import type { Resolution } from "@/sync/merge";

export type SyncPhase = "idle" | "syncing" | "ok" | "error" | "conflict" | "signedOut" | "notConfigured";

export interface Toast {
  id: number;
  message: string;
  tone: "info" | "error";
}

interface Store {
  ready: boolean;
  brain: BrainState;
  settings: DesktopSettings;
  signedIn: boolean;
  phase: SyncPhase;
  syncMessage: string | null;
  merge: PendingMergeView | null;
  mergeOpen: boolean;
  toasts: Toast[];

  init(): Promise<void>;
  commit(fn: (s: BrainState) => BrainState): void;
  toast(message: string, tone?: Toast["tone"]): void;
  dismissToast(id: number): void;

  signIn(): Promise<void>;
  signOut(): Promise<void>;
  detectFolders(): Promise<{ id: string; name: string }[]>;
  setRootFolder(id: string, name: string | null): Promise<void>;
  setDeviceName(name: string): Promise<void>;
  resetSyncBase(): Promise<void>;

  syncNow(): Promise<void>;
  openMerge(): Promise<void>;
  closeMerge(): void;
  resolveMerge(choice: MergeChoice, resolutions?: Record<string, Resolution>): Promise<void>;

  addFiles(paths: string[], nodeId: string | null): Promise<number>;
  /** Saves bytes made in the app (voice notes, webcam shots) as an asset note; returns the note id. */
  addBlob(blob: Blob, fileName: string, nodeId: string | null): Promise<string | null>;
  openAsset(assetId: string): Promise<void>;
  assetPreview(assetId: string): Promise<string | null>;

  saveSupermarket(): Promise<number | null>;
  confirmTrip(went: boolean): void;
}

const SUPERMARKET_BASELINE = "supermarket_baseline.json";
const EDIT_SYNC_DELAY_MS = 8_000;
const PERIODIC_SYNC_MS = 5 * 60_000;
const FOCUS_SYNC_MIN_GAP_MS = 30_000;

let persistTimer: ReturnType<typeof setTimeout> | undefined;
let syncTimer: ReturnType<typeof setTimeout> | undefined;
let toastSeq = 0;
let lastSyncStarted = 0;
let syncing: Promise<void> | null = null;
let syncAgain = false;
let initStarted = false;

export async function blobToBase64(blob: Blob): Promise<string> {
  const bytes = new Uint8Array(await blob.arrayBuffer());
  let binary = "";
  for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(binary);
}

export const useBrain = create<Store>((set, get) => {
  const persist = () => {
    clearTimeout(persistTimer);
    persistTimer = setTimeout(() => void local.writeState(WORKING, get().brain), 250);
  };

  const scheduleSync = (delay = EDIT_SYNC_DELAY_MS) => {
    clearTimeout(syncTimer);
    syncTimer = setTimeout(() => void get().syncNow(), delay);
  };

  const engine = new SyncEngine({
    get: () => get().brain,
    replace(expected, next) {
      if (get().brain !== expected) return false;
      set({ brain: next });
      persist();
      return true;
    },
    update(fn) {
      set({ brain: fn(get().brain) });
      persist();
    },
    rootFolderId: () => get().settings.rootFolderId,
    device: (): SyncDevice => ({ deviceId: get().settings.deviceId, name: get().settings.deviceName, platform: "desktop" }),
  });

  const applyOutcome = async (outcome: SyncOutcome) => {
    const settings = await local.readSettings();
    set({ settings: { ...get().settings, lastSyncAt: settings.lastSyncAt } });
    switch (outcome.kind) {
      case "upToDate":
      case "pushed":
      case "pulled":
        set({ phase: "ok", syncMessage: null, merge: null, mergeOpen: false });
        break;
      case "needsDecision": {
        const first = get().phase !== "conflict";
        const merge = await engine.loadPending();
        set({ phase: "conflict", syncMessage: null, merge, mergeOpen: get().mergeOpen || first });
        break;
      }
      case "signedOut":
        set({ phase: "signedOut", signedIn: false, syncMessage: "Sign in to Google Drive" });
        break;
      case "notConfigured":
        set({ phase: "notConfigured", syncMessage: "Choose your Drive Data folder" });
        break;
      case "failed":
        set({ phase: "error", syncMessage: outcome.message });
        break;
    }
  };

  const tripState = { pendingPoints: null as number | null };

  return {
    ready: false,
    brain: emptyState(),
    settings: { rootFolderId: null, rootFolderName: null, deviceId: "", deviceName: "Desktop", lastSyncAt: null },
    signedIn: false,
    phase: "idle",
    syncMessage: null,
    merge: null,
    mergeOpen: false,
    toasts: [],

    async init() {
      if (initStarted) return;
      initStarted = true;
      const settings = await local.readSettings();
      const brain = (await local.readState(WORKING)) ?? (await local.readState("base.json")) ?? emptyState();
      const signedIn = await auth.isSignedIn().catch(() => false);
      set({ ready: true, brain, settings, signedIn });
      if (!signedIn) set({ phase: "signedOut" });
      else if (!settings.rootFolderId) set({ phase: "notConfigured" });
      void get().syncNow();
      window.addEventListener("focus", () => {
        if (Date.now() - lastSyncStarted > FOCUS_SYNC_MIN_GAP_MS) void get().syncNow();
      });
      setInterval(() => void get().syncNow(), PERIODIC_SYNC_MS);
    },

    commit(fn) {
      const before = get().brain;
      const after = fn(before);
      if (after === before) return;
      set({ brain: after });
      persist();
      scheduleSync();
      const kept = new Set(after.assets.map((a) => a.id));
      for (const asset of before.assets) {
        if (kept.has(asset.id)) continue;
        if (asset.driveFileId) void queueTrash(asset.driveFileId);
        void native.cacheRemove(asset.id).catch(() => undefined);
      }
    },

    toast(message, tone = "info") {
      const id = ++toastSeq;
      set({ toasts: [...get().toasts, { id, message, tone }] });
      setTimeout(() => get().dismissToast(id), 4000);
    },
    dismissToast: (id) => set({ toasts: get().toasts.filter((t) => t.id !== id) }),

    async signIn() {
      try {
        await auth.signIn();
        set({ signedIn: true, phase: get().settings.rootFolderId ? "idle" : "notConfigured" });
        void get().syncNow();
      } catch (e) {
        get().toast(`Sign-in failed: ${errorMessage(e)}`, "error");
      }
    },

    async signOut() {
      await auth.signOut();
      forgetFolders();
      set({ signedIn: false, phase: "signedOut" });
    },

    detectFolders: () => detectDataFolders(),

    async setRootFolder(id, name) {
      const settings = { ...get().settings, rootFolderId: id, rootFolderName: name };
      if (id !== get().settings.rootFolderId) {
        await local.clearBase();
        forgetFolders();
      }
      await local.writeSettings(settings);
      set({ settings });
      void get().syncNow();
    },

    async setDeviceName(name) {
      const settings = { ...get().settings, deviceName: name.trim() || "Desktop" };
      await local.writeSettings(settings);
      set({ settings });
    },

    /** Forgets what this computer last agreed with Drive; the next sync compares both sides from scratch. */
    async resetSyncBase() {
      await local.clearBase();
      set({ merge: null, mergeOpen: false });
      void get().syncNow();
    },

    async syncNow() {
      clearTimeout(syncTimer);
      if (!get().ready || !get().signedIn || !get().settings.rootFolderId) return;
      if (syncing) {
        syncAgain = true;
        return syncing;
      }
      lastSyncStarted = Date.now();
      set({ phase: get().phase === "conflict" ? "conflict" : "syncing" });
      syncing = (async () => {
        try {
          await applyOutcome(await engine.sync());
        } finally {
          syncing = null;
          if (syncAgain) {
            syncAgain = false;
            scheduleSync(1000);
          }
        }
      })();
      return syncing;
    },

    async openMerge() {
      const merge = await engine.loadPending();
      set({ merge, mergeOpen: merge !== null });
    },
    closeMerge: () => set({ mergeOpen: false }),

    async resolveMerge(choice, resolutions = {}) {
      const outcome = await engine.resolvePending(choice, resolutions);
      await applyOutcome(outcome);
      if (outcome.kind === "pushed") get().toast("Brain merged and synced");
      else if (outcome.kind === "failed") get().toast(outcome.message, "error");
      else if (outcome.kind === "needsDecision") get().toast("The phone synced again; review the new changes");
    },

    async addFiles(paths, nodeId) {
      let added = 0;
      for (const path of paths) {
        try {
          const info = await native.fileInfo(path);
          const assetId = newId("asset");
          const cached = await native.cacheFromLocal(path, assetId, info.name);
          const body = info.mimeType.startsWith("text/") ? (await native.readTextFile(path, 1_000_000)) ?? "" : "";
          get().commit((s) => ops.createAssetNote(s, { assetId, fileName: info.name, mimeType: info.mimeType, sizeBytes: info.size, body }, nodeId)[0]);
          await queueUpload(assetId, cached);
          added++;
        } catch (e) {
          get().toast(`Could not add ${path.split(/[\\/]/).pop()}: ${errorMessage(e)}`, "error");
        }
      }
      if (added) scheduleSync(1500);
      return added;
    },

    async addBlob(blob, fileName, nodeId) {
      try {
        const assetId = newId("asset");
        const cached = await native.cacheWriteBase64(assetId, fileName, await blobToBase64(blob));
        let noteId: string | null = null;
        get().commit((s) => {
          const [next, note] = ops.createAssetNote(
            s, { assetId, fileName, mimeType: blob.type.split(";")[0] || "application/octet-stream", sizeBytes: blob.size, body: "" }, nodeId,
          );
          noteId = note;
          return next;
        });
        await queueUpload(assetId, cached);
        scheduleSync(1500);
        return noteId;
      } catch (e) {
        get().toast(`Could not save ${fileName}: ${errorMessage(e)}`, "error");
        return null;
      }
    },

    async openAsset(assetId) {
      const asset = get().brain.assets.find((a) => a.id === assetId);
      if (!asset) return;
      try {
        const path = await ensureAssetFile(asset);
        if (!path) {
          get().toast("This file hasn't reached Drive yet", "error");
          return;
        }
        await native.openPath(path);
      } catch (e) {
        get().toast(`Could not open file: ${errorMessage(e)}`, "error");
      }
    },

    async assetPreview(assetId) {
      const asset = get().brain.assets.find((a) => a.id === assetId);
      if (!asset) return null;
      try {
        const path = await ensureAssetFile(asset);
        return path ? native.fileSrc(path) : null;
      } catch {
        return null;
      }
    },

    /**
     * Confirms the list and moves the restock baseline. A restock (some stock went up since the last save
     * and nothing is at zero) during an open cycle returns the trip points so the UI can ask "Did you go?".
     */
    async saveSupermarket() {
      const list = get().brain.supermarket;
      const raw = await native.storeRead(SUPERMARKET_BASELINE);
      const baseline: Record<string, number> = raw ? JSON.parse(raw) : {};
      let points: number | null = null;
      if (list.items.length > 0 && !list.items.some((i) => i.remaining === 0)) {
        const restocked = list.items.some((i) => {
          const before = baseline[supermarketKey(i.title)];
          return before !== undefined && (i.remaining ?? i.quantity ?? 1) > before;
        });
        const cycle = ops.currentSupermarketCycle(get().brain);
        if (restocked && cycle.open) points = ops.recordSupermarketTrip(get().brain)?.[1] ?? null;
      }
      const next: Record<string, number> = {};
      for (const i of list.items) next[supermarketKey(i.title)] = i.remaining ?? i.quantity ?? 1;
      await native.storeWrite(SUPERMARKET_BASELINE, JSON.stringify(next));
      tripState.pendingPoints = points;
      if (points === null) get().toast("Saved · syncing");
      return points;
    },

    confirmTrip(went) {
      tripState.pendingPoints = null;
      if (!went) {
        get().toast("Saved · syncing");
        return;
      }
      const result = ops.recordSupermarketTrip(get().brain);
      if (!result) return;
      get().commit(() => result[0]);
      get().toast(`Trip logged · +${result[1]} pts`);
    },
  };
});
