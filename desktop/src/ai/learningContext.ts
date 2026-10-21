import { buildLearningText, MAX_PDF_BYTES } from "@/domain/learnContext";
import { native } from "@/platform/native";
import { useBrain } from "@/store/brain";
import { ensureAssetFile } from "@/sync/engine";
import type { LearningContext } from "./learning";

/** Builds the Gemini context for a tile; PDFs are read from the local cache (downloaded if needed). */
export async function loadLearningContext(nodeId: string, includePdfs = false): Promise<LearningContext> {
  const built = buildLearningText(useBrain.getState().brain, nodeId, { includePdfs });
  const pdfs: LearningContext["pdfs"] = [];
  for (const asset of built.pdfAssets) {
    try {
      const path = await ensureAssetFile(asset);
      if (!path) continue;
      pdfs.push({ name: asset.fileName, base64: await native.readFileBase64(path, MAX_PDF_BYTES), size: asset.sizeBytes });
    } catch {
      // A PDF that can't be fetched is skipped; the notes still go in.
    }
  }
  return { nodeId, title: built.title, text: built.text, pdfs };
}
