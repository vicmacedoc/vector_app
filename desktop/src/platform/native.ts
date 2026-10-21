// Typed wrappers around the Rust commands in src-tauri/src.
import { convertFileSrc, invoke } from "@tauri-apps/api/core";

export interface AccessToken {
  accessToken: string;
  expiresAtMs: number;
}

export interface LocalFileInfo {
  name: string;
  size: number;
  mimeType: string;
}

export type GeminiKeySource = "custom" | "build" | "none";
export type HelloResult = "verified" | "cancelled" | "unavailable";

/** False in a plain browser (`npm run dev` without Tauri): the UI runs on localStorage with Drive disabled. */
export const inTauri = typeof window !== "undefined" && "__TAURI_INTERNALS__" in window;

const browserStore = {
  read: async (name: string) => localStorage.getItem(`vector:${name}`),
  write: async (name: string, contents: string) => localStorage.setItem(`vector:${name}`, contents),
  delete: async (name: string) => localStorage.removeItem(`vector:${name}`),
};

export const native = {
  signIn: (clientId: string, clientSecret: string) => invoke<AccessToken>("oauth_sign_in", { clientId, clientSecret }),
  accessToken: (clientId: string, clientSecret: string, force: boolean) =>
    invoke<AccessToken>("oauth_access_token", { clientId, clientSecret, force }),
  signedIn: () => (inTauri ? invoke<boolean>("oauth_signed_in") : Promise.resolve(false)),
  signOut: () => invoke<void>("oauth_sign_out"),

  storeRead: (name: string) => (inTauri ? invoke<string | null>("store_read", { name }) : browserStore.read(name)),
  storeWrite: (name: string, contents: string) =>
    inTauri ? invoke<void>("store_write", { name, contents }) : browserStore.write(name, contents),
  storeDelete: (name: string) => (inTauri ? invoke<void>("store_delete", { name }) : browserStore.delete(name)),

  cacheLookup: (assetId: string, fileName: string) =>
    inTauri ? invoke<string | null>("cache_lookup", { assetId, fileName }) : Promise.resolve(null),
  cacheFromLocal: (source: string, assetId: string, fileName: string) =>
    invoke<string>("cache_from_local", { source, assetId, fileName }),
  cacheRemove: (assetId: string) => invoke<void>("cache_remove", { assetId }),
  cacheWriteBase64: (assetId: string, fileName: string, dataBase64: string) =>
    invoke<string>("cache_write_base64", { assetId, fileName, dataBase64 }),
  readFileBase64: (path: string, maxBytes: number) => invoke<string>("read_file_base64", { path, maxBytes }),
  fileInfo: (path: string) => invoke<LocalFileInfo>("file_info", { path }),
  readTextFile: (path: string, maxBytes: number) => invoke<string | null>("read_text_file", { path, maxBytes }),
  writeTextFile: (path: string, contents: string) => invoke<void>("write_text_file", { path, contents }),

  driveUpload: (token: string, parentId: string, name: string, mimeType: string, path: string) =>
    invoke<{ id: string; size: number }>("drive_upload", { token, parentId, name, mimeType, path }),
  driveDownload: (token: string, fileId: string, assetId: string, fileName: string) =>
    invoke<string>("drive_download", { token, fileId, assetId, fileName }),

  geminiStatus: () => (inTauri ? invoke<GeminiKeySource>("gemini_status") : Promise.resolve<GeminiKeySource>("none")),
  geminiKeySet: (key: string) => invoke<void>("gemini_key_set", { key }),
  geminiKeyClear: () => invoke<void>("gemini_key_clear"),
  geminiGenerate: (model: string, body: unknown) => {
    if (!inTauri) return Promise.reject(new Error("AI needs the desktop app (not the browser preview)."));
    return invoke<unknown>("gemini_generate", { model, body });
  },
  helloVerify: (reason: string) => (inTauri ? invoke<HelloResult>("hello_verify", { reason }) : Promise.resolve<HelloResult>("unavailable")),

  openPath: (path: string) => invoke<void>("open_path", { path }),
  openCapture: () => invoke<void>("open_capture"),
  fileSrc: (path: string) => convertFileSrc(path),
};

export function errorMessage(e: unknown): string {
  if (typeof e === "string") return e;
  if (e instanceof Error) return e.message;
  return String(e);
}
