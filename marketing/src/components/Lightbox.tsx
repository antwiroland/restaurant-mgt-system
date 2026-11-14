import { AnimatePresence, motion } from "framer-motion";
import { X } from "lucide-react";
import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { screens, type ScreenKey } from "../data/images";

type State = { key: ScreenKey; caption?: string } | null;
const Ctx = createContext<{ open: (key: ScreenKey, caption?: string) => void }>({ open: () => {} });

export const useLightbox = () => useContext(Ctx);

export function LightboxProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<State>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  const returnTo = useRef<HTMLElement | null>(null);

  const open = useCallback((key: ScreenKey, caption?: string) => {
    returnTo.current = document.activeElement as HTMLElement;
    setState({ key, caption });
  }, []);
  const close = useCallback(() => {
    setState(null);
    returnTo.current?.focus();
  }, []);

  useEffect(() => {
    if (!state) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") close();
      if (e.key === "Tab") { e.preventDefault(); closeRef.current?.focus(); } // single focusable control
    };
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    closeRef.current?.focus();
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
    };
  }, [state, close]);

  const info = state ? screens[state.key] : null;

  return (
    <Ctx.Provider value={{ open }}>
      {children}
      <AnimatePresence>
        {state && info && (
          <motion.div
            className="fixed inset-0 z-[100] flex items-center justify-center bg-kenvo-night/85 p-3 backdrop-blur-sm sm:p-8"
            role="dialog"
            aria-modal="true"
            aria-label={state.caption ?? info.alt}
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={close}
          >
            <button
              ref={closeRef}
              type="button"
              onClick={close}
              className="absolute top-3 right-3 z-10 rounded-full bg-white/10 p-2.5 text-white transition hover:bg-white/20 sm:top-5 sm:right-5"
              aria-label="Close screenshot"
            >
              <X className="size-5" aria-hidden />
            </button>
            <motion.figure
              className="flex max-h-full max-w-6xl flex-col items-center"
              initial={{ scale: 0.96, y: 10 }}
              animate={{ scale: 1, y: 0 }}
              exit={{ scale: 0.97, opacity: 0 }}
              transition={{ type: "spring", stiffness: 260, damping: 26 }}
              onClick={(e) => e.stopPropagation()}
            >
              <img
                src={`/images/${info.dir}/${state.key}-${info.widths[info.widths.length - 1]}.webp`}
                alt={info.alt}
                width={info.w}
                height={info.h}
                className="max-h-[80vh] w-auto rounded-xl bg-white object-contain shadow-2xl"
              />
              <figcaption className="mt-4 max-w-2xl text-center text-sm text-white/80">{state.caption ?? info.alt}</figcaption>
            </motion.figure>
          </motion.div>
        )}
      </AnimatePresence>
    </Ctx.Provider>
  );
}
