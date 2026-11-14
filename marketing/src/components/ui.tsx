import { motion, type HTMLMotionProps } from "framer-motion";
import { ArrowRight } from "lucide-react";
import type { ReactNode } from "react";

/** Fades and lifts its children into view once, when scrolled to. */
export function Reveal({ children, delay = 0, className = "", ...rest }: HTMLMotionProps<"div"> & { delay?: number }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 24 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "-80px" }}
      transition={{ duration: 0.6, delay, ease: [0.22, 1, 0.36, 1] }}
      className={className}
      {...rest}
    >
      {children}
    </motion.div>
  );
}

export function SectionHeader({
  eyebrow, title, lead, align = "left", dark = false, id,
}: { eyebrow: string; title: ReactNode; lead?: ReactNode; align?: "left" | "center"; dark?: boolean; id?: string }) {
  return (
    <Reveal className={align === "center" ? "mx-auto max-w-3xl text-center" : "max-w-2xl"}>
      <p className={`eyebrow ${dark ? "text-gold" : ""}`}>{eyebrow}</p>
      <h2 id={id} className={`mt-3 font-display text-4xl leading-[1.08] font-semibold tracking-tight text-balance sm:text-5xl ${dark ? "text-cream" : "text-ink"}`}>
        {title}
      </h2>
      {lead && <p className={`mt-5 text-lg leading-relaxed text-pretty ${dark ? "text-cream/75" : "text-ink-soft"}`}>{lead}</p>}
    </Reveal>
  );
}

export function Button({
  href, children, variant = "primary", className = "", icon = true, ...rest
}: { href: string; children: ReactNode; variant?: "primary" | "secondary" | "light" | "ghost-dark"; className?: string; icon?: boolean } & React.AnchorHTMLAttributes<HTMLAnchorElement>) {
  const styles = {
    primary: "bg-terra text-white shadow-[0_10px_30px_-10px_rgb(194_86_43/0.7)] hover:bg-terra-deep",
    secondary: "bg-white/70 text-ink ring-1 ring-ink/15 hover:bg-white hover:ring-ink/25",
    light: "bg-cream text-ink hover:bg-white",
    "ghost-dark": "text-cream ring-1 ring-cream/30 hover:bg-cream/10",
  }[variant];
  return (
    <motion.a
      href={href}
      whileHover={{ y: -2 }}
      whileTap={{ scale: 0.98 }}
      className={`group inline-flex min-h-12 items-center justify-center gap-2 rounded-full px-6 text-[15px] font-semibold transition-colors ${styles} ${className}`}
      {...(rest as object)}
    >
      {children}
      {icon && <ArrowRight className="size-4 transition-transform group-hover:translate-x-0.5" aria-hidden />}
    </motion.a>
  );
}

/** Checklist row used in feature sections. */
export function Point({ icon: Icon, title, children, dark }: { icon: React.ComponentType<{ className?: string }>; title: string; children: ReactNode; dark?: boolean }) {
  return (
    <li className="flex gap-4">
      <span className={`mt-0.5 flex size-10 shrink-0 items-center justify-center rounded-xl ${dark ? "bg-cream/10 text-gold" : "bg-terra-soft text-terra"}`}>
        <Icon className="size-5" aria-hidden />
      </span>
      <div>
        <p className={`font-semibold ${dark ? "text-cream" : "text-ink"}`}>{title}</p>
        <p className={`mt-1 text-[15px] leading-relaxed ${dark ? "text-cream/70" : "text-ink-soft"}`}>{children}</p>
      </div>
    </li>
  );
}

export function Badge({ children, tone = "leaf" }: { children: ReactNode; tone?: "leaf" | "terra" | "kenvo" | "gold" }) {
  const t = {
    leaf: "bg-leaf-soft text-leaf",
    terra: "bg-terra-soft text-terra-deep",
    kenvo: "bg-kenvo/10 text-kenvo",
    gold: "bg-gold/15 text-[#8a6a2a]",
  }[tone];
  return <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-bold tracking-wide ${t}`}>{children}</span>;
}
