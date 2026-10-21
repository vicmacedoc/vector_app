import { useState } from "react";
import { Plus, X } from "lucide-react";
import type { BrainSection } from "@/domain/models";
import * as ops from "@/domain/ops";
import { useBrain } from "@/store/brain";

/** Chips for [value] plus an input that suggests and creates tags in [section]'s library. */
export function TagEditor({ section, value, onChange }: { section: BrainSection; value: string[]; onChange: (tags: string[]) => void }) {
  const tags = useBrain((s) => s.brain.tags);
  const commit = useBrain((s) => s.commit);
  const [draft, setDraft] = useState("");
  const library = tags.filter((t) => t.section === section).map((t) => t.name).sort((a, b) => a.localeCompare(b));
  const suggestions = library.filter((t) => !value.includes(t) && t.toLowerCase().includes(draft.trim().toLowerCase())).slice(0, 6);

  const add = (name: string) => {
    let resolved: string | null = null;
    commit((s) => {
      const [next, tag] = ops.ensureTag(s, name, section);
      resolved = tag;
      return next;
    });
    if (resolved && !value.includes(resolved)) onChange([...value, resolved]);
    setDraft("");
  };

  return (
    <div>
      <div className="flex flex-wrap items-center gap-1.5">
        {value.map((t) => (
          <span key={t} className="inline-flex items-center gap-1 rounded-full bg-navy px-2.5 py-1 text-xs text-white">
            {t}
            <button onClick={() => onChange(value.filter((x) => x !== t))} aria-label={`Remove ${t}`}>
              <X size={12} />
            </button>
          </span>
        ))}
        <div className="relative">
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && draft.trim()) {
                e.preventDefault();
                add(draft);
              }
            }}
            placeholder="Add tag"
            className="h-7 w-32 rounded-full border border-dashed border-hairline px-3 text-xs outline-none focus:border-electric"
          />
        </div>
      </div>
      {draft.trim() && (
        <div className="mt-2 flex flex-wrap gap-1.5">
          {suggestions.map((t) => (
            <button key={t} onClick={() => add(t)} className="rounded-full border border-hairline px-2.5 py-1 text-xs hover:border-electric hover:text-electric">
              {t}
            </button>
          ))}
          {!library.some((t) => t.toLowerCase() === draft.trim().toLowerCase()) && (
            <button onClick={() => add(draft)} className="inline-flex items-center gap-1 rounded-full bg-electric/10 px-2.5 py-1 text-xs text-electric">
              <Plus size={12} /> Create “{draft.trim()}”
            </button>
          )}
        </div>
      )}
    </div>
  );
}
