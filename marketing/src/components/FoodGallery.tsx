import type { FoodKey } from "../data/images";
import { Photo } from "./Media";

const strip: { name: FoodKey; label: string }[] = [
  { name: "pizza", label: "Wood-fired" },
  { name: "cocktails", label: "From the bar" },
  { name: "fried-rice", label: "Rice dishes" },
  { name: "burger-fries", label: "Fast service" },
  { name: "pasta", label: "From the kitchen" },
  { name: "bowl", label: "Fresh & healthy" },
  { name: "ribs", label: "Grill" },
  { name: "coffee", label: "Café" },
  { name: "steak", label: "Fine dining" },
];

/** An auto-scrolling strip of food photography (decorative; paused for reduced motion). */
export default function FoodGallery() {
  const items = [...strip, ...strip];
  return (
    <section aria-label="Food photography" className="overflow-hidden border-y border-line bg-cream-2/60 py-6">
      <ul className="marquee flex w-max gap-4">
        {items.map((it, i) => (
          <li key={i} aria-hidden={i >= strip.length} className="relative h-44 w-64 shrink-0 overflow-hidden rounded-3xl sm:h-52 sm:w-80">
            <Photo name={it.name} sizes="20rem" className="size-full transition-transform duration-700 hover:scale-105" />
            <span className="absolute bottom-3 left-3 rounded-full bg-cream/90 px-3 py-1 text-xs font-bold text-ink backdrop-blur">{it.label}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}
