// Port of core/.../SupermarketMath.kt. Items are addressed by array index (ids are stripped in sync).
import type { SyncSupermarket, VectorItem } from "./models";

export type Urgency = "URGENT" | "LOW" | "STOCKED";

export function urgency(item: VectorItem): Urgency {
  const qty = item.quantity;
  const remaining = item.remaining;
  if (qty === null || qty <= 0 || remaining === null) return item.isChecked ? "STOCKED" : "URGENT";
  const ratio = remaining / qty;
  if (ratio <= 0.25) return "URGENT";
  if (ratio <= 0.5) return "LOW";
  return "STOCKED";
}

export const needsBuy = (item: VectorItem) => urgency(item) !== "STOCKED";

const MAX = Number.MAX_SAFE_INTEGER;

export function routeOrder(list: SyncSupermarket): number[] {
  const sectionIndex = new Map(list.sections.map((s, i) => [s, i]));
  return list.items
    .map((item, i) => ({ item, i }))
    .sort((a, b) =>
      (sectionIndex.get(a.item.category ?? "") ?? MAX) - (sectionIndex.get(b.item.category ?? "") ?? MAX) ||
      (a.item.order ?? MAX) - (b.item.order ?? MAX) ||
      a.item.title.toLowerCase().localeCompare(b.item.title.toLowerCase()),
    )
    .map((x) => x.i);
}

/** Indices in user order; colliding legacy per-aisle orders fall back to the aisle route. */
export function sortedIndices(list: SyncSupermarket): number[] {
  const orders = list.items.map((i) => i.order).filter((o): o is number => o !== null);
  const route = routeOrder(list);
  if (orders.length !== new Set(orders).size) return route;
  const routePos = new Map(route.map((idx, pos) => [idx, pos]));
  return list.items
    .map((_, i) => i)
    .sort((a, b) =>
      (list.items[a].order ?? MAX) - (list.items[b].order ?? MAX) || (routePos.get(a) ?? MAX) - (routePos.get(b) ?? MAX),
    );
}
