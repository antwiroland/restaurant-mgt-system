"use client";

/* eslint-disable @next/next/no-img-element -- menu images are arbitrary backend/CDN URLs */
import { useState } from "react";

/**
 * Menu photograph with a consistent aspect ratio.
 * When an item has no image (or it fails to load) we render a warm, clearly
 * decorative monogram tile instead of a stock photo — no fake food imagery.
 */
const tones = [
  ["var(--color-brand-soft)", "var(--color-brand)"],
  ["var(--color-sage-subtle)", "var(--color-sage)"],
  ["var(--color-accent-subtle)", "var(--color-accent-hover)"],
  ["var(--color-cream)", "var(--color-preparing)"],
  ["var(--color-success-subtle)", "var(--color-success-on)"],
] as const;

function toneFor(seed: string) {
  let hash = 0;
  for (let i = 0; i < seed.length; i += 1) hash = (hash * 31 + seed.charCodeAt(i)) >>> 0;
  return tones[hash % tones.length];
}

type FoodImageProps = {
  src?: string | null;
  alt: string;
  className?: string;
  /** e.g. "aspect-[4/3]" */
  ratio?: string;
  priority?: boolean;
  label?: string;
};

export function FoodImage({ src, alt, className = "", ratio = "aspect-[4/3]", priority = false, label }: FoodImageProps) {
  const [failed, setFailed] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const showImage = !!src && !failed;
  const [bg, fg] = toneFor(alt);

  return (
    <div className={`relative overflow-hidden bg-muted ${ratio} ${className}`}>
      {showImage ? (
        <img
          src={src}
          alt={alt}
          loading={priority ? "eager" : "lazy"}
          decoding="async"
          onLoad={() => setLoaded(true)}
          onError={() => setFailed(true)}
          className={`absolute inset-0 h-full w-full object-cover transition-[opacity,transform] duration-700 ease-out ${loaded ? "opacity-100" : "opacity-0"}`}
        />
      ) : (
        <div
          role="img"
          aria-label={`${alt} (no photo yet)`}
          className="absolute inset-0 grid place-items-center"
          style={{
            background: `radial-gradient(120% 90% at 30% 20%, ${bg} 0%, ${bg} 40%, color-mix(in srgb, ${bg} 75%, ${fg}) 100%)`,
          }}
        >
          <svg viewBox="0 0 120 120" className="h-[46%] w-[46%] opacity-90" aria-hidden="true">
            <circle cx="60" cy="60" r="52" fill="none" style={{ stroke: fg }} strokeOpacity="0.35" strokeWidth="1.5" />
            <circle cx="60" cy="60" r="40" style={{ fill: "var(--color-surface)" }} fillOpacity="0.6" />
            <text x="60" y="73" textAnchor="middle" fontSize="38" style={{ fill: fg, fontFamily: "var(--font-serif)" }}>
              {(label ?? alt).trim().charAt(0).toUpperCase()}
            </text>
          </svg>
        </div>
      )}
    </div>
  );
}
