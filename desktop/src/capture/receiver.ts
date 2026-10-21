import { useEffect } from "react";
import { listen } from "@tauri-apps/api/event";
import * as ops from "@/domain/ops";
import { useBrain } from "@/store/brain";
import { CAPTURE_EVENT, type CapturePayload } from "./protocol";

/** Applies quick captures sent by the capture window; everything lands in the Inbox except Learn tiles. */
export function useCaptureReceiver() {
  useEffect(() => {
    let unlisten: (() => void) | undefined;
    let disposed = false;
    void listen<CapturePayload>(CAPTURE_EVENT, async ({ payload: p }) => {
      const { commit, toast, addFiles } = useBrain.getState();
      switch (p.kind) {
        case "note":
          commit((s) => ops.createTextNote(s, p.title, p.body, null)[0]);
          toast("Note added to Inbox");
          break;
        case "link":
          commit((s) => ops.createLinkNote(s, p.title, p.url, null)[0]);
          toast("Link added to Inbox");
          break;
        case "todo":
          commit((s) => ops.createTask(s, { title: p.title, priority: p.priority, dueAt: p.dueAt }, null)[0]);
          toast("To-do added to Inbox");
          break;
        case "learn":
          commit((s) => ops.createNode(s, "MODEL", p.name)[0]);
          toast(`Learn tile “${p.name}” created`);
          break;
        case "files": {
          const n = await addFiles(p.paths, null);
          if (n) toast(`${n} file${n === 1 ? "" : "s"} added to Inbox · uploading`);
          break;
        }
      }
    })
      .then((fn) => (disposed ? fn() : (unlisten = fn)))
      .catch(() => undefined);
    return () => {
      disposed = true;
      unlisten?.();
    };
  }, []);
}
