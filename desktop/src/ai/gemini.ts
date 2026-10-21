// Gemini request plumbing shared by every AI client. The key stays in Rust; see src-tauri/src/gemini.rs.
import { native } from "@/platform/native";

export const DEFAULT_MODEL = "gemini-3.6-flash";
const MODEL_KEY = "vector:geminiModel";
const MAX_IMAGE_SIDE = 1280;
const JPEG_QUALITY = 0.85;

export const getModel = () => localStorage.getItem(MODEL_KEY)?.trim() || DEFAULT_MODEL;
export function setModel(model: string) {
  if (model.trim() && model.trim() !== DEFAULT_MODEL) localStorage.setItem(MODEL_KEY, model.trim());
  else localStorage.removeItem(MODEL_KEY);
}

export type Part = { text: string } | { inlineData: { mimeType: string; data: string } };
export type Schema = Record<string, unknown>;

// ---- schema helpers (Gemini OpenAPI subset) ----
export const S = {
  str: (): Schema => ({ type: "STRING" }),
  num: (): Schema => ({ type: "NUMBER" }),
  int: (): Schema => ({ type: "INTEGER" }),
  enumStr: (values: readonly string[]): Schema => ({ type: "STRING", enum: [...values] }),
  arr: (items: Schema): Schema => ({ type: "ARRAY", items }),
  obj: (properties: Record<string, Schema>, required?: string[]): Schema => ({
    type: "OBJECT",
    properties,
    ...(required ? { required } : {}),
  }),
};

export function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const r = new FileReader();
    r.onload = () => resolve(String(r.result).split(",", 2)[1] ?? "");
    r.onerror = () => reject(r.error ?? new Error("Couldn't read file"));
    r.readAsDataURL(blob);
  });
}

/** Downscales to 1280 px on the long side and re-encodes as JPEG q85, like the phone. */
export async function imagePart(blob: Blob): Promise<Part> {
  try {
    const bmp = await createImageBitmap(blob);
    const scale = Math.min(1, MAX_IMAGE_SIDE / Math.max(bmp.width, bmp.height));
    const w = Math.max(1, Math.round(bmp.width * scale));
    const h = Math.max(1, Math.round(bmp.height * scale));
    const canvas = new OffscreenCanvas(w, h);
    canvas.getContext("2d")!.drawImage(bmp, 0, 0, w, h);
    bmp.close();
    const jpeg = await canvas.convertToBlob({ type: "image/jpeg", quality: JPEG_QUALITY });
    return { inlineData: { mimeType: "image/jpeg", data: await blobToBase64(jpeg) } };
  } catch {
    return { inlineData: { mimeType: blob.type || "image/jpeg", data: await blobToBase64(blob) } };
  }
}

export function audioMime(mime: string): string {
  const m = mime.toLowerCase();
  if (m.includes("mp4") || m.includes("m4a")) return "audio/mp4";
  if (m.includes("mpeg") || m.includes("mp3")) return "audio/mpeg";
  if (m.includes("wav")) return "audio/wav";
  if (m.includes("ogg")) return "audio/ogg";
  if (m.includes("webm")) return "audio/webm";
  if (m.includes("3gp")) return "audio/3gpp";
  return m.trim() || "audio/mp4";
}

/** First candidate's text with optional ```json fences removed. */
export function extractModelText(response: unknown): string | null {
  const r = response as { candidates?: { content?: { parts?: { text?: string }[] } }[] };
  const text = r?.candidates?.[0]?.content?.parts?.find((p) => typeof p.text === "string")?.text;
  if (typeof text !== "string") return null;
  return text.trim().replace(/^```(?:json)?\s*/i, "").replace(/\s*```$/, "").trim();
}

export interface JsonRequest {
  parts: Part[];
  schema: Schema;
  temperature: number;
}

/** Sends one user turn with a JSON response schema and returns the parsed JSON. */
export async function generateJson<T = unknown>({ parts, schema, temperature }: JsonRequest): Promise<T> {
  const response = await native.geminiGenerate(getModel(), {
    contents: [{ role: "user", parts }],
    generationConfig: { temperature, responseMimeType: "application/json", responseSchema: schema },
  });
  const text = extractModelText(response);
  if (!text) throw new Error("Gemini returned no JSON content");
  try {
    return JSON.parse(text) as T;
  } catch {
    throw new Error("Gemini returned malformed JSON");
  }
}

/** Cheap round trip used by Settings → Test key. */
export async function testKey(): Promise<string> {
  const out = await generateJson<{ ok: string }>({
    parts: [{ text: 'Reply with {"ok":"yes"}.' }],
    schema: S.obj({ ok: S.str() }, ["ok"]),
    temperature: 0,
  });
  return `${getModel()} answered: ${out.ok}`;
}
