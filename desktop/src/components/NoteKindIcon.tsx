import { File, FileText, Film, Image, Link2, Mic, NotebookText } from "lucide-react";
import type { MemoryNoteKind } from "@/domain/models";

export function NoteKindIcon({ kind, size = 16 }: { kind: MemoryNoteKind; size?: number }) {
  switch (kind) {
    case "TEXT": return <NotebookText size={size} />;
    case "LINK": return <Link2 size={size} />;
    case "AUDIO": return <Mic size={size} />;
    case "IMAGE": return <Image size={size} />;
    case "VIDEO": return <Film size={size} />;
    case "PDF": return <FileText size={size} />;
    default: return <File size={size} />;
  }
}

export const KIND_LABEL: Record<MemoryNoteKind, string> = {
  TEXT: "Note", LINK: "Link", AUDIO: "Audio", IMAGE: "Image", VIDEO: "Video", PDF: "PDF", FILE: "File",
};
