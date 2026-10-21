// Plain-text view of a Learn tile (LearningContextBuilder), shared by the .txt export and the Gemini prompts.
import { dateKey } from "./dates";
import type { BrainNode, BrainState, LearningTileStatus, MemoryNote, SyncAsset } from "./models";

export const MAX_PDF_BYTES = 10 * 1024 * 1024;

const STATUS_TEXT: Record<LearningTileStatus, string> = {
  BACKLOG: "Backlog",
  PRIORITY: "Backlog (priority)",
  IN_PROGRESS: "In Progress",
  ON_HOLD: "On Hold",
  TO_REVIEW: "Review",
  LEARNED: "Learned",
};

const kindName = (kind: string) => kind.charAt(0) + kind.slice(1).toLowerCase();

function tileText(s: BrainState, node: BrainNode, prerequisites: BrainNode[], dependents: BrainNode[], pdfSink: SyncAsset[] | null): string {
  const out: string[] = [];
  const line = (t: string) => out.push(t);
  const tile = s.learningTiles.find((t) => t.id === node.id);
  line(`# ${node.name}`);
  if (tile) {
    line(`Status: ${STATUS_TEXT[tile.status]}`);
    line(`Last activity: ${dateKey(tile.lastActivityAt)}`);
    if (tile.learnedAt !== null) line(`Learned: ${dateKey(tile.learnedAt)}`);
    if (tile.nextReviewAt !== null) line(`Next review: ${dateKey(tile.nextReviewAt)}`);
  }
  if (node.tags.length) line(`Tags: ${node.tags.join(", ")}`);
  line(`Created: ${dateKey(node.createdAt)}`);
  if (prerequisites.length) line(`Depends on: ${prerequisites.map((n) => n.name).join(", ")}`);
  if (dependents.length) line(`Used in: ${dependents.map((n) => n.name).join(", ")}`);

  const assets = new Map(s.assets.map((a) => [a.id, a]));
  const notes = s.notes
    .filter((n) => n.nodeId === node.id)
    .sort((a: MemoryNote, b: MemoryNote) => Number(b.pinned) - Number(a.pinned) || a.createdAt - b.createdAt);
  const files: string[] = [];
  let text = out.join("\n") + "\n";
  for (const note of notes) {
    const desc = note.description.trim();
    const body = note.body.trim();
    if (note.kind === "TEXT") {
      text += `\n## ${note.title.trim() || "Note"}\n`;
      if (desc) text += `${desc}\n\n`;
      if (body) text += `${body}\n`;
    } else if (note.kind === "LINK") {
      text += `\n## Link: ${note.title.trim() || note.url || ""}\n`;
      if (note.url) text += `${note.url}\n`;
      if (desc) text += `${desc}\n`;
      if (body) text += `${body}\n`;
    } else {
      const asset = note.assetId ? assets.get(note.assetId) : undefined;
      const name = asset?.fileName.trim() || note.title.trim() || "File";
      files.push(`${kindName(note.kind)}: ${name}`);
      if (desc || body) {
        text += `\n## ${kindName(note.kind)}: ${note.title.trim() || name}\n`;
        if (desc) text += `${desc}\n`;
        if (body) text += `${body}\n`;
      }
      if (pdfSink && asset && asset.mimeType.toLowerCase() === "application/pdf" && asset.sizeBytes > 0 && asset.sizeBytes <= MAX_PDF_BYTES) {
        pdfSink.push(asset);
      }
    }
  }
  if (files.length) text += `\n## Files\n${files.map((f) => `- ${f}\n`).join("")}`;
  return text;
}

export interface LearningText {
  title: string;
  text: string;
  /** PDF assets small enough to attach; only filled when asked for. */
  pdfAssets: SyncAsset[];
}

export function buildLearningText(s: BrainState, nodeId: string, opts: { includePrerequisites?: boolean; includePdfs?: boolean } = {}): LearningText {
  const node = s.nodes.find((n) => n.id === nodeId);
  if (!node) throw new Error("Tile not found");
  const learn = new Map(s.nodes.filter((n) => n.section === "MODEL").map((n) => [n.id, n]));
  const prerequisites = s.learningLinks.filter((l) => l.fromNodeId === nodeId).map((l) => learn.get(l.toNodeId)).filter((n): n is BrainNode => !!n);
  const dependents = s.learningLinks.filter((l) => l.toNodeId === nodeId).map((l) => learn.get(l.fromNodeId)).filter((n): n is BrainNode => !!n);
  const pdfAssets: SyncAsset[] = [];
  let text = tileText(s, node, prerequisites, dependents, opts.includePdfs ? pdfAssets : null);
  if (opts.includePrerequisites) {
    const rule = "=".repeat(60);
    for (const p of prerequisites) text += `\n\n${rule}\nPREREQUISITE\n${rule}\n\n${tileText(s, p, [], [], null)}`;
  }
  return { title: node.name, text: text.trim(), pdfAssets };
}

export function exportFileName(title: string): string {
  const clean = title.trim().replace(/[\\/:*?"<>|\p{Cc}]/gu, "").replace(/\s+/g, " ").slice(0, 80);
  return `${clean || "Learn tile"}.txt`;
}
