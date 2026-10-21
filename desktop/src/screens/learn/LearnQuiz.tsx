// Gemini quiz over a Learn tile (LearnQuizViewModel). In Review it decides the ladder step; otherwise it just records the score.
import { useEffect, useRef, useState } from "react";
import { Check, Sparkles, X } from "lucide-react";
import { generateQuiz, gradeOpenAnswer, type LearningContext, type QuizQuestion } from "@/ai/learning";
import { loadLearningContext } from "@/ai/learningContext";
import { Markdown } from "@/components/Markdown";
import { effectiveStatus, intervalDays } from "@/domain/learn";
import * as ops from "@/domain/ops";
import { cn } from "@/lib/cn";
import { errorMessage } from "@/platform/native";
import { useBrain } from "@/store/brain";
import { learnSettingsNow } from "@/store/learnSettings";
import { Button, Dialog, Textarea } from "@/ui/primitives";

interface Answer {
  selectedIndex: number | null;
  text: string;
  score: number | null;
  verdict: string;
  feedback: string;
  grading: boolean;
  gradeError: string | null;
}

const emptyAnswer = (): Answer => ({ selectedIndex: null, text: "", score: null, verdict: "", feedback: "", grading: false, gradeError: null });

type Phase = "loading" | "error" | "question" | "summary";

export function LearnQuiz({ nodeId, open, onClose }: { nodeId: string; open: boolean; onClose: () => void }) {
  return (
    <Dialog open={open} onClose={onClose} title="Quiz" width="max-w-2xl" dismissable>
      {open && <QuizBody nodeId={nodeId} onClose={onClose} />}
    </Dialog>
  );
}

function QuizBody({ nodeId, onClose }: { nodeId: string; onClose: () => void }) {
  const commit = useBrain((s) => s.commit);
  const settings = useRef(learnSettingsNow()).current;
  const tile = useRef(useBrain.getState().brain.learningTiles.find((t) => t.id === nodeId)).current;
  const isReview = tile ? effectiveStatus(tile, settings) === "TO_REVIEW" : false;
  const ctx = useRef<LearningContext | null>(null);
  const [phase, setPhase] = useState<Phase>("loading");
  const [error, setError] = useState<string | null>(null);
  const [questions, setQuestions] = useState<QuizQuestion[]>([]);
  const [index, setIndex] = useState(0);
  const [answers, setAnswers] = useState<Record<number, Answer>>({});
  const [outcome, setOutcome] = useState<string | null>(null);

  const load = async () => {
    setPhase("loading");
    setError(null);
    try {
      ctx.current ??= await loadLearningContext(nodeId, true);
      const qs = await generateQuiz(ctx.current, settings.quizSize);
      setQuestions(qs);
      setAnswers({});
      setIndex(0);
      setPhase("question");
    } catch (e) {
      setError(errorMessage(e));
      setPhase("error");
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const q = questions[index];
  const answer = answers[index] ?? emptyAnswer();
  const setAnswer = (a: Answer, i = index) => setAnswers((m) => ({ ...m, [i]: a }));
  const score = questions.length ? questions.reduce((sum, _, i) => sum + (answers[i]?.score ?? 0), 0) / questions.length : 0;
  const passed = score * 100 >= settings.passPercent;
  const nextInterval = intervalDays(settings, (tile?.reviewStep ?? 0) + 1);

  const select = (i: number) => {
    if (!q || answer.score !== null) return;
    const correct = i === q.correctIndex;
    setAnswer({ ...emptyAnswer(), selectedIndex: i, score: correct ? 1 : 0, verdict: correct ? "correct" : "incorrect" });
  };

  const check = async () => {
    if (!q || !ctx.current || !answer.text.trim() || answer.grading || answer.score !== null) return;
    const i = index;
    const current = answer;
    setAnswer({ ...current, grading: true, gradeError: null }, i);
    try {
      const g = await gradeOpenAnswer(q.prompt, q.referenceAnswer, current.text.trim(), ctx.current);
      setAnswer({ ...current, score: g.score, verdict: g.verdict, feedback: g.feedback, grading: false }, i);
    } catch (e) {
      setAnswer({ ...current, grading: false, gradeError: errorMessage(e) }, i);
    }
  };

  const next = () => {
    if (answer.score === null) return;
    if (index < questions.length - 1) setIndex(index + 1);
    else {
      setPhase("summary");
      if (!isReview) {
        commit((s) => ops.saveQuizScore(s, nodeId, score));
        setOutcome("Score saved");
      }
    }
  };

  const completeReview = (solid: boolean) => {
    commit((s) => ops.completeReview(s, nodeId, solid, score, settings));
    setOutcome(!solid ? "Back to In Progress · the review schedule restarts" : nextInterval !== null ? `Next review in ${nextInterval} days` : "Mastered · no more reviews");
  };

  if (phase === "loading") {
    return (
      <div className="flex flex-col items-center gap-3 py-16 text-sm text-muted">
        <Sparkles size={22} className="animate-pulse text-electric" />
        Writing {settings.quizSize} questions from your notes…
      </div>
    );
  }
  if (phase === "error") {
    return (
      <div className="space-y-4 py-6 text-center">
        <div className="text-sm text-danger">{error}</div>
        <Button onClick={() => void load()}>Try again</Button>
      </div>
    );
  }
  if (phase === "summary") {
    const pct = Math.round(score * 100);
    return (
      <div className="space-y-5">
        <div className="text-center">
          <div className={cn("text-5xl font-semibold tabular-nums", passed ? "text-prio-low" : "text-prio-high")}>{pct}%</div>
          <div className="mt-1 text-sm text-muted">Pass mark {settings.passPercent}% · {passed ? "passed" : "not passed"}</div>
        </div>
        <div className="space-y-1">
          {questions.map((qq, i) => {
            const a = answers[i];
            const ok = (a?.score ?? 0) >= 0.5;
            return (
              <button key={i} onClick={() => { setIndex(i); setPhase("question"); }} className="flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left text-sm hover:bg-offwhite">
                {ok ? <Check size={14} className="shrink-0 text-prio-low" /> : <X size={14} className="shrink-0 text-prio-high" />}
                <span className="truncate">{qq.prompt}</span>
                <span className="ml-auto text-xs tabular-nums text-item">{Math.round((a?.score ?? 0) * 100)}%</span>
              </button>
            );
          })}
        </div>
        {outcome ? (
          <div className="rounded-lg bg-offwhite px-4 py-3 text-center text-sm font-medium">{outcome}</div>
        ) : isReview ? (
          <div className="rounded-xl border border-hairline p-4">
            <div className="mb-3 text-sm">
              {passed ? "Looks solid." : "Below the pass mark."}{" "}
              <span className="text-muted">
                Solid moves the tile up the ladder{nextInterval !== null ? ` (next review in ${nextInterval} days)` : " and masters it"}; shaky sends it back to In Progress.
              </span>
            </div>
            <div className="flex justify-end gap-2">
              <Button variant={passed ? "secondary" : "primary"} onClick={() => completeReview(false)}>Shaky</Button>
              <Button variant={passed ? "primary" : "secondary"} onClick={() => completeReview(true)}>Solid</Button>
            </div>
          </div>
        ) : null}
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => void load()}>New quiz</Button>
          <Button onClick={onClose}>Close</Button>
        </div>
      </div>
    );
  }

  if (!q) return null;
  const answered = answer.score !== null;
  return (
    <div className="space-y-4" onKeyDown={(e) => e.key === "Enter" && e.ctrlKey && (answered ? next() : void check())}>
      <div className="flex items-center gap-2">
        <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-offwhite">
          <div className="h-full bg-navy transition-all" style={{ width: `${((index + (answered ? 1 : 0)) / questions.length) * 100}%` }} />
        </div>
        <span className="text-xs tabular-nums text-item">{index + 1} / {questions.length}</span>
      </div>
      <div className="text-base font-medium"><Markdown>{q.prompt}</Markdown></div>

      {q.type === "MULTIPLE_CHOICE" ? (
        <div className="space-y-2">
          {q.options.map((o, i) => {
            const chosen = answer.selectedIndex === i;
            const correct = i === q.correctIndex;
            return (
              <button
                key={i}
                disabled={answered}
                onClick={() => select(i)}
                className={cn(
                  "flex w-full items-start gap-3 rounded-lg border px-3 py-2.5 text-left text-sm transition",
                  !answered && "border-hairline hover:border-electric",
                  answered && correct && "border-prio-low bg-prio-low/10",
                  answered && chosen && !correct && "border-prio-high bg-prio-high/10",
                  answered && !chosen && !correct && "border-hairline opacity-60",
                )}
              >
                <span className="mt-px font-mono text-xs text-item">{String.fromCharCode(65 + i)}</span>
                <span className="flex-1">{o}</span>
              </button>
            );
          })}
        </div>
      ) : (
        <div className="space-y-2">
          <Textarea
            autoFocus
            rows={5}
            value={answer.text}
            disabled={answered || answer.grading}
            onChange={(e) => setAnswer({ ...answer, text: e.target.value, gradeError: null })}
            placeholder="Your answer"
          />
          {answer.gradeError && <div className="text-sm text-danger">{answer.gradeError}</div>}
          {!answered && (
            <div className="flex justify-end gap-2">
              <Button variant="ghost" disabled={answer.grading} onClick={() => setAnswer({ ...answer, score: 0, verdict: "skipped" })}>Skip</Button>
              <Button disabled={!answer.text.trim() || answer.grading} onClick={() => void check()}>{answer.grading ? "Grading…" : "Check"}</Button>
            </div>
          )}
        </div>
      )}

      {answered && (
        <div className="rounded-lg bg-offwhite px-4 py-3 text-sm">
          <div className={cn("mb-1 font-semibold capitalize", answer.verdict === "correct" ? "text-prio-low" : answer.verdict === "partial" ? "text-prio-mid" : "text-prio-high")}>
            {answer.verdict}{q.type === "OPEN" && answer.verdict !== "skipped" ? ` · ${Math.round((answer.score ?? 0) * 100)}%` : ""}
          </div>
          {answer.feedback && <div className="mb-2">{answer.feedback}</div>}
          {q.type === "OPEN" && q.referenceAnswer && <div className="mb-2 text-muted"><b>Reference:</b> {q.referenceAnswer}</div>}
          {q.explanation && <div className="text-muted">{q.explanation}</div>}
        </div>
      )}

      <div className="flex justify-between">
        <Button variant="ghost" disabled={index === 0} onClick={() => setIndex(index - 1)}>Back</Button>
        <Button disabled={!answered} onClick={next}>{index < questions.length - 1 ? "Next" : "Finish"}</Button>
      </div>
    </div>
  );
}
