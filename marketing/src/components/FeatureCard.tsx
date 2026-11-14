import { motion } from "framer-motion";
import type { ReactNode } from "react";

export default function FeatureCard({
  icon: Icon, title, children, index = 0, href,
}: { icon: React.ComponentType<{ className?: string }>; title: string; children: ReactNode; index?: number; href?: string }) {
  const body = (
    <>
      <span className="flex size-12 items-center justify-center rounded-2xl bg-terra-soft text-terra transition-colors duration-300 group-hover:bg-terra group-hover:text-white">
        <Icon className="size-6" aria-hidden />
      </span>
      <h3 className="mt-5 text-lg font-bold text-ink">{title}</h3>
      <p className="mt-2 text-[15px] leading-relaxed text-ink-soft">{children}</p>
      <span aria-hidden className="absolute right-5 bottom-5 font-display text-4xl font-semibold text-ink/[0.06] transition-colors group-hover:text-terra/15">
        {String(index + 1).padStart(2, "0")}
      </span>
    </>
  );
  return (
    <motion.li
      initial={{ opacity: 0, y: 24 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: "-60px" }}
      transition={{ duration: 0.55, delay: (index % 3) * 0.08, ease: [0.22, 1, 0.36, 1] }}
      whileHover={{ y: -6 }}
      className="group relative rounded-3xl bg-white p-6 shadow-soft ring-1 ring-ink/6 transition-shadow hover:shadow-lift sm:p-7"
    >
      {href ? <a href={href} className="block after:absolute after:inset-0 after:rounded-3xl">{body}</a> : body}
    </motion.li>
  );
}
