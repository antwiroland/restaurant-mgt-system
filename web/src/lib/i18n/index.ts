import en, { type Messages } from "./messages/en";

/**
 * Localization framework.
 *
 * Adding a language:
 *   1. copy messages/en.ts to messages/<code>.ts and translate the values
 *      (TypeScript checks the file has every key);
 *   2. register it in `catalogs` and `localeNames` below.
 * Missing keys fall back to English, then to the key itself.
 */
export const catalogs = {
  en,
} satisfies Record<string, Messages>;

export type Locale = keyof typeof catalogs;

export const localeNames: Record<Locale, string> = {
  en: "English",
};

export const defaultLocale: Locale = "en";
export const locales = Object.keys(catalogs) as Locale[];

type Leaves<T, P extends string = ""> = {
  [K in keyof T & string]: T[K] extends string ? `${P}${K}` : Leaves<T[K], `${P}${K}.`>;
}[keyof T & string];

/** Every valid key, e.g. "checkout.placeOrder". */
export type MessageKey = Leaves<Messages>;

export function isLocale(value: string | null | undefined): value is Locale {
  return !!value && value in catalogs;
}

function lookup(messages: unknown, key: string): string | undefined {
  let node: unknown = messages;
  for (const part of key.split(".")) {
    if (node && typeof node === "object" && part in (node as Record<string, unknown>)) {
      node = (node as Record<string, unknown>)[part];
    } else {
      return undefined;
    }
  }
  return typeof node === "string" ? node : undefined;
}

export function translate(locale: Locale, key: MessageKey, vars?: Record<string, string | number>): string {
  const template = lookup(catalogs[locale], key) ?? lookup(catalogs[defaultLocale], key) ?? key;
  if (!vars) return template;
  return template.replace(/\{(\w+)\}/g, (match, name: string) => (name in vars ? String(vars[name]) : match));
}

/** Locale-aware number/date formatting (Intl). */
export function formatNumber(locale: Locale, value: number, options?: Intl.NumberFormatOptions): string {
  return new Intl.NumberFormat(locale, options).format(value);
}

export function formatDate(locale: Locale, value: string | number | Date, options?: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat(locale, options).format(new Date(value));
}
