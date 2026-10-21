// Image picker for AI prompts: drop, paste (Ctrl+V) or browse; shows thumbnails.
import { useEffect, useMemo, useRef } from "react";
import { ImagePlus, X } from "lucide-react";
import { cn } from "@/lib/cn";

export function ImageDrop({
  images, onChange, max = 4, hint = "Drop, paste or browse images",
}: {
  images: Blob[];
  onChange: (images: Blob[]) => void;
  max?: number;
  hint?: string;
}) {
  const input = useRef<HTMLInputElement>(null);
  const urls = useMemo(() => images.map((b) => URL.createObjectURL(b)), [images]);
  useEffect(() => () => urls.forEach((u) => URL.revokeObjectURL(u)), [urls]);

  const add = (files: Iterable<Blob>) => {
    const next = [...images, ...[...files].filter((f) => f.type.startsWith("image/"))];
    onChange(next.slice(-max));
  };

  useEffect(() => {
    const onPaste = (e: ClipboardEvent) => {
      const files = [...(e.clipboardData?.items ?? [])].filter((i) => i.type.startsWith("image/")).map((i) => i.getAsFile()).filter((f): f is File => !!f);
      if (files.length) {
        e.preventDefault();
        add(files);
      }
    };
    window.addEventListener("paste", onPaste);
    return () => window.removeEventListener("paste", onPaste);
  });

  return (
    <div
      className="rounded-xl border border-dashed border-hairline bg-offwhite/60 p-3"
      onDragOver={(e) => e.preventDefault()}
      onDrop={(e) => {
        e.preventDefault();
        add(e.dataTransfer.files);
      }}
    >
      <div className="flex flex-wrap gap-2">
        {urls.map((u, i) => (
          <div key={u} className="relative h-20 w-20 overflow-hidden rounded-lg border border-hairline bg-white">
            <img src={u} alt="" className="h-full w-full object-cover" />
            <button
              className="absolute top-1 right-1 rounded-full bg-black/60 p-0.5 text-white"
              onClick={() => onChange(images.filter((_, j) => j !== i))}
              aria-label="Remove image"
            >
              <X size={12} />
            </button>
          </div>
        ))}
        {images.length < max && (
          <button
            className={cn("flex h-20 flex-1 items-center justify-center gap-2 rounded-lg text-xs text-muted hover:bg-white", images.length ? "min-w-32" : "w-full")}
            onClick={() => input.current?.click()}
          >
            <ImagePlus size={16} /> {hint}
          </button>
        )}
      </div>
      <input
        ref={input}
        type="file"
        accept="image/*"
        multiple={max > 1}
        className="hidden"
        onChange={(e) => {
          if (e.target.files) add(e.target.files);
          e.target.value = "";
        }}
      />
    </div>
  );
}
