// Learn tile helpers (GeminiLearningClient): link suggestions, note drafts, quizzes and open-answer grading.
import { prompt } from "./prompts";
import { S, generateJson, type Part, type Schema } from "./gemini";

const MAX_CONTEXT_CHARS = 200_000;
const MAX_GRADE_CONTEXT_CHARS = 40_000;
const MAX_ATTACHMENT_BYTES = 15 * 1024 * 1024;
const MAX_MISSING = 5;

export type LinkDirection = "DEPENDS_ON" | "USED_IN";

/** Plain-text view of a tile (see LearningContextBuilder) plus PDFs small enough to attach. */
export interface LearningContext {
  nodeId: string;
  title: string;
  text: string;
  pdfs: { name: string; base64: string; size: number }[];
}

export interface LinkCandidate {
  id: string;
  name: string;
  tags: string[];
}

export interface SuggestedLink {
  tileId: string;
  /** DEPENDS_ON: the current tile depends on tileId; USED_IN: tileId depends on the current tile. */
  direction: LinkDirection;
  reason: string;
}

export interface MissingPrerequisite {
  name: string;
  reason: string;
}

export interface QuizQuestion {
  type: "MULTIPLE_CHOICE" | "OPEN";
  prompt: string;
  options: string[];
  correctIndex: number;
  explanation: string;
  referenceAnswer: string;
}

export interface OpenGrade {
  score: number;
  verdict: "correct" | "partial" | "incorrect";
  feedback: string;
}

/** Every property required unless [required] narrows it, matching the phone's `obj()` helper. */
const obj = (properties: Record<string, Schema>, required?: string[]) => S.obj(properties, required ?? Object.keys(properties));

function pdfParts(pdfs: LearningContext["pdfs"]): Part[] {
  let budget = MAX_ATTACHMENT_BYTES;
  const out: Part[] = [];
  for (const p of pdfs) {
    if (p.size <= 0 || p.size > budget) continue;
    budget -= p.size;
    out.push({ inlineData: { mimeType: "application/pdf", data: p.base64 } });
  }
  return out;
}

export async function suggestLinks(
  ctx: LearningContext,
  candidates: LinkCandidate[],
  existingLinkIds: Set<string>,
): Promise<{ links: SuggestedLink[]; missing: MissingPrerequisite[] }> {
  const list = candidates.map((c) => `${c.id} | ${c.name}${c.tags.length ? ` | tags: ${c.tags.join(", ")}` : ""}`).join("\n") || "(none)";
  const text = `${prompt("learning", "links")}\n\nCURRENT TILE\n${ctx.text.slice(0, MAX_CONTEXT_CHARS)}\n\nOTHER LEARN TILES (id | name | tags)\n${list}`;
  const out = await generateJson<{ links?: Partial<SuggestedLink>[]; missing?: Partial<MissingPrerequisite>[] }>({
    parts: [{ text }],
    schema: obj({
      links: S.arr(obj({ tileId: S.str(), direction: S.enumStr(["DEPENDS_ON", "USED_IN"]), reason: S.str() })),
      missing: S.arr(obj({ name: S.str(), reason: S.str() })),
    }),
    temperature: 0.3,
  });
  const valid = new Set(candidates.map((c) => c.id));
  const known = new Set([...candidates.map((c) => c.name.trim().toLowerCase()), ctx.title.trim().toLowerCase()]);
  const seen = new Set<string>();
  const links = (out.links ?? [])
    .filter((l) => typeof l.tileId === "string" && valid.has(l.tileId) && !existingLinkIds.has(l.tileId))
    .filter((l) => !seen.has(l.tileId!) && seen.add(l.tileId!))
    .map<SuggestedLink>((l) => ({ tileId: l.tileId!, direction: l.direction === "USED_IN" ? "USED_IN" : "DEPENDS_ON", reason: l.reason ?? "" }));
  const seenMissing = new Set<string>();
  const missing = (out.missing ?? [])
    .map((m) => ({ name: (m.name ?? "").trim().slice(0, 80), reason: m.reason ?? "" }))
    .filter((m) => m.name && !known.has(m.name.toLowerCase()))
    .filter((m) => !seenMissing.has(m.name.toLowerCase()) && seenMissing.add(m.name.toLowerCase()))
    .slice(0, MAX_MISSING);
  return { links, missing };
}

export async function draftNotes(ctx: LearningContext): Promise<{ title: string; markdown: string }> {
  const out = await generateJson<{ title?: string; markdown?: string }>({
    parts: [{ text: `${prompt("learning", "draft")}\n\nTILE\n${ctx.text.slice(0, MAX_CONTEXT_CHARS)}` }, ...pdfParts(ctx.pdfs)],
    schema: obj({ title: S.str(), markdown: S.str() }),
    temperature: 0.4,
  });
  const markdown = (out.markdown ?? "").trim();
  if (!markdown) throw new Error("Gemini returned an empty draft");
  return { title: (out.title ?? "").trim().slice(0, 80) || ctx.title, markdown };
}

export async function generateQuiz(ctx: LearningContext, count: number): Promise<QuizQuestion[]> {
  const total = Math.min(20, Math.max(1, Math.round(count)));
  const mc = Math.min(total, Math.ceil(total * 0.6));
  const text =
    `${prompt("learning", "quiz")}\nWrite exactly ${total} questions: ${mc} of type MULTIPLE_CHOICE and ${total - mc} of type OPEN.` +
    `\n\nTILE\n${ctx.text.slice(0, MAX_CONTEXT_CHARS)}`;
  const out = await generateJson<{ questions?: Partial<QuizQuestion>[] }>({
    parts: [{ text }, ...pdfParts(ctx.pdfs)],
    schema: obj({
      questions: S.arr(obj(
        {
          type: S.enumStr(["MULTIPLE_CHOICE", "OPEN"]), prompt: S.str(), options: S.arr(S.str()), correctIndex: S.int(),
          explanation: S.str(), referenceAnswer: S.str(),
        },
        ["type", "prompt", "explanation"],
      )),
    }),
    temperature: 0.5,
  });
  const questions = (out.questions ?? [])
    .filter((q) => (q.prompt ?? "").trim())
    .map<QuizQuestion>((q) => {
      const options = Array.isArray(q.options) ? q.options.filter((o) => typeof o === "string") : [];
      const base = { prompt: q.prompt!.trim(), explanation: q.explanation ?? "", referenceAnswer: q.referenceAnswer ?? "" };
      return q.type === "MULTIPLE_CHOICE" && options.length >= 2
        ? { ...base, type: "MULTIPLE_CHOICE", options, correctIndex: Math.min(options.length - 1, Math.max(0, q.correctIndex ?? 0)) }
        : { ...base, type: "OPEN", options: [], correctIndex: 0 };
    });
  if (!questions.length) throw new Error("Gemini returned no questions");
  return questions;
}

export async function gradeOpenAnswer(question: string, referenceAnswer: string, userAnswer: string, ctx: LearningContext): Promise<OpenGrade> {
  const text =
    `${prompt("learning", "grade")}\n\nQUESTION\n${question}\n\nREFERENCE ANSWER\n${referenceAnswer}\n\nSTUDENT ANSWER\n${userAnswer}` +
    `\n\nTILE NOTES (for context)\n${ctx.text.slice(0, MAX_GRADE_CONTEXT_CHARS)}`;
  const out = await generateJson<Partial<OpenGrade>>({
    parts: [{ text }],
    schema: obj({ score: S.num(), verdict: S.enumStr(["correct", "partial", "incorrect"]), feedback: S.str() }),
    temperature: 0.1,
  });
  const verdict = out.verdict === "correct" || out.verdict === "partial" ? out.verdict : "incorrect";
  return { score: Math.min(1, Math.max(0, out.score ?? 0)), verdict, feedback: (out.feedback ?? "").trim() };
}
