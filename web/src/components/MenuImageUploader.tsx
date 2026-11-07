"use client";

/* eslint-disable @next/next/no-img-element -- previews are object URLs / backend media URLs */
import { useEffect, useRef, useState } from "react";
import { Icon } from "@/components/Icon";
import { Spinner } from "@/components/Spinner";
import { useStaffSession } from "@/components/SessionProvider";
import { removeMenuItemImage, uploadMenuItemImage } from "@/lib/apiClient";

const ACCEPT = ["image/jpeg", "image/png", "image/webp"];
const MAX_BYTES = 5 * 1024 * 1024; // matches the backend default MEDIA_MAX_FILE_SIZE

type Props = {
  menuItemId: string;
  imageUrl?: string;
  onChange: (imageUrl: string) => void;
};

/** Upload / replace / remove a dish photo. Changes are saved immediately. */
export function MenuImageUploader({ menuItemId, imageUrl, onChange }: Props) {
  const { authenticatedFetch } = useStaffSession();
  const inputRef = useRef<HTMLInputElement>(null);
  const [preview, setPreview] = useState<string | null>(null);
  const [busy, setBusy] = useState<"upload" | "remove" | null>(null);
  const [error, setError] = useState("");
  const [dragging, setDragging] = useState(false);

  useEffect(() => () => {
    if (preview) URL.revokeObjectURL(preview);
  }, [preview]);

  async function handleFile(file: File | undefined) {
    if (!file) return;
    setError("");
    if (!ACCEPT.includes(file.type)) {
      setError("Use a JPG, PNG or WebP image.");
      return;
    }
    if (file.size > MAX_BYTES) {
      setError("That image is larger than 5 MB.");
      return;
    }
    const local = URL.createObjectURL(file);
    setPreview(local);
    setBusy("upload");
    try {
      const result = await authenticatedFetch((session) => uploadMenuItemImage(session, menuItemId, file));
      onChange(result.imageUrl);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Upload failed");
      setPreview(null);
    } finally {
      setBusy(null);
      if (inputRef.current) inputRef.current.value = "";
    }
  }

  async function handleRemove() {
    setError("");
    setBusy("remove");
    try {
      await authenticatedFetch((session) => removeMenuItemImage(session, menuItemId));
      setPreview(null);
      onChange("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not remove image");
    } finally {
      setBusy(null);
    }
  }

  const shown = preview ?? imageUrl;

  return (
    <div className="field">
      <span className="field-label">Photo</span>
      <div
        className={`relative grid overflow-hidden rounded-xl border-2 border-dashed transition-colors ${dragging ? "border-brand bg-brand-subtle" : "border-line-strong bg-ivory"}`}
        onDragOver={(event) => { event.preventDefault(); setDragging(true); }}
        onDragLeave={() => setDragging(false)}
        onDrop={(event) => {
          event.preventDefault();
          setDragging(false);
          void handleFile(event.dataTransfer.files?.[0]);
        }}
      >
        {shown ? (
          <div className="relative aspect-[4/3]">
            <img src={shown} alt="Dish photo preview" className="absolute inset-0 h-full w-full object-cover" />
            {busy ? (
              <div className="absolute inset-0 grid place-items-center bg-overlay text-white"><Spinner size={28} /></div>
            ) : null}
          </div>
        ) : (
          <button type="button" onClick={() => inputRef.current?.click()} className="grid aspect-[4/3] place-items-center gap-2 p-6 text-center text-ink-soft hover:text-ink">
            <span className="grid justify-items-center gap-2">
              <span className="grid h-12 w-12 place-items-center rounded-full bg-surface shadow-xs"><Icon name="image" size={22} /></span>
              <span className="text-sm font-semibold">Drop a photo here or click to upload</span>
              <span className="text-xs text-ink-muted">JPG, PNG or WebP · up to 5 MB · 4:3 looks best</span>
            </span>
          </button>
        )}
      </div>
      <input
        ref={inputRef}
        type="file"
        accept={ACCEPT.join(",")}
        className="sr-only"
        onChange={(event) => void handleFile(event.target.files?.[0])}
        aria-label="Upload dish photo"
      />
      {shown ? (
        <div className="flex gap-2">
          <button type="button" className="btn btn-secondary btn-sm" disabled={!!busy} onClick={() => inputRef.current?.click()}>
            <Icon name="upload" size={16} /> Replace
          </button>
          <button type="button" className="btn btn-ghost btn-sm text-danger-on" disabled={!!busy} onClick={() => void handleRemove()}>
            {busy === "remove" ? <Spinner size={14} /> : <Icon name="trash" size={16} />} Remove
          </button>
        </div>
      ) : null}
      {error ? <span className="field-error" role="alert">{error}</span> : <span className="field-hint">Saved as soon as it uploads.</span>}
    </div>
  );
}
