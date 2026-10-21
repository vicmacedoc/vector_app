// BrainState <-> RecordSet. Port of BrainStateCodec.toRecords/fromRecords in BrainState.kt.
import {
  type BrainState,
  normAsset, normEvent, normItem, normLink, normNode, normNote, normSettings, normTag, normTask, normTile,
} from "@/domain/models";
import { C, type JsonObject, type RecordSet, type Records } from "./merge";

export const META_SETTINGS = "settings";
export const META_SUPERMARKET = "supermarket";

export const supermarketKey = (title: string) => title.trim().toLowerCase();
export const linkKey = (from: string, to: string) => `${from}|${to}`;

function keyed<T>(items: T[], key: (t: T) => string): Records {
  const out: Records = new Map();
  for (const item of items) out.set(key(item), item as unknown as JsonObject);
  return out;
}

/** Keys for Supermarket items: the normalized title, then `#2`, `#3` for duplicates (array order). */
export function supermarketKeys(titles: string[]): string[] {
  const seen = new Map<string, number>();
  return titles.map((t) => {
    const base = supermarketKey(t);
    const n = (seen.get(base) ?? 0) + 1;
    seen.set(base, n);
    return n === 1 ? base : `${base}#${n}`;
  });
}

export function toRecords(state: BrainState): RecordSet {
  const out: RecordSet = new Map();
  out.set(C.NODES, keyed(state.nodes, (x) => x.id));
  out.set(C.NOTES, keyed(state.notes, (x) => x.id));
  out.set(C.ASSETS, keyed(state.assets, (x) => x.id));
  out.set(C.LEARNING_TILES, keyed(state.learningTiles, (x) => x.id));
  out.set(C.LEARNING_LINKS, keyed(state.learningLinks, (x) => linkKey(x.fromNodeId, x.toNodeId)));
  out.set(C.TAGS, keyed(state.tags, (x) => x.id));
  out.set(C.TASKS, keyed(state.tasks, (x) => x.id));
  out.set(C.SCORE_EVENTS, keyed(state.scoreEvents, (x) => x.id));
  const items: Records = new Map();
  const keys = supermarketKeys(state.supermarket.items.map((i) => i.title));
  state.supermarket.items.forEach((item, i) => items.set(keys[i], { ...item, id: "" } as unknown as JsonObject));
  out.set(C.SUPERMARKET_ITEMS, items);
  out.set(
    C.META,
    new Map<string, JsonObject>([
      [META_SETTINGS, { ...state.settings } as unknown as JsonObject],
      [META_SUPERMARKET, { name: state.supermarket.name, sections: [...state.supermarket.sections] }],
    ]),
  );
  return out;
}

export function fromRecords(set: RecordSet, header: BrainState): BrainState {
  const vals = (c: string) => [...(set.get(c) ?? new Map()).values()] as Record<string, unknown>[];
  const meta = set.get(C.META) ?? new Map();
  const market = meta.get(META_SUPERMARKET) as Record<string, unknown> | undefined;
  return {
    ...header,
    settings: meta.has(META_SETTINGS) ? normSettings(meta.get(META_SETTINGS) as never) : header.settings,
    nodes: vals(C.NODES).map((x) => normNode(x)),
    notes: vals(C.NOTES).map((x) => normNote(x)),
    assets: vals(C.ASSETS).map((x) => normAsset(x)),
    learningTiles: vals(C.LEARNING_TILES).map((x) => normTile(x)),
    learningLinks: vals(C.LEARNING_LINKS).map((x) => normLink(x)),
    tags: vals(C.TAGS).map((x) => normTag(x)),
    tasks: vals(C.TASKS).map((x) => normTask(x)),
    scoreEvents: vals(C.SCORE_EVENTS).map((x) => normEvent(x)),
    supermarket: {
      name: typeof market?.name === "string" ? market.name : header.supermarket.name,
      sections: Array.isArray(market?.sections)
        ? (market!.sections as unknown[]).filter((s): s is string => typeof s === "string")
        : header.supermarket.sections,
      items: vals(C.SUPERMARKET_ITEMS).map((x) => normItem(x)),
    },
  };
}
