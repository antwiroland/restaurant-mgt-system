import { AnimatePresence, motion, useMotionValueEvent, useScroll } from "framer-motion";
import { Menu, X } from "lucide-react";
import { useEffect, useState } from "react";
import { nav } from "../data/site";
import { Logo } from "./Logo";
import { Button } from "./ui";

export default function Navbar() {
  const { scrollY } = useScroll();
  const [scrolled, setScrolled] = useState(false);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState("#home");

  useMotionValueEvent(scrollY, "change", (y) => setScrolled(y > 12));

  // highlight the section in view
  useEffect(() => {
    const ids = nav.map((n) => n.href.slice(1));
    const obs = new IntersectionObserver(
      (entries) => entries.forEach((e) => e.isIntersecting && setActive(`#${e.target.id}`)),
      { rootMargin: "-45% 0px -50% 0px" },
    );
    ids.forEach((id) => { const el = document.getElementById(id); if (el) obs.observe(el); });
    return () => obs.disconnect();
  }, []);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open]);

  return (
    <header className="fixed inset-x-0 top-0 z-50 px-3 pt-3 sm:px-5">
      <motion.nav
        aria-label="Main"
        initial={{ y: -30, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
        className={`mx-auto flex max-w-[78rem] items-center justify-between rounded-2xl px-3 py-2.5 transition-all duration-300 sm:px-4 ${
          scrolled || open ? "bg-cream/90 shadow-soft ring-1 ring-ink/8 backdrop-blur-md" : "bg-transparent"
        }`}
      >
        <a href="#home" className="rounded-lg" aria-label="Restaurant Management System by KENVO Integrated Systems, home">
          <Logo />
        </a>

        <ul className="hidden items-center gap-1 lg:flex">
          {nav.map((item) => (
            <li key={item.href}>
              <a
                href={item.href}
                aria-current={active === item.href ? "true" : undefined}
                className="relative rounded-full px-3.5 py-2 text-[14.5px] font-semibold text-ink-soft transition-colors hover:text-ink"
              >
                {active === item.href && (
                  <motion.span layoutId="nav-pill" className="absolute inset-0 -z-10 rounded-full bg-ink/6" transition={{ type: "spring", stiffness: 380, damping: 32 }} />
                )}
                {item.label}
              </a>
            </li>
          ))}
        </ul>

        <div className="flex items-center gap-2">
          <span className="hidden sm:block"><Button href="#demo" className="min-h-11 px-5 text-sm">Request a Demo</Button></span>
          <button
            type="button"
            className="flex size-11 items-center justify-center rounded-xl text-ink hover:bg-ink/6 lg:hidden"
            aria-expanded={open}
            aria-controls="mobile-menu"
            aria-label={open ? "Close menu" : "Open menu"}
            onClick={() => setOpen((v) => !v)}
          >
            {open ? <X className="size-6" aria-hidden /> : <Menu className="size-6" aria-hidden />}
          </button>
        </div>
      </motion.nav>

      <AnimatePresence>
        {open && (
          <motion.div
            id="mobile-menu"
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            className="mx-auto mt-2 max-w-[78rem] rounded-2xl bg-cream p-3 shadow-lift ring-1 ring-ink/8 lg:hidden"
          >
            <ul className="grid gap-1">
              {nav.map((item) => (
                <li key={item.href}>
                  <a href={item.href} onClick={() => setOpen(false)} className="block rounded-xl px-4 py-3 text-base font-semibold text-ink hover:bg-ink/5">
                    {item.label}
                  </a>
                </li>
              ))}
            </ul>
            <Button href="#demo" className="mt-2 w-full" onClick={() => setOpen(false)}>Request a Demo</Button>
          </motion.div>
        )}
      </AnimatePresence>
    </header>
  );
}
