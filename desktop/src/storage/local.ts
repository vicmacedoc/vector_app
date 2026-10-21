// Device-local files in the app data folder (written atomically by Rust).
import { normState, type BrainState } from "@/domain/models";
import { native } from "@/platform/native";

export const WORKING = "working.json";
export const BASE = "base.json";
export const PENDING = "pending_remote.json";
export const SETTINGS = "settings.json";
export const OUTBOX = "outbox.json";

export interface DesktopSettings {
  rootFolderId: string | null;
  rootFolderName: string | null;
  deviceId: string;
  deviceName: string;
  lastSyncAt: number | null;
}

/** Drive work waiting for the next sync: files to trash, local files to upload. */
export interface Outbox {
  trash: string[];
  uploads: { assetId: string; localPath: string }[];
}

export interface PendingMerge {
  remote: BrainState;
  remoteVersion: string;
  detectedAt: number;
}

async function readJson<T>(name: string): Promise<T | null> {
  const text = await native.storeRead(name);
  if (!text) return null;
  try {
    return JSON.parse(text) as T;
  } catch {
    return null;
  }
}

const writeJson = (name: string, value: unknown) => native.storeWrite(name, JSON.stringify(value));

export const local = {
  async readState(name: typeof WORKING | typeof BASE): Promise<BrainState | null> {
    const raw = await readJson<unknown>(name);
    return raw ? normState(raw) : null;
  },
  writeState: (name: typeof WORKING | typeof BASE, state: BrainState) => writeJson(name, state),
  clearBase: async () => {
    await native.storeDelete(BASE);
    await native.storeDelete(PENDING);
  },

  async readPending(): Promise<PendingMerge | null> {
    const raw = await readJson<PendingMerge>(PENDING);
    return raw ? { ...raw, remote: normState(raw.remote) } : null;
  },
  writePending: (p: PendingMerge) => writeJson(PENDING, p),
  clearPending: () => native.storeDelete(PENDING),

  async readSettings(): Promise<DesktopSettings> {
    const raw = await readJson<Partial<DesktopSettings>>(SETTINGS);
    const settings: DesktopSettings = {
      rootFolderId: raw?.rootFolderId ?? null,
      rootFolderName: raw?.rootFolderName ?? null,
      deviceId: raw?.deviceId ?? crypto.randomUUID(),
      deviceName: raw?.deviceName ?? "Desktop",
      lastSyncAt: raw?.lastSyncAt ?? null,
    };
    if (!raw?.deviceId) await writeJson(SETTINGS, settings);
    return settings;
  },
  writeSettings: (s: DesktopSettings) => writeJson(SETTINGS, s),

  async readOutbox(): Promise<Outbox> {
    const raw = await readJson<Partial<Outbox>>(OUTBOX);
    return { trash: raw?.trash ?? [], uploads: raw?.uploads ?? [] };
  },
  writeOutbox: (o: Outbox) => writeJson(OUTBOX, o),
};
