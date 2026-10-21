import { describe, expect, it } from "vitest";
import { parsePrompts, prompt, type PromptFile } from "./prompts";

const MODES = ["SINGLE", "MULTIPLE", "PLAN_SCRATCH", "PLAN_PRESET"].map((m) => `mode.${m}`);

const EXPECTED: Record<PromptFile, string[]> = {
  nutrition: ["system", ...MODES],
  activity: ["system", ...MODES],
  exercise: ["system", ...MODES],
  sleep: ["system"],
  brain: ["transcribe", "summary"],
  motion: ["todos"],
  learning: ["links", "draft", "quiz", "grade"],
};

describe("shared prompts", () => {
  it("has every section the clients read", () => {
    for (const [file, sections] of Object.entries(EXPECTED) as [PromptFile, string[]][]) {
      for (const s of sections) expect(prompt(file, s).length, `${file}/${s}`).toBeGreaterThan(20);
    }
  });

  it("splits on markers and ignores the preamble", () => {
    expect(parsePrompts("# T\nintro\n=== a ===\none\n\n=== b ===\ntwo\n")).toEqual({ a: "one", b: "two" });
  });
});
