/** KENVO's own mark (from kenvointegratedsystems.com), drawn inline so it stays crisp. */
export function KenvoMark({ className = "size-9" }: { className?: string }) {
  return (
    <svg viewBox="0 0 40 40" className={className} aria-hidden>
      <rect width="40" height="40" rx="9" fill="#04060b" />
      <rect x="1" y="1" width="38" height="38" rx="8" fill="#071633" stroke="#2f6bff" strokeOpacity="0.6" strokeWidth="1.5" />
      <path d="M13.5 10.5v19M13.5 20.4 24.8 10.5M17.6 17.1l9.4 12.4" stroke="#5c8dff" strokeWidth="2.6" strokeLinecap="square" fill="none" />
      <circle cx="30.5" cy="9.5" r="2.6" fill="#5c8dff" />
    </svg>
  );
}

/** Product lockup: KENVO mark + product name. */
export function Logo({ dark = false }: { dark?: boolean }) {
  return (
    <span className="flex items-center gap-3">
      <KenvoMark />
      <span className="leading-tight whitespace-nowrap">
        <span className={`block font-display text-[15.5px] font-semibold sm:text-[17px] tracking-tight ${dark ? "text-cream" : "text-ink"}`}>
          Restaurant Management
        </span>
        <span className={`block font-brand text-[9.5px] font-bold tracking-[0.12em] sm:text-[10.5px] sm:tracking-[0.16em] uppercase ${dark ? "text-kenvo-bright" : "text-kenvo"}`}>
          by KENVO Integrated Systems
        </span>
      </span>
    </span>
  );
}
