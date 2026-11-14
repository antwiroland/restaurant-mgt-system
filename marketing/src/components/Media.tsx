import { Maximize2 } from "lucide-react";
import { food, screens, type FoodKey, type ScreenKey } from "../data/images";
import { useLightbox } from "./Lightbox";

type Info = { w: number; h: number; widths: readonly number[]; alt: string; dir: string };

export function srcFor(info: Info, key: string, width?: number) {
  const w = width ?? info.widths[info.widths.length - 1];
  return `/images/${info.dir}/${key}-${w}.webp`;
}
function srcSet(info: Info, key: string) {
  return info.widths.map((w) => `${srcFor(info, key, w)} ${w}w`).join(", ");
}

type ImgProps = {
  sizes?: string;
  className?: string;
  eager?: boolean;
  alt?: string;
};

/** A food or interior photograph. */
export function Photo({ name, sizes = "(min-width: 1024px) 50vw, 100vw", className = "", eager, alt }: ImgProps & { name: FoodKey }) {
  const info = food[name];
  return (
    <img
      src={srcFor(info, name, info.widths[0])}
      srcSet={srcSet(info, name)}
      sizes={sizes}
      width={info.w}
      height={info.h}
      alt={alt ?? info.alt}
      loading={eager ? "eager" : "lazy"}
      fetchPriority={eager ? "high" : undefined}
      decoding="async"
      className={`object-cover ${className}`}
    />
  );
}

/** A real application screenshot. Clicking it opens it full size. */
export function Shot({
  name, sizes = "(min-width: 1024px) 60vw, 100vw", className = "", eager, caption, zoom = true,
}: ImgProps & { name: ScreenKey; caption?: string; zoom?: boolean }) {
  const info = screens[name];
  const { open } = useLightbox();
  const img = (
    <img
      src={srcFor(info, name, info.widths[0])}
      srcSet={srcSet(info, name)}
      sizes={sizes}
      width={info.w}
      height={info.h}
      alt={info.alt}
      loading={eager ? "eager" : "lazy"}
      decoding="async"
      className={`block h-auto w-full ${className}`}
    />
  );
  if (!zoom) return img;
  return (
    <button
      type="button"
      onClick={() => open(name, caption)}
      className="group relative block w-full cursor-zoom-in text-left"
      aria-label={`Enlarge screenshot: ${caption ?? info.alt}`}
    >
      {img}
      <span className="pointer-events-none absolute right-3 bottom-3 flex items-center gap-1.5 rounded-full bg-ink/80 px-2.5 py-1 text-xs font-semibold text-cream opacity-0 backdrop-blur transition group-hover:opacity-100 group-focus-visible:opacity-100">
        <Maximize2 className="size-3.5" aria-hidden /> Enlarge
      </span>
    </button>
  );
}

/** Desktop screenshot inside a browser window. */
export function BrowserFrame({
  name, caption, className = "", sizes, eager, url = "app.restaurant",
}: { name: ScreenKey; caption?: string; className?: string; sizes?: string; eager?: boolean; url?: string }) {
  return (
    <figure className={className}>
      <div className="overflow-hidden rounded-2xl border border-ink/10 bg-white shadow-lift">
        <div className="flex items-center gap-2 border-b border-ink/8 bg-cream-2/70 px-3 py-2" aria-hidden>
          <span className="size-2.5 rounded-full bg-[#ef6a5e]" />
          <span className="size-2.5 rounded-full bg-[#f5bf4f]" />
          <span className="size-2.5 rounded-full bg-[#61c454]" />
          <span className="ml-3 hidden truncate rounded-md bg-white/80 px-3 py-0.5 text-[11px] text-ink-muted sm:block">{url}</span>
        </div>
        <Shot name={name} caption={caption} sizes={sizes} eager={eager} />
      </div>
      {caption && <figcaption className="mt-3 text-center text-sm text-ink-muted">{caption}</figcaption>}
    </figure>
  );
}

/** Phone screenshot inside a phone body. */
export function PhoneFrame({
  name, caption, className = "", width = "w-56", eager,
}: { name: ScreenKey; caption?: string; className?: string; width?: string; eager?: boolean }) {
  return (
    <figure className={`${width} ${className}`}>
      <div className="relative rounded-[2.4rem] bg-ink p-2.5 shadow-lift ring-1 ring-black/40">
        <div className="absolute top-3.5 left-1/2 z-10 h-1.5 w-14 -translate-x-1/2 rounded-full bg-black/70" aria-hidden />
        <div className="overflow-hidden rounded-[1.9rem] bg-cream">
          <Shot name={name} caption={caption} sizes="(min-width: 1024px) 18rem, 60vw" eager={eager} />
        </div>
      </div>
      {caption && <figcaption className="mt-3 text-center text-sm leading-snug text-ink-muted">{caption}</figcaption>}
    </figure>
  );
}
