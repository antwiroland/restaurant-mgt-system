import Link from "next/link";
import type { ReactNode } from "react";
import { BrandMark } from "@/components/NavSidebar";
import { brand } from "@/lib/brand";

/** Centered card used by account pages (reset password, verify email, …). */
export function AuthCard({ eyebrow, title, description, children }: { eyebrow: string; title: string; description?: ReactNode; children: ReactNode }) {
  return (
    <main className="grid min-h-dvh place-items-center bg-ivory px-4 py-10">
      <div className="w-full max-w-[420px]">
        <Link href="/" className="mx-auto mb-8 flex w-fit items-center gap-3" aria-label={`${brand.name} home`}>
          <BrandMark size={40} />
          <span className="font-serif text-xl font-semibold">{brand.name}</span>
        </Link>
        <section className="rounded-[20px] border border-line bg-surface p-7 shadow-card sm:p-8">
          <p className="kicker">{eyebrow}</p>
          <h1 className="heading-lg mt-2">{title}</h1>
          {description ? <p className="mt-2 text-sm leading-relaxed text-ink-soft">{description}</p> : null}
          <div className="mt-6">{children}</div>
        </section>
      </div>
    </main>
  );
}
