"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { defaultLocale, isLocale, locales, localeNames, translate, type Locale, type MessageKey } from "@/lib/i18n";

const STORAGE_KEY = "locale";

type I18nValue = {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  t: (key: MessageKey, vars?: Record<string, string | number>) => string;
};

const I18nContext = createContext<I18nValue>({
  locale: defaultLocale,
  setLocale: () => {},
  t: (key, vars) => translate(defaultLocale, key, vars),
});

function readStoredLocale(): Locale {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY);
    return isLocale(stored) ? stored : defaultLocale;
  } catch {
    return defaultLocale;
  }
}

export function I18nProvider({ children }: { children: React.ReactNode }) {
  // Server and first client render use the default locale; a stored choice applies after hydration.
  const [locale, setLocaleState] = useState<Locale>(defaultLocale);

  useEffect(() => {
    const stored = readStoredLocale();
    if (stored !== defaultLocale) {
      const frame = window.requestAnimationFrame(() => setLocaleState(stored));
      return () => window.cancelAnimationFrame(frame);
    }
  }, []);

  useEffect(() => {
    document.documentElement.lang = locale;
  }, [locale]);

  const setLocale = useCallback((next: Locale) => {
    setLocaleState(next);
    try {
      window.localStorage.setItem(STORAGE_KEY, next);
    } catch {
      /* preference just won't persist */
    }
  }, []);

  const value = useMemo<I18nValue>(
    () => ({ locale, setLocale, t: (key, vars) => translate(locale, key, vars) }),
    [locale, setLocale],
  );
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n(): I18nValue {
  return useContext(I18nContext);
}

/** Shortcut: `const t = useT(); t("nav.menu")`. */
export function useT() {
  return useContext(I18nContext).t;
}

/** Language picker; hidden while only one language is installed. */
export function LanguageSelect({ className = "" }: { className?: string }) {
  const { locale, setLocale, t } = useI18n();
  if (locales.length < 2) return null;
  return (
    <label className={`flex items-center gap-2 text-sm ${className}`}>
      <span className="sr-only">{t("common.language")}</span>
      <select className="select min-h-[36px] w-auto py-1 text-sm" value={locale} onChange={(e) => isLocale(e.target.value) && setLocale(e.target.value)}>
        {locales.map((code) => (
          <option key={code} value={code}>{localeNames[code]}</option>
        ))}
      </select>
    </label>
  );
}
