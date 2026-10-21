import { create } from "zustand";
import { native } from "@/platform/native";
import type { Resolution } from "@/sync/merge";
import type { MergeChoice } from "@/sync/engine";
import {
  ShardEngine, shardFiles, shardStoreName,
  type ShardMergeView, type ShardRecords, type ShardSyncReport,
} from "@/sync/shardEngine";
import { CONFIG_SHARD } from "@/sync/shards";
import { useBrain } from "./brain";

export type DailyPhase = "idle" | "syncing" | "ok" | "error" | "conflict";

export interface CalendarDraft {
  payloadJson: string;
  updatedAt: number;
}

interface DailyStore {
  ready: boolean;
  shards: Record<string, ShardRecords>;
  dirty: Record<string, number>;
  drafts: Record<string, CalendarDraft>;
  phase: DailyPhase;
  message: string | null;
  lastSyncAt: number | null;
  conflicts: string[];
  merge: ShardMergeView | null;
  mergeOpen: boolean;

  init(): Promise<void>;
  /** Applies [fn] to one shard, persists it, marks it dirty and schedules a push. */
  mutate(shard: string, fn: (r: ShardRecords) => ShardRecords): void;
  setDraft(key: string, payloadJson: string | null): void;
  syncNow(fullScan?: boolean): Promise<void>;
  openMerge(shard?: string): Promise<void>;
  closeMerge(): void;
  resolveMerge(choice: MergeChoice, resolutions?: Record<string, Resolution>): Promise<void>;
  resetSyncBase(): Promise<void>;
}

const INDEX = "daily_index.json";
const DIRTY = "shard_dirty.json";
const DRAFTS = "calendar_drafts.json";
const PUSH_DELAY_MS = 3_000;
const POLL_MS = 60_000;
const FOCUS_MIN_GAP_MS = 15_000;

let pushTimer: ReturnType<typeof setTimeout> | undefined;
const persistTimers = new Map<string, ReturnType<typeof setTimeout>>();
let draftTimer: ReturnType<typeof setTimeout> | undefined;
let syncing: Promise<void> | null = null;
let again = false;
let lastStarted = 0;
let initStarted = false;

async function readJson<T>(name: string, fallback: T): Promise<T> {
  const text = await native.storeRead(name);
  if (!text) return fallback;
  try {
    return JSON.parse(text) as T;
  } catch {
    return fallback;
  }
}

export const useDaily = create<DailyStore>((set, get) => {
  const persistShard = (shard: string) => {
    clearTimeout(persistTimers.get(shard));
    persistTimers.set(
      shard,
      setTimeout(() => {
        void native.storeWrite(shardStoreName(shard), JSON.stringify(get().shards[shard] ?? {}));
        void native.storeWrite(INDEX, JSON.stringify(Object.keys(get().shards)));
      }, 200),
    );
  };
  const persistDirty = () => void native.storeWrite(DIRTY, JSON.stringify(get().dirty));

  const engine = new ShardEngine({
    get: (shard) => get().shards[shard] ?? {},
    replace(shard, expected, next) {
      if ((get().shards[shard] ?? {}) !== expected && get().shards[shard] !== undefined) return false;
      set({ shards: { ...get().shards, [shard]: next } });
      persistShard(shard);
      return true;
    },
    knownShards: () => Object.keys(get().shards),
    dirty: () => get().dirty,
    clearDirty(shard, ifAt) {
      if (get().dirty[shard] !== ifAt) return;
      const { [shard]: _, ...rest } = get().dirty;
      set({ dirty: rest });
      persistDirty();
    },
    rootFolderId: () => useBrain.getState().settings.rootFolderId,
    device: () => {
      const s = useBrain.getState().settings;
      return { deviceId: s.deviceId, name: s.deviceName, platform: "desktop" };
    },
  });

  const apply = async (r: ShardSyncReport) => {
    const brain = useBrain.getState();
    if (r.kind === "failed") {
      set({ phase: "error", message: r.message ?? "Sync failed" });
      return;
    }
    if (r.kind !== "ok") {
      set({ phase: "idle", message: null });
      return;
    }
    set({ lastSyncAt: Date.now(), conflicts: r.conflicts, message: null });
    if (r.merged > 0) brain.toast(`Merged ${r.merged} change(s) from your other device`);
    else if (r.pulled > 0) brain.toast(`Calendar updated from your other device`);
    if (r.conflicts.length > 0) {
      const first = get().phase !== "conflict";
      set({ phase: "conflict" });
      if (first) await get().openMerge(r.conflicts[0]);
    } else {
      set({ phase: "ok", merge: null, mergeOpen: false });
    }
  };

  return {
    ready: false,
    shards: {},
    dirty: {},
    drafts: {},
    phase: "idle",
    message: null,
    lastSyncAt: null,
    conflicts: [],
    merge: null,
    mergeOpen: false,

    async init() {
      if (initStarted) return;
      initStarted = true;
      const index = await readJson<string[]>(INDEX, []);
      const shards: Record<string, ShardRecords> = {};
      for (const shard of new Set([CONFIG_SHARD, ...index])) {
        const records = await readJson<ShardRecords | null>(shardStoreName(shard), null);
        if (records) shards[shard] = records;
      }
      const dirty = await readJson<Record<string, number>>(DIRTY, {});
      const drafts = await readJson<Record<string, CalendarDraft>>(DRAFTS, {});
      set({ ready: true, shards, dirty, drafts });
      void get().syncNow(true);
      window.addEventListener("focus", () => {
        if (Date.now() - lastStarted > FOCUS_MIN_GAP_MS) void get().syncNow();
      });
      setInterval(() => void get().syncNow(), POLL_MS);
    },

    mutate(shard, fn) {
      const before = get().shards[shard] ?? {};
      const after = fn(before);
      if (after === before) return;
      set({ shards: { ...get().shards, [shard]: after }, dirty: { ...get().dirty, [shard]: Date.now() } });
      persistShard(shard);
      persistDirty();
      clearTimeout(pushTimer);
      pushTimer = setTimeout(() => void get().syncNow(), PUSH_DELAY_MS);
    },

    setDraft(key, payloadJson) {
      const drafts = { ...get().drafts };
      if (payloadJson === null) delete drafts[key];
      else drafts[key] = { payloadJson, updatedAt: Date.now() };
      set({ drafts });
      clearTimeout(draftTimer);
      draftTimer = setTimeout(() => void native.storeWrite(DRAFTS, JSON.stringify(get().drafts)), 300);
    },

    async syncNow(fullScan = false) {
      clearTimeout(pushTimer);
      const brain = useBrain.getState();
      if (!get().ready || !brain.signedIn || !brain.settings.rootFolderId) return;
      if (syncing) {
        again = true;
        return syncing;
      }
      lastStarted = Date.now();
      if (get().phase !== "conflict") set({ phase: "syncing" });
      syncing = (async () => {
        try {
          await apply(await engine.sync(fullScan));
        } finally {
          syncing = null;
          if (again) {
            again = false;
            pushTimer = setTimeout(() => void get().syncNow(), 1000);
          }
        }
      })();
      return syncing;
    },

    async openMerge(shard) {
      const target = shard ?? get().conflicts[0] ?? (await engine.pendingShards())[0];
      if (!target) return;
      const merge = await engine.loadPending(target);
      set({ merge, mergeOpen: merge !== null });
    },
    closeMerge: () => set({ mergeOpen: false }),

    async resolveMerge(choice, resolutions = {}) {
      const shard = get().merge?.shard;
      if (!shard) return;
      const r = await engine.resolvePending(shard, choice, resolutions);
      if (r.kind === "failed") {
        useBrain.getState().toast(r.message ?? "Merge failed", "error");
        return;
      }
      if (r.conflicts.length > 0) {
        useBrain.getState().toast("The other device synced again; review the new changes");
        await get().openMerge(shard);
        return;
      }
      useBrain.getState().toast("Merged and synced");
      const rest = get().conflicts.filter((c) => c !== shard);
      set({ conflicts: rest, merge: null, mergeOpen: false, phase: rest.length ? "conflict" : "ok" });
      if (rest.length) await get().openMerge(rest[0]);
    },

    async resetSyncBase() {
      await shardFiles.clearAll(Object.keys(get().shards));
      set({ merge: null, mergeOpen: false, conflicts: [] });
      void get().syncNow(true);
    },
  };
});
