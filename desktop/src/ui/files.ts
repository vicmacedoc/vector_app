import { useEffect, useRef, useState } from "react";
import { getCurrentWebview } from "@tauri-apps/api/webview";
import { open, save } from "@tauri-apps/plugin-dialog";
import { openUrl } from "@tauri-apps/plugin-opener";
import { inTauri, native } from "@/platform/native";

export async function pickFiles(): Promise<string[]> {
  const picked = await open({ multiple: true, directory: false });
  if (!picked) return [];
  return Array.isArray(picked) ? picked : [picked];
}

/** Text of one picked file (JSON presets etc.), or null when cancelled. */
export async function pickTextFile(extensions: string[], maxBytes = 4 * 1024 * 1024): Promise<{ name: string; text: string } | null> {
  const picked = await open({ multiple: false, directory: false, filters: [{ name: extensions.join(", ").toUpperCase(), extensions }] });
  const path = Array.isArray(picked) ? picked[0] : picked;
  if (!path) return null;
  const text = await native.readTextFile(path, maxBytes);
  if (text === null) throw new Error("File is too large or not text");
  return { name: path.split(/[\\/]/).pop() ?? path, text };
}

/** Save-as dialog, then writes [contents]. Returns false when cancelled. */
export async function saveTextFile(defaultName: string, contents: string, extensions: string[]): Promise<boolean> {
  if (!inTauri) {
    const a = document.createElement("a");
    a.href = URL.createObjectURL(new Blob([contents], { type: "text/plain" }));
    a.download = defaultName;
    a.click();
    URL.revokeObjectURL(a.href);
    return true;
  }
  const path = await save({ defaultPath: defaultName, filters: [{ name: extensions.join(", ").toUpperCase(), extensions }] });
  if (!path) return false;
  await native.writeTextFile(path, contents);
  return true;
}

/** Files dropped onto the window while [enabled]; returns whether a drag is hovering. */
export function useFileDrop(onDrop: (paths: string[]) => void, enabled = true): boolean {
  const [hovering, setHovering] = useState(false);
  const handler = useRef(onDrop);
  handler.current = onDrop;
  useEffect(() => {
    if (!enabled || !inTauri) return;
    let unlisten: (() => void) | undefined;
    let disposed = false;
    getCurrentWebview()
      .onDragDropEvent((event) => {
        const p = event.payload;
        if (p.type === "enter" || p.type === "over") setHovering(true);
        else if (p.type === "leave") setHovering(false);
        else if (p.type === "drop") {
          setHovering(false);
          if (p.paths.length) handler.current(p.paths);
        }
      })
      .then((fn) => (disposed ? fn() : (unlisten = fn)))
      .catch(() => undefined);
    return () => {
      disposed = true;
      unlisten?.();
    };
  }, [enabled]);
  return hovering;
}

export function openLink(url: string) {
  const target = /^[a-z]+:\/\//i.test(url) ? url : `https://${url}`;
  void openUrl(target);
}

export const looksLikeUrl = (s: string) => /^(https?:\/\/|www\.)\S+$/i.test(s.trim());

export function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`;
  return `${(n / 1024 / 1024 / 1024).toFixed(1)} GB`;
}
