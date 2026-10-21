// Drive v3 REST for metadata and the JSON state file. Binary transfers go through Rust (platform/native).
import { fetch } from "@tauri-apps/plugin-http";
import { accessToken } from "./auth";

const API = "https://www.googleapis.com/drive/v3/files";
const UPLOAD = "https://www.googleapis.com/upload/drive/v3/files";
export const FOLDER_MIME = "application/vnd.google-apps.folder";

export interface DriveFile {
  id: string;
  name: string;
  mimeType: string;
  parents?: string[];
  version?: string;
  size?: string;
  modifiedTime?: string;
  trashed?: boolean;
}

const FIELDS = "id,name,mimeType,parents,version,size,modifiedTime,trashed";

export class DriveError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function request(url: string, init: RequestInit = {}): Promise<Response> {
  for (let attempt = 0; attempt < 2; attempt++) {
    const token = await accessToken(attempt > 0);
    const headers = new Headers(init.headers);
    headers.set("Authorization", `Bearer ${token}`);
    const res = await fetch(url, { ...init, headers });
    if (res.status === 401 && attempt === 0) continue;
    if (!res.ok) {
      const text = await res.text().catch(() => "");
      let message = `Drive error ${res.status}`;
      try {
        message = JSON.parse(text)?.error?.message ?? message;
      } catch {
        /* not JSON */
      }
      throw new DriveError(res.status, message);
    }
    return res;
  }
  throw new DriveError(401, "Drive rejected the sign-in");
}

const escape = (s: string) => s.replace(/\\/g, "\\\\").replace(/'/g, "\\'");

export async function list(q: string): Promise<DriveFile[]> {
  const out: DriveFile[] = [];
  let pageToken: string | undefined;
  do {
    const params = new URLSearchParams({
      q,
      fields: `nextPageToken,files(${FIELDS})`,
      pageSize: "1000",
      spaces: "drive",
    });
    if (pageToken) params.set("pageToken", pageToken);
    const body = await (await request(`${API}?${params}`)).json();
    out.push(...(body.files ?? []));
    pageToken = body.nextPageToken;
  } while (pageToken);
  return out;
}

export const listChildren = (folderId: string) => list(`'${escape(folderId)}' in parents and trashed=false`);

export async function findChild(parentId: string, name: string, folder?: boolean): Promise<DriveFile | null> {
  const kind = folder === undefined ? "" : folder ? ` and mimeType='${FOLDER_MIME}'` : ` and mimeType!='${FOLDER_MIME}'`;
  const files = await list(`'${escape(parentId)}' in parents and name='${escape(name)}' and trashed=false${kind}`);
  return files[0] ?? null;
}

const folderCache = new Map<string, string>();

export async function ensureFolder(parentId: string, name: string): Promise<string> {
  const cacheKey = `${parentId}/${name}`;
  const hit = folderCache.get(cacheKey);
  if (hit) return hit;
  const existing = await findChild(parentId, name, true);
  const id =
    existing?.id ??
    (
      await (
        await request(`${API}?fields=id`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ name, mimeType: FOLDER_MIME, parents: [parentId] }),
        })
      ).json()
    ).id;
  folderCache.set(cacheKey, id);
  return id;
}

export async function ensurePath(rootId: string, parts: string[]): Promise<string> {
  let current = rootId;
  for (const part of parts.filter(Boolean)) current = await ensureFolder(current, part);
  return current;
}

export function forgetFolders() {
  folderCache.clear();
}

export async function meta(fileId: string): Promise<DriveFile> {
  return (await request(`${API}/${encodeURIComponent(fileId)}?fields=${FIELDS}`)).json();
}

export async function downloadText(fileId: string): Promise<string> {
  return (await request(`${API}/${encodeURIComponent(fileId)}?alt=media`)).text();
}

/** Overwrites [name] in [parentId] (creating it when missing); Drive keeps the previous revision. */
export async function writeText(parentId: string, name: string, content: string, mimeType = "application/json"): Promise<DriveFile> {
  const existing = await findChild(parentId, name, false);
  if (existing) {
    const res = await request(`${UPLOAD}/${encodeURIComponent(existing.id)}?uploadType=media&fields=${FIELDS}`, {
      method: "PATCH",
      headers: { "Content-Type": `${mimeType}; charset=UTF-8` },
      body: content,
    });
    return res.json();
  }
  const boundary = `vector_${Math.random().toString(36).slice(2)}`;
  const body =
    `--${boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n` +
    JSON.stringify({ name, parents: [parentId], mimeType }) +
    `\r\n--${boundary}\r\nContent-Type: ${mimeType}; charset=UTF-8\r\n\r\n${content}\r\n--${boundary}--`;
  const res = await request(`${UPLOAD}?uploadType=multipart&fields=${FIELDS}`, {
    method: "POST",
    headers: { "Content-Type": `multipart/related; boundary=${boundary}` },
    body,
  });
  return res.json();
}

export async function trash(fileId: string): Promise<void> {
  try {
    await request(`${API}/${encodeURIComponent(fileId)}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ trashed: true }),
    });
  } catch (e) {
    if (e instanceof DriveError && e.status === 404) return;
    throw e;
  }
}

/** Folder id from a pasted Drive link (`.../folders/<id>`, `?id=<id>`) or a bare id. */
export function folderIdFromInput(input: string): string | null {
  const s = input.trim();
  const m = s.match(/folders\/([A-Za-z0-9_-]{10,})/) ?? s.match(/[?&]id=([A-Za-z0-9_-]{10,})/);
  if (m) return m[1];
  return /^[A-Za-z0-9_-]{10,}$/.test(s) ? s : null;
}

/** `Data` folders that hold `brain/_sync/state.json`, i.e. where the phone already syncs Brain. */
export async function detectDataFolders(): Promise<{ id: string; name: string }[]> {
  const states = await list("name='state.json' and trashed=false");
  const found = new Map<string, string>();
  for (const state of states) {
    const syncId = state.parents?.[0];
    if (!syncId) continue;
    const sync = await meta(syncId);
    if (sync.name !== "_sync" || !sync.parents?.[0]) continue;
    const brain = await meta(sync.parents[0]);
    if (brain.name !== "brain" || !brain.parents?.[0]) continue;
    const data = await meta(brain.parents[0]);
    found.set(data.id, data.name);
  }
  return [...found].map(([id, name]) => ({ id, name }));
}
