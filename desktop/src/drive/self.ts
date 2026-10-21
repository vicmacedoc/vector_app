// Self media on Drive, same layout as the phone: Data/diary_audio/{date}.* and Data/collections/{album}/.
import { blobToBase64 } from "@/store/brain";
import { useBrain } from "@/store/brain";
import { native } from "@/platform/native";
import { accessToken } from "./auth";
import { ensureFolder, FOLDER_MIME, listChildren, trash, type DriveFile } from "./client";

const BODY_SCAN_ANGLES = ["front", "left", "back", "right"] as const;
export type BodyScanAngle = (typeof BODY_SCAN_ANGLES)[number];
export { BODY_SCAN_ANGLES };

function root(): string {
  const id = useBrain.getState().settings.rootFolderId;
  if (!id) throw new Error("Choose your Drive Data folder in Settings");
  return id;
}

async function uploadBlob(folderId: string, name: string, blob: Blob): Promise<string> {
  const tmpId = `upload_${crypto.randomUUID()}`;
  const path = await native.cacheWriteBase64(tmpId, name, await blobToBase64(blob));
  try {
    const token = await accessToken();
    const res = await native.driveUpload(token, folderId, name, blob.type.split(";")[0] || "application/octet-stream", path);
    return res.id;
  } finally {
    void native.cacheRemove(tmpId);
  }
}

const audioExt = (type: string) => (type.includes("ogg") ? "ogg" : type.includes("mp4") ? "m4a" : type.includes("3gp") ? "3gp" : "webm");

/** Uploads a journal recording and returns its Drive file id. */
export async function uploadDiaryAudio(date: string, blob: Blob): Promise<string> {
  const folder = await ensureFolder(root(), "diary_audio");
  return uploadBlob(folder, `${date}.${audioExt(blob.type)}`, blob);
}

/** Local path of a Drive file (downloaded once into the cache). */
export async function cachedDriveFile(fileId: string, fileName: string): Promise<string> {
  const hit = await native.cacheLookup(`drive_${fileId}`, fileName);
  if (hit) return hit;
  return native.driveDownload(await accessToken(), fileId, `drive_${fileId}`, fileName);
}

export async function driveFileSrc(fileId: string, fileName: string): Promise<string> {
  return native.fileSrc(await cachedDriveFile(fileId, fileName));
}

export interface Album {
  id: string;
  name: string;
  isBodyScan: boolean;
  images: DriveFile[];
}

const isImage = (f: DriveFile) => f.mimeType.startsWith("image/");

export async function listAlbums(): Promise<Album[]> {
  const collections = await ensureFolder(root(), "collections");
  const folders = (await listChildren(collections)).filter((f) => f.mimeType === FOLDER_MIME);
  const albums = await Promise.all(
    folders.map(async (f) => ({
      id: f.id,
      name: f.name.trim() || "Album",
      isBodyScan: (BODY_SCAN_ANGLES as readonly string[]).includes(f.name.trim().toLowerCase()),
      images: (await listChildren(f.id)).filter(isImage).sort((a, b) => a.name.localeCompare(b.name)),
    })),
  );
  return albums.sort((a, b) => Number(b.isBodyScan) - Number(a.isBodyScan) || a.name.localeCompare(b.name));
}

export async function createAlbum(name: string): Promise<string> {
  const clean = name.trim().replace(/[\\/:*?"<>|]/g, "_");
  if (!clean) throw new Error("Album name is required");
  return ensureFolder(await ensureFolder(root(), "collections"), clean);
}

const pad = (n: number) => String(n).padStart(2, "0");

/** Images use datetime-only names like the phone (`yyyy-MM-ddTHH-mm-ss-SSS.jpg`). */
export function imageFileName(at = new Date(), ext = "jpg") {
  return `${at.getFullYear()}-${pad(at.getMonth() + 1)}-${pad(at.getDate())}T${pad(at.getHours())}-${pad(at.getMinutes())}-${pad(at.getSeconds())}-${String(at.getMilliseconds()).padStart(3, "0")}.${ext}`;
}

/** Capture time from a datetime-only name, else null. */
export function imageTakenAt(name: string): number | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2})-(\d{2})-(\d{2})(?:-(\d{3}))?\./.exec(name);
  if (!m) return null;
  return new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +m[6], +(m[7] ?? 0)).getTime();
}

export async function uploadAlbumImage(folderId: string, blob: Blob, at = new Date()): Promise<string> {
  const ext = blob.type.includes("png") ? "png" : blob.type.includes("webp") ? "webp" : "jpg";
  return uploadBlob(folderId, imageFileName(at, ext), blob);
}

export async function uploadBodyScan(angle: BodyScanAngle, blob: Blob): Promise<string> {
  const folder = await ensureFolder(await ensureFolder(root(), "collections"), angle);
  return uploadAlbumImage(folder, blob);
}

export const deleteDriveFile = (id: string) => trash(id);
