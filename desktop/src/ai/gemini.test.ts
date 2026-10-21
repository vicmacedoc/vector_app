import { describe, expect, it } from "vitest";
import { audioMime, extractModelText } from "./gemini";
import { parseDeadline } from "./motion";

describe("extractModelText", () => {
  it("strips json fences from the first text part", () => {
    const r = { candidates: [{ content: { parts: [{ text: '```json\n{"a":1}\n```' }] } }] };
    expect(extractModelText(r)).toBe('{"a":1}');
  });
  it("returns null without candidates", () => {
    expect(extractModelText({ error: {} })).toBeNull();
  });
});

describe("audioMime", () => {
  it("normalizes recorder mime types", () => {
    expect(audioMime("audio/webm;codecs=opus")).toBe("audio/webm");
    expect(audioMime("audio/x-m4a")).toBe("audio/mp4");
    expect(audioMime("")).toBe("audio/mp4");
  });
});

describe("parseDeadline", () => {
  it("uses 18:00 for date-only deadlines", () => {
    expect(new Date(parseDeadline("2026-10-23")!).getHours()).toBe(18);
  });
  it("keeps explicit times", () => {
    const d = new Date(parseDeadline("2026-10-23T09:30")!);
    expect([d.getHours(), d.getMinutes()]).toEqual([9, 30]);
  });
  it("rejects garbage", () => {
    expect(parseDeadline("soon")).toBeNull();
  });
});
