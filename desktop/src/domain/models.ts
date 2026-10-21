// Mirrors the Room entities in core/src/main/java/com/vm/core/models. Field names, enum names and
// defaults must match Kotlin so records written here field-merge cleanly with the phone's.

export type BrainSection = "MEMORY" | "MODEL" | "MOTION";
export type MemoryNoteKind = "TEXT" | "AUDIO" | "IMAGE" | "LINK" | "PDF" | "VIDEO" | "FILE";
export type LearningTileStatus = "BACKLOG" | "PRIORITY" | "IN_PROGRESS" | "ON_HOLD" | "TO_REVIEW" | "LEARNED";
export type MotionPriority = "NONE" | "LOW" | "MEDIUM" | "HIGH";
export type MotionTaskStatus = "OPEN" | "DONE" | "CANCELLED";
export type MotionRecurrence = "NONE" | "DAILY" | "WEEKLY" | "MONTHLY";
export type MotionScoreSource = "TASK" | "SUPERMARKET";
export type DayOfWeek = "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";

export const NAVY_ARGB = -16769217; // 0xFF001F3F as a signed Int

export interface BrainNode {
  id: string;
  parentId: string | null;
  section: BrainSection;
  name: string;
  colorArgb: number;
  sortOrder: number;
  driveFolderId: string | null;
  isSystem: boolean;
  tags: string[];
  createdAt: number;
}

export interface MemoryNote {
  id: string;
  nodeId: string | null;
  kind: MemoryNoteKind;
  title: string;
  description: string;
  body: string;
  assetId: string | null;
  url: string | null;
  tags: string[];
  pinned: boolean;
  createdAt: number;
  updatedAt: number;
}

/** BrainAsset without device-local fields; `path` is relative to Drive `brain/`. */
export interface SyncAsset {
  id: string;
  mimeType: string;
  sizeBytes: number;
  driveFileId: string | null;
  fileName: string;
  description: string;
  createdAt: number;
  updatedAt: number;
  path: string;
}

export interface LearningTile {
  id: string;
  status: LearningTileStatus;
  lastActivityAt: number;
  learnedAt: number | null;
  reviewStep: number;
  nextReviewAt: number | null;
  lastReviewScore: number | null;
  lastReviewedAt: number | null;
  createdAt: number;
  updatedAt: number;
}

export interface LearningLink {
  fromNodeId: string;
  toNodeId: string;
  createdAt: number;
}

export interface BrainTag {
  id: string;
  name: string;
  createdAt: number;
  section: BrainSection;
}

export interface MotionTask {
  id: string;
  nodeId: string | null;
  title: string;
  notes: string;
  priority: MotionPriority;
  dueAt: number | null;
  leadMinutes: number;
  recurrence: MotionRecurrence;
  status: MotionTaskStatus;
  completedAt: number | null;
  sortOrder: number;
  linkedTileId: string | null;
  createdAt: number;
  updatedAt: number;
  points: number;
  pointsEdited: boolean;
  firstDueAt: number | null;
  target: number | null;
  progress: number;
  unit: string | null;
  earnedPoints: number | null;
  spawnedTaskId: string | null;
}

export interface MotionScoreEvent {
  id: string;
  dateKey: string;
  sourceType: MotionScoreSource;
  sourceId: string;
  title: string;
  points: number;
  createdAt: number;
}

export interface VectorItem {
  id: string;
  title: string;
  isChecked: boolean;
  quantity: number | null;
  remaining: number | null;
  unit: string | null;
  price: number | null;
  priority: string | null;
  category: string | null;
  order: number | null;
  dueDate: number | null;
}

export interface SyncSupermarket {
  name: string;
  sections: string[];
  items: VectorItem[];
}

export interface SyncSettings {
  motionDailyTarget: number;
  supermarketDay: DayOfWeek;
}

export interface SyncDevice {
  deviceId: string;
  name: string;
  platform: string;
}

export interface BrainState {
  formatVersion: number;
  rev: number;
  writtenBy: SyncDevice | null;
  writtenAt: number;
  settings: SyncSettings;
  nodes: BrainNode[];
  notes: MemoryNote[];
  assets: SyncAsset[];
  learningTiles: LearningTile[];
  learningLinks: LearningLink[];
  tags: BrainTag[];
  tasks: MotionTask[];
  scoreEvents: MotionScoreEvent[];
  supermarket: SyncSupermarket;
}

export const BRAIN_STATE_FORMAT_VERSION = 1;
export const DEFAULT_DAILY_TARGET = 50;
export const DEFAULT_SUPERMARKET_DAY: DayOfWeek = "SUNDAY";

export function basePoints(priority: MotionPriority): number {
  switch (priority) {
    case "LOW": return 10;
    case "MEDIUM": return 15;
    case "HIGH": return 20;
    default: return 5;
  }
}

/** `{prefix}_{epochMillis}_{8 hex}`, same as the phone. */
export function newId(prefix: string): string {
  const hex = crypto.getRandomValues(new Uint8Array(4));
  return `${prefix}_${Date.now()}_${Array.from(hex, (b) => b.toString(16).padStart(2, "0")).join("")}`;
}

type Loose<T> = { [K in keyof T]?: T[K] | null | undefined };

const str = (v: unknown, d = ""): string => (typeof v === "string" ? v : d);
const strOrNull = (v: unknown): string | null => (typeof v === "string" ? v : null);
const num = (v: unknown, d = 0): number => (typeof v === "number" && Number.isFinite(v) ? v : d);
const numOrNull = (v: unknown): number | null => (typeof v === "number" && Number.isFinite(v) ? v : null);
const bool = (v: unknown, d = false): boolean => (typeof v === "boolean" ? v : d);
const strings = (v: unknown): string[] => (Array.isArray(v) ? v.filter((x): x is string => typeof x === "string") : []);
function oneOf<T extends string>(v: unknown, values: readonly T[], d: T): T {
  return typeof v === "string" && (values as readonly string[]).includes(v) ? (v as T) : d;
}

const SECTIONS = ["MEMORY", "MODEL", "MOTION"] as const;
const NOTE_KINDS = ["TEXT", "AUDIO", "IMAGE", "LINK", "PDF", "VIDEO", "FILE"] as const;
const TILE_STATUSES = ["BACKLOG", "PRIORITY", "IN_PROGRESS", "ON_HOLD", "TO_REVIEW", "LEARNED"] as const;
const PRIORITIES = ["NONE", "LOW", "MEDIUM", "HIGH"] as const;
const TASK_STATUSES = ["OPEN", "DONE", "CANCELLED"] as const;
const RECURRENCES = ["NONE", "DAILY", "WEEKLY", "MONTHLY"] as const;
export const DAYS: readonly DayOfWeek[] = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];

// Normalizers fill every field with the Kotlin default, like kotlinx decoding with encodeDefaults.

export function normNode(o: Loose<BrainNode>, now = Date.now()): BrainNode {
  return {
    id: str(o.id),
    parentId: strOrNull(o.parentId),
    section: oneOf(o.section, SECTIONS, "MEMORY"),
    name: str(o.name),
    colorArgb: num(o.colorArgb, NAVY_ARGB),
    sortOrder: num(o.sortOrder),
    driveFolderId: strOrNull(o.driveFolderId),
    isSystem: bool(o.isSystem),
    tags: strings(o.tags),
    createdAt: num(o.createdAt, now),
  };
}

export function normNote(o: Loose<MemoryNote>, now = Date.now()): MemoryNote {
  return {
    id: str(o.id),
    nodeId: strOrNull(o.nodeId),
    kind: oneOf(o.kind, NOTE_KINDS, "TEXT"),
    title: str(o.title),
    description: str(o.description),
    body: str(o.body),
    assetId: strOrNull(o.assetId),
    url: strOrNull(o.url),
    tags: strings(o.tags),
    pinned: bool(o.pinned),
    createdAt: num(o.createdAt, now),
    updatedAt: num(o.updatedAt, now),
  };
}

export function normAsset(o: Loose<SyncAsset>): SyncAsset {
  return {
    id: str(o.id),
    mimeType: str(o.mimeType, "application/octet-stream"),
    sizeBytes: num(o.sizeBytes),
    driveFileId: strOrNull(o.driveFileId),
    fileName: str(o.fileName),
    description: str(o.description),
    createdAt: num(o.createdAt),
    updatedAt: num(o.updatedAt),
    path: str(o.path),
  };
}

export function normTile(o: Loose<LearningTile>, now = Date.now()): LearningTile {
  return {
    id: str(o.id),
    status: oneOf(o.status, TILE_STATUSES, "BACKLOG"),
    lastActivityAt: num(o.lastActivityAt, now),
    learnedAt: numOrNull(o.learnedAt),
    reviewStep: num(o.reviewStep),
    nextReviewAt: numOrNull(o.nextReviewAt),
    lastReviewScore: numOrNull(o.lastReviewScore),
    lastReviewedAt: numOrNull(o.lastReviewedAt),
    createdAt: num(o.createdAt, now),
    updatedAt: num(o.updatedAt, now),
  };
}

export function normLink(o: Loose<LearningLink>, now = Date.now()): LearningLink {
  return { fromNodeId: str(o.fromNodeId), toNodeId: str(o.toNodeId), createdAt: num(o.createdAt, now) };
}

export function normTag(o: Loose<BrainTag>, now = Date.now()): BrainTag {
  return { id: str(o.id), name: str(o.name), createdAt: num(o.createdAt, now), section: oneOf(o.section, SECTIONS, "MEMORY") };
}

export function parsePriority(v: unknown): MotionPriority {
  const s = typeof v === "string" ? v.toUpperCase() : "";
  if (s === "P1") return "HIGH";
  if (s === "P2") return "MEDIUM";
  if (s === "P3") return "LOW";
  if (s === "P4") return "NONE";
  return oneOf(s, PRIORITIES, "NONE");
}

export function normTask(o: Loose<MotionTask>, now = Date.now()): MotionTask {
  const priority = parsePriority(o.priority);
  const dueAt = numOrNull(o.dueAt);
  return {
    id: str(o.id),
    nodeId: strOrNull(o.nodeId),
    title: str(o.title),
    notes: str(o.notes),
    priority,
    dueAt,
    leadMinutes: num(o.leadMinutes),
    recurrence: oneOf(o.recurrence, RECURRENCES, "NONE"),
    status: oneOf(o.status, TASK_STATUSES, "OPEN"),
    completedAt: numOrNull(o.completedAt),
    sortOrder: num(o.sortOrder),
    linkedTileId: strOrNull(o.linkedTileId),
    createdAt: num(o.createdAt, now),
    updatedAt: num(o.updatedAt, now),
    points: num(o.points, basePoints(priority)),
    pointsEdited: bool(o.pointsEdited),
    firstDueAt: o.firstDueAt === undefined ? dueAt : numOrNull(o.firstDueAt),
    target: numOrNull(o.target),
    progress: num(o.progress),
    unit: strOrNull(o.unit),
    earnedPoints: numOrNull(o.earnedPoints),
    spawnedTaskId: strOrNull(o.spawnedTaskId),
  };
}

export function normEvent(o: Loose<MotionScoreEvent>, now = Date.now()): MotionScoreEvent {
  return {
    id: str(o.id),
    dateKey: str(o.dateKey),
    sourceType: oneOf(o.sourceType, ["TASK", "SUPERMARKET"] as const, "TASK"),
    sourceId: str(o.sourceId),
    title: str(o.title),
    points: num(o.points),
    createdAt: num(o.createdAt, now),
  };
}

export function normItem(o: Loose<VectorItem>): VectorItem {
  const priority = typeof o.priority === "string" ? o.priority : null;
  return {
    id: "",
    title: str(o.title),
    isChecked: bool(o.isChecked),
    quantity: numOrNull(o.quantity),
    remaining: numOrNull(o.remaining),
    unit: strOrNull(o.unit),
    price: numOrNull(o.price),
    priority,
    category: strOrNull(o.category),
    order: numOrNull(o.order),
    dueDate: numOrNull(o.dueDate),
  };
}

export function normSettings(o: Loose<SyncSettings> | null | undefined): SyncSettings {
  const target = num(o?.motionDailyTarget, DEFAULT_DAILY_TARGET);
  return {
    motionDailyTarget: Math.min(1000, Math.max(5, Math.round(target))),
    supermarketDay: oneOf(o?.supermarketDay, DAYS, DEFAULT_SUPERMARKET_DAY),
  };
}

export function emptyState(): BrainState {
  return {
    formatVersion: BRAIN_STATE_FORMAT_VERSION,
    rev: 0,
    writtenBy: null,
    writtenAt: 0,
    settings: normSettings(null),
    nodes: [],
    notes: [],
    assets: [],
    learningTiles: [],
    learningLinks: [],
    tags: [],
    tasks: [],
    scoreEvents: [],
    supermarket: { name: "Supermarket", sections: [], items: [] },
  };
}

/** Parses any state.json-shaped value, dropping records without keys and filling defaults. */
export function normState(raw: unknown): BrainState {
  const o = (raw && typeof raw === "object" ? raw : {}) as Record<string, unknown>;
  const arr = (v: unknown): Record<string, unknown>[] =>
    Array.isArray(v) ? v.filter((x): x is Record<string, unknown> => !!x && typeof x === "object") : [];
  const market = (o.supermarket && typeof o.supermarket === "object" ? o.supermarket : {}) as Record<string, unknown>;
  const writtenBy = o.writtenBy && typeof o.writtenBy === "object" ? (o.writtenBy as Record<string, unknown>) : null;
  return {
    formatVersion: num(o.formatVersion, BRAIN_STATE_FORMAT_VERSION),
    rev: num(o.rev),
    writtenBy: writtenBy
      ? { deviceId: str(writtenBy.deviceId), name: str(writtenBy.name), platform: str(writtenBy.platform) }
      : null,
    writtenAt: num(o.writtenAt),
    settings: normSettings(o.settings as Loose<SyncSettings>),
    nodes: arr(o.nodes).map((x) => normNode(x)).filter((x) => x.id),
    notes: arr(o.notes).map((x) => normNote(x)).filter((x) => x.id),
    assets: arr(o.assets).map((x) => normAsset(x)).filter((x) => x.id),
    learningTiles: arr(o.learningTiles).map((x) => normTile(x)).filter((x) => x.id),
    learningLinks: arr(o.learningLinks).map((x) => normLink(x)).filter((x) => x.fromNodeId && x.toNodeId),
    tags: arr(o.tags).map((x) => normTag(x)).filter((x) => x.id),
    tasks: arr(o.tasks).map((x) => normTask(x)).filter((x) => x.id),
    scoreEvents: arr(o.scoreEvents).map((x) => normEvent(x)).filter((x) => x.id),
    supermarket: {
      name: str(market.name, "Supermarket"),
      sections: strings(market.sections),
      items: arr(market.items).map((x) => normItem(x)).filter((x) => x.title),
    },
  };
}
