import { useMemo, useState } from "react";
import { Brain, ChevronDown, Download, GitBranch, GraduationCap, Plus, Sparkles, X } from "lucide-react";
import { draftNotes, suggestLinks } from "@/ai/learning";
import { loadLearningContext } from "@/ai/learningContext";
import { SourceList } from "@/components/SourceList";
import { TileHeader } from "@/components/TileHeader";
import { formatDateTime } from "@/domain/dates";
import { effectiveStatus, GROUP_OF, intervalDays, STATUS_LABEL, type LearnGroup } from "@/domain/learn";
import { buildLearningText, exportFileName } from "@/domain/learnContext";
import type { BrainNode, LearningTileStatus } from "@/domain/models";
import * as ops from "@/domain/ops";
import { cn } from "@/lib/cn";
import { errorMessage } from "@/platform/native";
import { useBrain } from "@/store/brain";
import { useLearnSettings } from "@/store/learnSettings";
import { useNav } from "@/store/nav";
import { saveTextFile } from "@/ui/files";
import { LearnQuiz } from "./learn/LearnQuiz";
import { LinkSuggestionsDialog, type Suggestions } from "./learn/LinkSuggestions";
import { Badge, Button, Card, Dialog, Empty, Menu, SearchInput } from "@/ui/primitives";
import { NewTileDialog, TagFilter } from "./Memory";

const GROUPS: { id: LearnGroup; label: string; hint: string }[] = [
  { id: "BACKLOG", label: "Backlog", hint: "Priority and general" },
  { id: "IN_PROGRESS", label: "In progress", hint: "Active and on hold" },
  { id: "LEARNED", label: "Learned", hint: "Review and done" },
];

/** Statuses a person can pick; On hold and Review are set by the phone's schedule. */
const PICKABLE: LearningTileStatus[] = ["PRIORITY", "BACKLOG", "IN_PROGRESS", "LEARNED"];

const STATUS_TONE: Record<LearningTileStatus, "neutral" | "navy" | "electric" | "danger"> = {
  PRIORITY: "danger",
  BACKLOG: "neutral",
  IN_PROGRESS: "electric",
  ON_HOLD: "neutral",
  TO_REVIEW: "electric",
  LEARNED: "navy",
};

function StatusMenu({ id, status }: { id: string; status: LearningTileStatus }) {
  const commit = useBrain((s) => s.commit);
  const toast = useBrain((s) => s.toast);
  const rules = useLearnSettings();
  const pick = (s: LearningTileStatus) => {
    commit((st) => ops.setLearnStatus(st, id, s, rules));
    if (s === "LEARNED" && s !== status) {
      const days = intervalDays(rules, 0);
      toast(days !== null ? `Learned · first review in ${days} days` : "Learned");
    }
  };
  return (
    <Menu
      align="left"
      trigger={<button className="inline-flex"><Badge tone={STATUS_TONE[status]} className="cursor-pointer">{STATUS_LABEL[status]}<ChevronDown size={11} /></Badge></button>}
      items={PICKABLE.map((s) => ({ label: STATUS_LABEL[s], active: s === status, onSelect: () => pick(s) }))}
    />
  );
}

/** Review ladder: one dot per interval; filled up to the current step. */
function Ladder({ step, intervals }: { step: number; intervals: number[] }) {
  return (
    <div className="flex items-center gap-1" title={intervals.map((d) => `${d}d`).join(" → ")}>
      {intervals.map((d, i) => (
        <div key={i} className={cn("h-1.5 flex-1 rounded-full", i < step ? "bg-navy" : i === step ? "bg-electric/50" : "bg-offwhite")} title={`${d} days`} />
      ))}
    </div>
  );
}

function LinkPicker({ open, title, candidates, onPick, onClose }: { open: boolean; title: string; candidates: BrainNode[]; onPick: (id: string) => void; onClose: () => void }) {
  const [query, setQuery] = useState("");
  const q = query.trim().toLowerCase();
  return (
    <Dialog open={open} onClose={onClose} title={title} width="max-w-sm">
      <div className="mb-3"><SearchInput value={query} onChange={setQuery} placeholder="Find a tile" /></div>
      <div className="max-h-80 space-y-0.5 overflow-y-auto">
        {candidates.filter((c) => !q || c.name.toLowerCase().includes(q)).map((c) => (
          <button key={c.id} onClick={() => { onPick(c.id); onClose(); }} className="w-full rounded-lg px-3 py-2 text-left text-sm hover:bg-offwhite">{c.name}</button>
        ))}
        {candidates.length === 0 && <div className="py-4 text-center text-sm text-muted">No other tiles</div>}
      </div>
    </Dialog>
  );
}

function TileDetail({ node }: { node: BrainNode }) {
  const brain = useBrain((s) => s.brain);
  const commit = useBrain((s) => s.commit);
  const toast = useBrain((s) => s.toast);
  const go = useNav((s) => s.go);
  const rules = useLearnSettings();
  const [picking, setPicking] = useState<"prereq" | "dependent" | null>(null);
  const [quiz, setQuiz] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);
  const [suggestions, setSuggestions] = useState<Suggestions | null>(null);
  const [focusNote, setFocusNote] = useState<string | null>(null);
  const tile = brain.learningTiles.find((t) => t.id === node.id);
  const status = tile ? effectiveStatus(tile, rules) : "BACKLOG";
  const names = new Map(brain.nodes.map((n) => [n.id, n]));
  const prereqs = brain.learningLinks.filter((l) => l.fromNodeId === node.id).map((l) => names.get(l.toNodeId)).filter((n): n is BrainNode => !!n);
  const dependents = brain.learningLinks.filter((l) => l.toNodeId === node.id).map((l) => names.get(l.fromNodeId)).filter((n): n is BrainNode => !!n);
  const linked = new Set([...prereqs, ...dependents].map((n) => n.id));
  const candidates = brain.nodes.filter((n) => n.section === "MODEL" && n.id !== node.id && !linked.has(n.id)).sort((a, b) => a.name.localeCompare(b.name));
  const tasks = brain.tasks.filter((t) => t.linkedTileId === node.id && t.status === "OPEN");

  const link = (from: string, to: string) => {
    let ok = true;
    commit((s) => {
      const next = ops.addLearningLink(s, from, to);
      ok = next !== null;
      return next ?? s;
    });
    if (!ok) toast("That link would make a loop of prerequisites", "error");
  };

  const runAi = async (label: string, job: () => Promise<void>) => {
    if (busy) return;
    setBusy(label);
    try {
      await job();
    } catch (e) {
      toast(errorMessage(e), "error");
    } finally {
      setBusy(null);
    }
  };

  const suggest = () => runAi("Finding links…", async () => {
    const ctx = await loadLearningContext(node.id);
    const others = brain.nodes.filter((n) => n.section === "MODEL" && n.id !== node.id).map((n) => ({ id: n.id, name: n.name, tags: n.tags }));
    const out = await suggestLinks(ctx, others, linked);
    if (!out.links.length && !out.missing.length) toast("No new links suggested");
    else setSuggestions(out);
  });

  const draft = () => runAi("Drafting notes…", async () => {
    const out = await draftNotes(await loadLearningContext(node.id, true));
    let id = "";
    commit((s) => {
      const [next, noteId] = ops.createTextNote(s, `AI draft: ${out.title}`, out.markdown, node.id, [], "Generated by Gemini, verify");
      id = noteId;
      return next;
    });
    setFocusNote(id);
  });

  const exportTile = async (withPrereqs: boolean) => {
    try {
      const built = buildLearningText(useBrain.getState().brain, node.id, { includePrerequisites: withPrereqs });
      const name = exportFileName(built.title);
      if (await saveTextFile(name, `${built.text}\n`, ["txt"])) toast(`Saved ${name}`);
    } catch (e) {
      toast(errorMessage(e), "error");
    }
  };

  const review = (solid: boolean) => {
    const step = (tile?.reviewStep ?? 0) + 1;
    commit((s) => ops.completeReview(s, node.id, solid, null, rules));
    const days = intervalDays(rules, step);
    toast(!solid ? "Back to In Progress · the review schedule restarts" : days !== null ? `Next review in ${days} days` : "Mastered · no more reviews");
  };

  const chip = (n: BrainNode, onRemove: () => void) => (
    <span key={n.id} className="inline-flex items-center gap-1.5 rounded-full border border-hairline py-1 pr-1.5 pl-3 text-sm">
      <button onClick={() => go("model", n.id)} className="hover:text-electric">{n.name}</button>
      <button onClick={onRemove} className="rounded-full p-0.5 text-item hover:bg-offwhite hover:text-black"><X size={12} /></button>
    </span>
  );

  return (
    <div>
      <TileHeader node={node} onBack={() => go("model")} deleteMessage="Its sources, progress and prerequisite links are deleted; files go to the Drive trash.">
        <StatusMenu id={node.id} status={status} />
      </TileHeader>

      <div className="mb-5 flex flex-wrap items-center gap-2">
        <Button size="sm" variant={status === "TO_REVIEW" ? "accent" : "primary"} onClick={() => setQuiz(true)}>
          <GraduationCap size={14} /> {status === "TO_REVIEW" ? "Review quiz" : "Quiz me"}
        </Button>
        <Button size="sm" variant="secondary" disabled={!!busy} onClick={() => void suggest()}><Sparkles size={14} /> Suggest links</Button>
        <Button size="sm" variant="secondary" disabled={!!busy} onClick={() => void draft()}><Sparkles size={14} /> Draft notes</Button>
        <Menu
          align="left"
          trigger={<Button size="sm" variant="secondary"><Download size={14} /> Export</Button>}
          items={[
            { label: "This tile (.txt)", onSelect: () => void exportTile(false) },
            { label: "With prerequisites (.txt)", onSelect: () => void exportTile(true) },
          ]}
        />
        {busy && <span className="flex items-center gap-1.5 text-sm text-muted"><Sparkles size={13} className="animate-pulse text-electric" />{busy}</span>}
      </div>

      {status === "TO_REVIEW" && (
        <Card className="mb-5 flex items-center gap-4 border-electric/50 bg-electric/5 p-4">
          <GraduationCap size={20} className="shrink-0 text-electric" />
          <div className="min-w-0 flex-1 text-sm">
            <div className="font-medium">Time to review</div>
            <div className="text-muted">Take the quiz, or say how well you remember it.</div>
          </div>
          <Button size="sm" variant="secondary" onClick={() => review(false)}>Shaky</Button>
          <Button size="sm" onClick={() => review(true)}>Still solid</Button>
        </Card>
      )}

      <div className="mb-8 grid grid-cols-3 gap-4">
        <Card className="p-4">
          <div className="mb-2 flex items-center justify-between text-xs font-semibold tracking-wide text-muted uppercase">
            Depends on
            <button onClick={() => setPicking("prereq")} className="text-electric"><Plus size={14} /></button>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {prereqs.length ? prereqs.map((n) => chip(n, () => commit((s) => ops.removeLearningLink(s, node.id, n.id)))) : <span className="text-sm text-item">None</span>}
          </div>
        </Card>
        <Card className="p-4">
          <div className="mb-2 flex items-center justify-between text-xs font-semibold tracking-wide text-muted uppercase">
            Unlocks
            <button onClick={() => setPicking("dependent")} className="text-electric"><Plus size={14} /></button>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {dependents.length ? dependents.map((n) => chip(n, () => commit((s) => ops.removeLearningLink(s, n.id, node.id)))) : <span className="text-sm text-item">None</span>}
          </div>
        </Card>
        <Card className="p-4 text-sm">
          <div className="mb-2 text-xs font-semibold tracking-wide text-muted uppercase">Progress</div>
          {tile ? (
            <div className="space-y-1 text-muted">
              <div>Last studied {formatDateTime(tile.lastActivityAt)}</div>
              {(tile.status === "LEARNED" || status === "TO_REVIEW") && (
                <div className="py-1">
                  <Ladder step={tile.reviewStep} intervals={rules.reviewIntervals} />
                  <div className="mt-1 text-xs text-item">
                    {tile.nextReviewAt ? `Review ${Math.min(tile.reviewStep + 1, rules.reviewIntervals.length)} of ${rules.reviewIntervals.length} · ${formatDateTime(tile.nextReviewAt)}` : "Mastered"}
                  </div>
                </div>
              )}
              {tile.lastReviewScore !== null && <div>Last score {Math.round(tile.lastReviewScore * 100)}%</div>}
              {tasks.length > 0 && <div className="text-electric">{tasks.length} open Motion item(s)</div>}
            </div>
          ) : <span className="text-item">Not started</span>}
        </Card>
      </div>

      <SourceList nodeId={node.id} section="MODEL" focusNoteId={focusNote} />
      <LearnQuiz nodeId={node.id} open={quiz} onClose={() => setQuiz(false)} />
      <LinkSuggestionsDialog nodeId={node.id} suggestions={suggestions} onClose={() => setSuggestions(null)} />

      <LinkPicker
        open={picking !== null}
        title={picking === "prereq" ? `${node.name} depends on…` : `${node.name} unlocks…`}
        candidates={candidates}
        onPick={(id) => (picking === "prereq" ? link(node.id, id) : link(id, node.id))}
        onClose={() => setPicking(null)}
      />
    </div>
  );
}

export function ModelScreen() {
  const brain = useBrain((s) => s.brain);
  const { selected, go } = useNav();
  const rules = useLearnSettings();
  const [query, setQuery] = useState("");
  const [tag, setTag] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const counts = useMemo(() => {
    const m = new Map<string, number>();
    for (const n of brain.notes) if (n.nodeId) m.set(n.nodeId, (m.get(n.nodeId) ?? 0) + 1);
    return m;
  }, [brain.notes]);

  const node = selected ? brain.nodes.find((n) => n.id === selected && n.section === "MODEL") : undefined;
  if (node) return <TileDetail node={node} />;

  const tiles = new Map(brain.learningTiles.map((t) => [t.id, t]));
  const q = query.trim().toLowerCase();
  const nodes = brain.nodes
    .filter((n) => n.section === "MODEL")
    .filter((n) => !tag || n.tags.includes(tag))
    .filter((n) => !q || n.name.toLowerCase().includes(q) || n.tags.some((t) => t.toLowerCase().includes(q)));
  const statusOf = (id: string): LearningTileStatus => {
    const t = tiles.get(id);
    return t ? effectiveStatus(t, rules) : "BACKLOG";
  };
  const rank: Record<LearningTileStatus, number> = { PRIORITY: 0, BACKLOG: 1, TO_REVIEW: 0, LEARNED: 1, IN_PROGRESS: 0, ON_HOLD: 1 };

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Model</h1>
          <div className="text-sm text-muted">What you're learning, from backlog to mastered.</div>
        </div>
        <div className="flex items-center gap-2">
          <div className="w-64"><SearchInput value={query} onChange={setQuery} placeholder="Search tiles" /></div>
          <Button onClick={() => setCreating(true)}><Plus size={16} /> New tile</Button>
        </div>
      </div>
      <TagFilter section="MODEL" value={tag} onChange={setTag} />
      {nodes.length === 0 && !q && !tag ? (
        <Empty icon={<Brain size={28} />} title="No Learn tiles yet" hint="Add a topic, drop in sources, and track it from backlog to learned." action={<Button onClick={() => setCreating(true)}><Plus size={16} /> New tile</Button>} />
      ) : (
        <div className="grid grid-cols-3 gap-4">
          {GROUPS.map((g) => {
            const items = nodes
              .filter((n) => GROUP_OF[statusOf(n.id)] === g.id)
              .sort((a, b) => rank[statusOf(a.id)] - rank[statusOf(b.id)] || (tiles.get(b.id)?.updatedAt ?? 0) - (tiles.get(a.id)?.updatedAt ?? 0));
            return (
              <div key={g.id} className="rounded-2xl bg-offwhite p-3">
                <div className="mb-3 flex items-baseline justify-between px-1">
                  <div className="font-semibold">{g.label} <span className="text-sm font-normal text-item">{items.length}</span></div>
                  <div className="text-[11px] text-item">{g.hint}</div>
                </div>
                <div className="space-y-2">
                  {items.map((n) => {
                    const status = statusOf(n.id);
                    const prereqs = brain.learningLinks.filter((l) => l.fromNodeId === n.id).length;
                    return (
                      <Card key={n.id} onClick={() => go("model", n.id)} className={cn("p-3", status === "TO_REVIEW" && "border-electric/60")}>
                        <div className="flex items-start justify-between gap-2">
                          <div className="line-clamp-2 text-sm font-medium">{n.name}</div>
                          <StatusMenu id={n.id} status={status} />
                        </div>
                        <div className="mt-2 flex items-center gap-3 text-xs text-muted">
                          <span>{counts.get(n.id) ?? 0} sources</span>
                          {prereqs > 0 && <span className="flex items-center gap-1"><GitBranch size={11} />{prereqs}</span>}
                          {n.tags.slice(0, 2).map((t) => <Badge key={t}>{t}</Badge>)}
                        </div>
                      </Card>
                    );
                  })}
                  {items.length === 0 && <div className="px-1 py-6 text-center text-xs text-item">Empty</div>}
                </div>
              </div>
            );
          })}
        </div>
      )}
      <NewTileDialog open={creating} section="MODEL" onClose={() => setCreating(false)} onCreated={(id) => go("model", id)} />
    </div>
  );
}
