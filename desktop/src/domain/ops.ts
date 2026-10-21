// Brain edits as pure BrainState -> BrainState functions. Each mirrors a method of mobile BrainRepository
// so records written here look exactly like the phone's.
import { dateKey } from "./dates";
import { touched, withReview, withStatus, wouldCycle, type LearnRules } from "./learn";
import {
  basePoints, newId, normNode, normNote, normTask, normTile,
  type BrainSection, type BrainState, type LearningTile, type LearningTileStatus, type MemoryNote, type MemoryNoteKind,
  type MotionPriority, type MotionRecurrence, type MotionTask, type SyncAsset, type SyncSupermarket,
} from "./models";
import { nextOccurrence, supermarketCycle, supermarketPoints, taskPointsAt } from "./motionScoring";

type S = BrainState;

const sanitize = (name: string) => {
  const safe = name.replace(/[\\/:*?"<>|]/g, "_").trim().slice(0, 200);
  return safe || `file_${newId("x").slice(-8)}`;
};

// ---- Tags ----

/** Library name for [name], reusing a same-section tag that differs only by case. */
export function ensureTag(s: S, name: string, section: BrainSection): [S, string | null] {
  const trimmed = name.trim().replace(/\s+/g, " ");
  if (!trimmed) return [s, null];
  const existing = s.tags.find((t) => t.section === section && t.name.toLowerCase() === trimmed.toLowerCase());
  if (existing) return [s, existing.name];
  const tag = { id: newId("tag"), name: trimmed, createdAt: Date.now(), section };
  return [{ ...s, tags: [...s.tags, tag] }, trimmed];
}

export const deleteTag = (s: S, id: string): S => ({ ...s, tags: s.tags.filter((t) => t.id !== id) });

// ---- Nodes ----

export function createNode(s: S, section: BrainSection, name: string, tags: string[] = []): [S, string] {
  const siblings = s.nodes.filter((n) => n.section === section && n.parentId === null).length;
  const node = normNode({ id: newId("node"), section, name: name.trim(), sortOrder: siblings, tags });
  let next: S = { ...s, nodes: [...s.nodes, node] };
  if (section === "MODEL") next = { ...next, learningTiles: [...next.learningTiles, normTile({ id: node.id })] };
  return [next, node.id];
}

export function renameNode(s: S, id: string, name: string): S {
  const trimmed = name.trim();
  if (!trimmed) return s;
  return { ...s, nodes: s.nodes.map((n) => (n.id === id && n.name !== trimmed ? { ...n, name: trimmed } : n)) };
}

export const setNodeTags = (s: S, id: string, tags: string[]): S => ({
  ...s,
  nodes: s.nodes.map((n) => (n.id === id ? { ...n, tags } : n)),
});

/** Tile, its sources and their assets (Memory and Learn); Learn tiles also lose their status row and links. */
export function deleteTile(s: S, id: string): S {
  const notes = s.notes.filter((n) => n.nodeId === id);
  const assetIds = new Set(notes.map((n) => n.assetId).filter((a): a is string => !!a));
  return {
    ...s,
    nodes: s.nodes.filter((n) => n.id !== id),
    notes: s.notes.filter((n) => n.nodeId !== id),
    assets: s.assets.filter((a) => !assetIds.has(a.id)),
    learningTiles: s.learningTiles.filter((t) => t.id !== id),
    learningLinks: s.learningLinks.filter((l) => l.fromNodeId !== id && l.toNodeId !== id),
  };
}

/** Points already earned stay in the ledger. */
export function deleteMotionList(s: S, id: string): S {
  return { ...s, nodes: s.nodes.filter((n) => n.id !== id), tasks: s.tasks.filter((t) => t.nodeId !== id) };
}

// ---- Notes ----

function touchLearn(s: S, nodeId: string | null, now = Date.now()): S {
  if (!nodeId) return s;
  return { ...s, learningTiles: s.learningTiles.map((t) => (t.id === nodeId ? touched(t, now) : t)) };
}

export function createTextNote(s: S, title: string, body: string, nodeId: string | null, tags: string[] = [], description = ""): [S, string] {
  const note = normNote({ id: newId("note"), nodeId, kind: "TEXT", title, body, tags, description });
  return [touchLearn({ ...s, notes: [...s.notes, note] }, nodeId), note.id];
}

export function createLinkNote(s: S, title: string, url: string, nodeId: string | null): [S, string] {
  const note = normNote({ id: newId("note"), nodeId, kind: "LINK", title: title.trim() || url, url });
  return [touchLearn({ ...s, notes: [...s.notes, note] }, nodeId), note.id];
}

export function kindForMime(mime: string): MemoryNoteKind {
  const m = mime.toLowerCase();
  if (m.startsWith("audio/")) return "AUDIO";
  if (m.startsWith("image/")) return "IMAGE";
  if (m.startsWith("video/")) return "VIDEO";
  if (m === "application/pdf") return "PDF";
  if (m.startsWith("text/")) return "TEXT";
  return "FILE";
}

/** Drive folder (under `brain/`) a new file for [nodeId] goes to; same layout the phone's folder scan reads. */
export function folderFor(s: S, nodeId: string | null): string {
  const node = nodeId ? s.nodes.find((n) => n.id === nodeId) : undefined;
  if (!node) return "Inbox";
  return node.section === "MODEL" ? `Model/${node.id}` : `Memory/${sanitize(node.name)}`;
}

export function createAssetNote(
  s: S,
  file: { assetId: string; fileName: string; mimeType: string; sizeBytes: number; body?: string },
  nodeId: string | null,
): [S, string] {
  const now = Date.now();
  const asset: SyncAsset = {
    id: file.assetId,
    mimeType: file.mimeType,
    sizeBytes: file.sizeBytes,
    driveFileId: null,
    fileName: file.fileName,
    description: "",
    createdAt: now,
    updatedAt: now,
    path: `${folderFor(s, nodeId)}/${sanitize(file.fileName)}`,
  };
  const note = normNote({
    id: newId("note"), nodeId, kind: kindForMime(file.mimeType), title: file.fileName,
    body: file.body ?? "", assetId: asset.id,
  });
  return [touchLearn({ ...s, assets: [...s.assets, asset], notes: [...s.notes, note] }, nodeId), note.id];
}

export function saveNote(s: S, note: MemoryNote): S {
  const now = Date.now();
  const saved = { ...note, updatedAt: now };
  return touchLearn({ ...s, notes: s.notes.map((n) => (n.id === note.id ? saved : n)) }, saved.nodeId, now);
}

// ---- Recordings (audio player) ----

export function setAssetDescription(s: S, assetId: string, description: string): S {
  const trimmed = description.trim();
  return {
    ...s,
    assets: s.assets.map((a) => (a.id === assetId && a.description !== trimmed ? { ...a, description: trimmed, updatedAt: Date.now() } : a)),
  };
}

const isGeneratedAudioTitle = (title: string, fileName: string) => {
  const t = title.trim();
  return !t || t === fileName || t.startsWith("Voice note ") || t.startsWith("Watch voice ");
};

/** Transcript into the body; the AI title only replaces a generated one (BrainRepository.applyTranscription). */
export function applyTranscription(s: S, noteId: string, title: string, transcript: string): S {
  const note = s.notes.find((n) => n.id === noteId);
  if (!note) return s;
  const fileName = note.assetId ? s.assets.find((a) => a.id === note.assetId)?.fileName ?? "" : "";
  const keep = !isGeneratedAudioTitle(note.title, fileName);
  return saveNote(s, { ...note, title: keep ? note.title : title.trim() || note.title, body: transcript });
}

export function moveNotes(s: S, ids: string[], nodeId: string | null): S {
  const now = Date.now();
  const set = new Set(ids);
  return touchLearn({ ...s, notes: s.notes.map((n) => (set.has(n.id) ? { ...n, nodeId, updatedAt: now } : n)) }, nodeId, now);
}

export function deleteNote(s: S, id: string): S {
  const note = s.notes.find((n) => n.id === id);
  if (!note) return s;
  return {
    ...s,
    notes: s.notes.filter((n) => n.id !== id),
    assets: note.assetId ? s.assets.filter((a) => a.id !== note.assetId) : s.assets,
  };
}

// ---- Learn ----

function putTile(s: S, id: string, fn: (t: LearningTile) => LearningTile): S {
  const tile = s.learningTiles.find((t) => t.id === id) ?? normTile({ id });
  const updated = fn(tile);
  const exists = s.learningTiles.some((t) => t.id === id);
  return {
    ...s,
    learningTiles: exists ? s.learningTiles.map((t) => (t.id === id ? updated : t)) : [...s.learningTiles, updated],
  };
}

export const setLearnStatus = (s: S, id: string, status: LearningTileStatus, rules?: LearnRules): S =>
  putTile(s, id, (t) => withStatus(t, status, rules));

export const completeReview = (s: S, id: string, solid: boolean, score: number | null, rules?: LearnRules): S =>
  putTile(s, id, (t) => withReview(t, solid, score, rules));

/** Quiz outside the Review status: records the score and counts as study time. */
export function saveQuizScore(s: S, id: string, score: number, now = Date.now()): S {
  if (!s.learningTiles.some((t) => t.id === id)) return s;
  return putTile(s, id, (t) => touched({ ...t, lastReviewScore: score, lastReviewedAt: now }, now));
}

/** "[from] depends on [to]"; null when it would be a self link or close a cycle. */
export function addLearningLink(s: S, from: string, to: string): S | null {
  if (wouldCycle(s.learningLinks, from, to)) return null;
  if (s.learningLinks.some((l) => l.fromNodeId === from && l.toNodeId === to)) return s;
  return { ...s, learningLinks: [...s.learningLinks, { fromNodeId: from, toNodeId: to, createdAt: Date.now() }] };
}

export const removeLearningLink = (s: S, from: string, to: string): S => ({
  ...s,
  learningLinks: s.learningLinks.filter((l) => !(l.fromNodeId === from && l.toNodeId === to)),
});

// ---- Motion ----

export interface TaskDraft {
  title: string;
  notes?: string;
  priority?: MotionPriority;
  dueAt?: number | null;
  recurrence?: MotionRecurrence;
  points?: number | null;
  target?: number | null;
  unit?: string | null;
  linkedTileId?: string | null;
}

export function createTask(s: S, draft: TaskDraft, nodeId: string | null): [S, string] {
  const priority = draft.priority ?? "NONE";
  const base = basePoints(priority);
  const points = draft.points ?? null;
  const dueAt = draft.dueAt ?? null;
  const task = normTask({
    id: newId("task"),
    nodeId,
    title: draft.title.trim(),
    notes: (draft.notes ?? "").trim(),
    priority,
    dueAt,
    recurrence: draft.recurrence ?? "NONE",
    linkedTileId: draft.linkedTileId ?? null,
    points: points ?? base,
    pointsEdited: points !== null && points !== base,
    firstDueAt: dueAt,
    target: draft.target && draft.target > 0 ? draft.target : null,
    unit: draft.unit?.trim() || null,
  });
  return [{ ...s, tasks: [...s.tasks, task] }, task.id];
}

/** Saves an edited item; the first deadline is remembered so the early bonus never grows. */
export function saveTask(s: S, task: MotionTask): S {
  const previous = s.tasks.find((t) => t.id === task.id);
  const saved: MotionTask = {
    ...task,
    firstDueAt: previous?.firstDueAt ?? task.firstDueAt ?? task.dueAt,
    points: task.pointsEdited ? task.points : basePoints(task.priority),
    target: task.target && task.target > 0 ? task.target : null,
    updatedAt: Date.now(),
  };
  const next = { ...s, tasks: s.tasks.map((t) => (t.id === task.id ? saved : t)) };
  if (saved.target !== null && saved.status === "OPEN" && saved.progress >= saved.target) return checkTask(next, saved.id);
  return next;
}

export const taskEventId = (taskId: string) => `task_${taskId}`;

/** Freezes the item's points into the ledger and spawns the next recurring occurrence. */
export function checkTask(s: S, id: string, now = Date.now()): S {
  const task = s.tasks.find((t) => t.id === id);
  if (!task || task.status === "DONE") return s;
  const points = taskPointsAt(task, now);
  let spawned: MotionTask | null = null;
  if (task.recurrence !== "NONE" && task.dueAt !== null) {
    const nextDue = nextOccurrence(task.dueAt, task.recurrence);
    spawned = {
      ...task, id: newId("task"), dueAt: nextDue, firstDueAt: nextDue, progress: 0, status: "OPEN",
      completedAt: null, earnedPoints: null, spawnedTaskId: null, createdAt: now, updatedAt: now,
    };
  }
  const done: MotionTask = {
    ...task,
    status: "DONE",
    completedAt: now,
    earnedPoints: points,
    spawnedTaskId: spawned?.id ?? null,
    progress: task.target !== null ? Math.max(task.target, task.progress) : task.progress,
    updatedAt: now,
  };
  const event = {
    id: taskEventId(id), dateKey: dateKey(now), sourceType: "TASK" as const, sourceId: id,
    title: task.title, points, createdAt: now,
  };
  return {
    ...s,
    tasks: [...s.tasks.map((t) => (t.id === id ? done : t)), ...(spawned ? [spawned] : [])],
    scoreEvents: [...s.scoreEvents.filter((e) => e.id !== event.id), event],
  };
}

/** Reopens the item, removes its points and the still-open occurrence its check spawned. */
export function uncheckTask(s: S, id: string, progress: number | null = null): S {
  const task = s.tasks.find((t) => t.id === id);
  if (!task || task.status !== "DONE") return s;
  let next = s;
  const spawned = task.spawnedTaskId ? s.tasks.find((t) => t.id === task.spawnedTaskId) : undefined;
  if (spawned?.status === "OPEN") next = deleteTask(next, spawned.id);
  const reopened: MotionTask = {
    ...task, status: "OPEN", completedAt: null, earnedPoints: null, spawnedTaskId: null,
    progress: task.target !== null ? progress ?? 0 : task.progress, updatedAt: Date.now(),
  };
  return {
    ...next,
    tasks: next.tasks.map((t) => (t.id === id ? reopened : t)),
    scoreEvents: next.scoreEvents.filter((e) => e.id !== taskEventId(id)),
  };
}

/** Numeric items check themselves at the target and uncheck when progress drops below it. */
export function setProgress(s: S, id: string, progress: number): S {
  const task = s.tasks.find((t) => t.id === id);
  if (!task || task.target === null) return s;
  const value = Math.max(0, progress);
  if (task.status === "DONE" && value < task.target) return uncheckTask(s, id, value);
  const updated = { ...s, tasks: s.tasks.map((t) => (t.id === id ? { ...t, progress: value, updatedAt: Date.now() } : t)) };
  return task.status === "OPEN" && value >= task.target ? checkTask(updated, id) : updated;
}

export function snoozeTask(s: S, id: string, until: number): S {
  return {
    ...s,
    tasks: s.tasks.map((t) =>
      t.id === id ? { ...t, dueAt: until, firstDueAt: t.firstDueAt ?? until, updatedAt: Date.now() } : t,
    ),
  };
}

export const deleteTask = (s: S, id: string): S => ({
  ...s,
  tasks: s.tasks.filter((t) => t.id !== id),
  scoreEvents: s.scoreEvents.filter((e) => e.id !== taskEventId(id)),
});

export function moveTasks(s: S, ids: string[], nodeId: string | null): S {
  const set = new Set(ids);
  const now = Date.now();
  return { ...s, tasks: s.tasks.map((t) => (set.has(t.id) ? { ...t, nodeId, updatedAt: now } : t)) };
}

// ---- Supermarket ----

export const setSupermarket = (s: S, supermarket: SyncSupermarket): S => ({ ...s, supermarket });

export function currentSupermarketCycle(s: S, today = dateKey(Date.now())) {
  const last = s.scoreEvents
    .filter((e) => e.sourceType === "SUPERMARKET")
    .map((e) => e.sourceId)
    .filter((d) => /^\d{4}-\d{2}-\d{2}$/.test(d))
    .sort()
    .pop();
  return supermarketCycle(last ?? null, today, s.settings.supermarketDay);
}

/** Logs a trip for the open cycle; null when this week's trip is already logged. */
export function recordSupermarketTrip(s: S, now = Date.now()): [S, number] | null {
  const today = dateKey(now);
  const cycle = currentSupermarketCycle(s, today);
  if (!cycle.open) return null;
  const points = supermarketPoints(cycle.dueDate, today);
  const event = {
    id: `supermarket_${cycle.dueDate}`, dateKey: today, sourceType: "SUPERMARKET" as const,
    sourceId: cycle.dueDate, title: "Supermarket trip", points, createdAt: now,
  };
  return [{ ...s, scoreEvents: [...s.scoreEvents.filter((e) => e.id !== event.id), event] }, points];
}
