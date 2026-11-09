import en, { type Messages } from './en';

/**
 * Localization framework (English only for now).
 *
 * Adding a language: copy en.ts to <code>.ts, translate the values (TypeScript checks
 * every key is present), register it in `catalogs`, then call setLocale('<code>').
 * Missing keys fall back to English, then to the key itself.
 */
const catalogs = { en } satisfies Record<string, Messages>;

export type Locale = keyof typeof catalogs;
export const locales = Object.keys(catalogs) as Locale[];
export const defaultLocale: Locale = 'en';

type Leaves<T, P extends string = ''> = {
  [K in keyof T & string]: T[K] extends string ? `${P}${K}` : Leaves<T[K], `${P}${K}.`>;
}[keyof T & string];
export type MessageKey = Leaves<Messages>;

let current: Locale = defaultLocale;

export function setLocale(locale: Locale): void {
  current = locale;
}

export function getLocale(): Locale {
  return current;
}

function lookup(messages: unknown, key: string): string | undefined {
  let node: unknown = messages;
  for (const part of key.split('.')) {
    if (node && typeof node === 'object' && part in (node as Record<string, unknown>)) {
      node = (node as Record<string, unknown>)[part];
    } else {
      return undefined;
    }
  }
  return typeof node === 'string' ? node : undefined;
}

export function t(key: MessageKey, vars?: Record<string, string | number>): string {
  const template = lookup(catalogs[current], key) ?? lookup(catalogs[defaultLocale], key) ?? key;
  if (!vars) return template;
  return template.replace(/\{(\w+)\}/g, (match, name: string) => (name in vars ? String(vars[name]) : match));
}
