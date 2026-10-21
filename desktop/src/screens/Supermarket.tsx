import { useEffect, useMemo, useState } from "react";
import { ArrowDown, ArrowUp, GripVertical, Layers, Pencil, Plus, Save, ShoppingCart, Trash2 } from "lucide-react";
import { daysBetween, formatDay, todayKey } from "@/domain/dates";
import type { SyncSupermarket, VectorItem } from "@/domain/models";
import * as ops from "@/domain/ops";
import { needsBuy, sortedIndices, urgency } from "@/domain/supermarketMath";
import { cn } from "@/lib/cn";
import { useBrain } from "@/store/brain";
import { Button, Card, Dialog, Empty, Input, Label, Segmented, Slider } from "@/ui/primitives";

type Filter = "ALL" | "TO_BUY" | "STOCKED";
type Layout = "ORDER" | "AISLES";

const URGENCY_DOT = { URGENT: "bg-prio-high", LOW: "bg-prio-mid", STOCKED: "bg-prio-low" };

const fmt = (n: number | null) => (n === null ? "—" : Number.isInteger(n) ? String(n) : n.toFixed(2).replace(/0+$/, "").replace(/\.$/, ""));

/** Filtered items keep their slots in the full order; then every item gets a global `order`. */
function reorder(list: SyncSupermarket, orderedVisible: number[]): SyncSupermarket {
  const full = sortedIndices(list);
  const visible = new Set(orderedVisible);
  let next = 0;
  const merged = full.map((idx) => (visible.has(idx) ? orderedVisible[next++] : idx));
  const position = new Map(merged.map((idx, pos) => [idx, pos]));
  return { ...list, items: list.items.map((it, i) => ({ ...it, order: position.get(i) ?? null })) };
}

function normalizeStock(total: number | null, remaining: number | null): [number, number] {
  const qty = total !== null && total > 0 ? total : 1;
  return [qty, Math.min(qty, Math.max(0, remaining ?? qty))];
}

function ItemDialog({ open, item, sections, onSave, onClose }: {
  open: boolean;
  item: VectorItem | null;
  sections: string[];
  onSave: (v: { title: string; aisle: string | null; total: number | null; remaining: number | null; unit: string | null }) => void;
  onClose: () => void;
}) {
  const [title, setTitle] = useState("");
  const [aisle, setAisle] = useState("");
  const [total, setTotal] = useState("");
  const [remaining, setRemaining] = useState("");
  const [unit, setUnit] = useState("");
  useEffect(() => {
    if (!open) return;
    setTitle(item?.title ?? "");
    setAisle(item?.category ?? "");
    setTotal(item?.quantity != null ? String(item.quantity) : "");
    setRemaining(item?.remaining != null ? String(item.remaining) : "");
    setUnit(item?.unit ?? "");
  }, [open, item]);
  const num = (s: string) => (s.trim() && Number.isFinite(Number(s)) ? Number(s) : null);
  const save = () => {
    if (!title.trim()) return;
    onSave({ title: title.trim(), aisle: aisle.trim() || null, total: num(total), remaining: num(remaining), unit: unit.trim() || null });
    onClose();
  };
  return (
    <Dialog open={open} onClose={onClose} title={item ? "Edit item" : "New item"} width="max-w-md"
      footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button onClick={save} disabled={!title.trim()}>Save</Button></>}>
      <div className="space-y-4" onKeyDown={(e) => e.key === "Enter" && save()}>
        <div><Label>Item</Label><Input autoFocus value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Oat milk" /></div>
        <div>
          <Label>Aisle</Label>
          <Input list="aisles" value={aisle} onChange={(e) => setAisle(e.target.value)} placeholder="e.g. Dairy" />
          <datalist id="aisles">{sections.map((s) => <option key={s} value={s} />)}</datalist>
        </div>
        <div className="grid grid-cols-3 gap-3">
          <div><Label>Total</Label><Input type="number" min={0} step="any" value={total} onChange={(e) => setTotal(e.target.value)} placeholder="1" /></div>
          <div><Label>Remaining</Label><Input type="number" min={0} step="any" value={remaining} onChange={(e) => setRemaining(e.target.value)} placeholder="= total" /></div>
          <div><Label>Unit</Label><Input value={unit} onChange={(e) => setUnit(e.target.value)} placeholder="L" /></div>
        </div>
      </div>
    </Dialog>
  );
}

function AislesDialog({ open, list, onChange, onClose }: { open: boolean; list: SyncSupermarket; onChange: (l: SyncSupermarket) => void; onClose: () => void }) {
  const [draft, setDraft] = useState("");
  const [editing, setEditing] = useState<{ index: number; name: string } | null>(null);
  const sections = list.sections;
  const move = (i: number, d: number) => {
    const next = [...sections];
    [next[i], next[i + d]] = [next[i + d], next[i]];
    onChange({ ...list, sections: next });
  };
  const rename = () => {
    if (!editing) return;
    const name = editing.name.trim();
    const old = sections[editing.index];
    if (name && name !== old && !sections.includes(name)) {
      onChange({
        ...list,
        sections: sections.map((s, i) => (i === editing.index ? name : s)),
        items: list.items.map((it) => (it.category === old ? { ...it, category: name } : it)),
      });
    }
    setEditing(null);
  };
  const add = () => {
    const name = draft.trim();
    if (name && !sections.includes(name)) onChange({ ...list, sections: [...sections, name] });
    setDraft("");
  };
  return (
    <Dialog open={open} onClose={onClose} title="Aisles · walking order" width="max-w-md">
      <div className="space-y-1">
        {sections.map((s, i) => (
          <div key={s} className="flex items-center gap-2 rounded-lg px-2 py-1.5 hover:bg-offwhite">
            <span className="w-5 text-xs text-item tabular-nums">{i + 1}</span>
            {editing?.index === i ? (
              <input autoFocus value={editing.name} onChange={(e) => setEditing({ index: i, name: e.target.value })} onBlur={rename} onKeyDown={(e) => e.key === "Enter" && rename()} className="flex-1 border-b border-electric bg-transparent text-sm outline-none" />
            ) : (
              <span className="flex-1 text-sm" onDoubleClick={() => setEditing({ index: i, name: s })}>{s}</span>
            )}
            <span className="text-xs text-item">{list.items.filter((it) => it.category === s).length}</span>
            <button disabled={i === 0} onClick={() => move(i, -1)} className="rounded p-1 text-muted hover:bg-white disabled:opacity-30"><ArrowUp size={14} /></button>
            <button disabled={i === sections.length - 1} onClick={() => move(i, 1)} className="rounded p-1 text-muted hover:bg-white disabled:opacity-30"><ArrowDown size={14} /></button>
            <button onClick={() => setEditing({ index: i, name: s })} className="rounded p-1 text-muted hover:bg-white"><Pencil size={14} /></button>
            <button onClick={() => onChange({ ...list, sections: sections.filter((x) => x !== s) })} className="rounded p-1 text-danger hover:bg-white"><Trash2 size={14} /></button>
          </div>
        ))}
      </div>
      <div className="mt-3 flex gap-2">
        <Input value={draft} onChange={(e) => setDraft(e.target.value)} onKeyDown={(e) => e.key === "Enter" && add()} placeholder="New aisle" />
        <Button variant="secondary" onClick={add}><Plus size={15} /></Button>
      </div>
    </Dialog>
  );
}

export function SupermarketScreen() {
  const list = useBrain((s) => s.brain.supermarket);
  const brain = useBrain((s) => s.brain);
  const commit = useBrain((s) => s.commit);
  const saveSupermarket = useBrain((s) => s.saveSupermarket);
  const confirmTrip = useBrain((s) => s.confirmTrip);
  const [filter, setFilter] = useState<Filter>("ALL");
  const [layout, setLayout] = useState<Layout>("ORDER");
  const [editing, setEditing] = useState<{ index: number | null } | null>(null);
  const [aisles, setAisles] = useState(false);
  const [tripPoints, setTripPoints] = useState<number | null>(null);
  const [drag, setDrag] = useState<{ from: number; over: number } | null>(null);

  const setList = (next: SyncSupermarket) => commit((s) => ops.setSupermarket(s, next));
  const updateItem = (index: number, fn: (it: VectorItem) => VectorItem) => setList({ ...list, items: list.items.map((it, i) => (i === index ? fn(it) : it)) });

  const ordered = useMemo(() => sortedIndices(list), [list]);
  const visible = ordered.filter((i) => filter === "ALL" || (filter === "TO_BUY") === needsBuy(list.items[i]));
  const toBuy = list.items.filter(needsBuy).length;
  const cycle = ops.currentSupermarketCycle(brain);
  const late = daysBetween(cycle.dueDate, todayKey());

  useEffect(() => {
    if (!drag) return;
    const up = () => {
      if (drag.from !== drag.over) {
        const next = [...visible];
        const [moved] = next.splice(drag.from, 1);
        next.splice(drag.over, 0, moved);
        setList(reorder(list, next));
      }
      setDrag(null);
    };
    window.addEventListener("pointerup", up);
    return () => window.removeEventListener("pointerup", up);
  });

  const save = async () => {
    const points = await saveSupermarket();
    if (points !== null) setTripPoints(points);
  };

  const groups = useMemo(() => {
    if (layout !== "AISLES") return [];
    const sections = list.sections.length ? list.sections : [...new Set(list.items.map((i) => i.category).filter((c): c is string => !!c))];
    const byCat = new Map<string, number[]>();
    for (const i of visible) {
      const cat = list.items[i].category ?? "Other";
      byCat.set(cat, [...(byCat.get(cat) ?? []), i]);
    }
    const out: [string, number[]][] = [];
    for (const s of sections) if (byCat.has(s)) out.push([s, byCat.get(s)!]);
    for (const [cat, idx] of byCat) if (!sections.includes(cat)) out.push([cat, idx]);
    return out;
  }, [layout, list, visible]);

  const row = (index: number, pos: number | null) => {
    const item = list.items[index];
    const qty = item.quantity ?? 1;
    const remaining = item.remaining ?? (item.isChecked ? qty : 0);
    const u = urgency(item);
    return (
      <div
        key={`${item.title}-${index}`}
        onPointerEnter={() => drag && pos !== null && setDrag({ ...drag, over: pos })}
        className={cn(
          "group flex items-center gap-3 px-4 py-2.5",
          drag && pos !== null && drag.over === pos && drag.from !== pos && "bg-electric/10",
          drag?.from === pos && pos !== null && "opacity-50",
        )}
      >
        {pos !== null ? (
          <button onPointerDown={(e) => { e.preventDefault(); setDrag({ from: pos, over: pos }); }} className="cursor-grab text-item hover:text-black active:cursor-grabbing"><GripVertical size={16} /></button>
        ) : <span className="w-4" />}
        <span className={cn("h-2.5 w-2.5 shrink-0 rounded-full", URGENCY_DOT[u])} title={u.toLowerCase()} />
        <button onClick={() => setEditing({ index })} className="w-36 shrink-0 text-left lg:w-56">
          <div className="truncate text-sm font-medium">{item.title}</div>
          <div className="truncate text-xs text-item">{item.category ?? "No aisle"}</div>
        </button>
        <div className="min-w-24 flex-1 px-2"><Slider value={remaining} max={qty} step={qty <= 10 ? 0.5 : 1} onChange={(v) => updateItem(index, (it) => ({ ...it, remaining: v }))} /></div>
        <div className="w-20 shrink-0 text-right text-sm tabular-nums">
          <span className="font-semibold">{fmt(remaining)}</span><span className="text-item"> / {fmt(qty)}{item.unit ? ` ${item.unit}` : ""}</span>
        </div>
        <button onClick={() => setList({ ...list, items: list.items.filter((_, i) => i !== index) })} className="rounded p-1 text-item opacity-0 group-hover:opacity-100 hover:text-danger"><Trash2 size={15} /></button>
      </div>
    );
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">{list.name}</h1>
          <div className="text-sm text-muted">
            {list.items.length} items · {toBuy} to buy · next trip {formatDay(cycle.dueDate)}
            {cycle.open ? (late > 0 ? ` · ${late}d late` : "") : " · this week's trip is logged"}
          </div>
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" onClick={() => setAisles(true)}><Layers size={15} /> Aisles</Button>
          <Button variant="secondary" onClick={() => setEditing({ index: null })}><Plus size={15} /> Item</Button>
          <Button onClick={() => void save()}><Save size={15} /> Save</Button>
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-2">
        <Segmented value={filter} onChange={setFilter} options={[
          { value: "ALL", label: `All ${list.items.length}` },
          { value: "TO_BUY", label: `To buy ${toBuy}` },
          { value: "STOCKED", label: `Stocked ${list.items.length - toBuy}` },
        ]} />
        <Segmented value={layout} onChange={setLayout} options={[{ value: "ORDER", label: "My order" }, { value: "AISLES", label: "By aisle" }]} />
      </div>

      {list.items.length === 0 ? (
        <Empty icon={<ShoppingCart size={28} />} title="No items yet" hint="Add what you keep at home; slide the remaining amount down as it runs out." action={<Button onClick={() => setEditing({ index: null })}><Plus size={15} /> Item</Button>} />
      ) : layout === "ORDER" ? (
        <Card className={cn("divide-y divide-hairline", drag && "select-none")}>
          {visible.map((index, pos) => row(index, pos))}
        </Card>
      ) : (
        <div className="space-y-4">
          {groups.map(([aisle, indices]) => (
            <div key={aisle}>
              <div className="mb-1.5 px-1 text-xs font-semibold tracking-wide text-muted uppercase">{aisle}</div>
              <Card className="divide-y divide-hairline">
                {indices.map((index) => row(index, null))}
              </Card>
            </div>
          ))}
        </div>
      )}

      <ItemDialog
        open={editing !== null}
        item={editing?.index != null ? list.items[editing.index] : null}
        sections={list.sections}
        onClose={() => setEditing(null)}
        onSave={(v) => {
          const [quantity, remaining] = normalizeStock(v.total, v.remaining);
          const sections = v.aisle && !list.sections.includes(v.aisle) ? [...list.sections, v.aisle] : list.sections;
          if (editing?.index != null) {
            setList({ ...list, sections, items: list.items.map((it, i) => (i === editing.index ? { ...it, title: v.title, category: v.aisle, quantity, remaining, unit: v.unit } : it)) });
          } else {
            const order = Math.max(-1, ...list.items.map((i) => i.order ?? -1)) + 1;
            const item: VectorItem = { id: "", title: v.title, isChecked: false, quantity, remaining, unit: v.unit, price: null, priority: null, category: v.aisle, order, dueDate: null };
            setList({ ...list, sections, items: [...list.items, item] });
          }
        }}
      />
      <AislesDialog open={aisles} list={list} onChange={setList} onClose={() => setAisles(false)} />
      <Dialog open={tripPoints !== null} onClose={() => { setTripPoints(null); confirmTrip(false); }} title="Did you go to the supermarket?" width="max-w-sm"
        footer={<>
          <Button variant="secondary" onClick={() => { setTripPoints(null); confirmTrip(false); }}>No, just restocking</Button>
          <Button onClick={() => { setTripPoints(null); confirmTrip(true); }}>Yes · +{tripPoints}</Button>
        </>}>
        <div className="text-sm text-muted">Stock went up since the last save. Logging the trip earns {tripPoints} Motion points for this week's cycle.</div>
      </Dialog>
    </div>
  );
}
