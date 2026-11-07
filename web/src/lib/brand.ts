/**
 * Restaurant brand details shown on customer-facing pages.
 *
 * The backend has no "restaurant profile" endpoint yet, so these come from
 * public env vars. Anything left unset is simply not rendered — we never show
 * placeholder addresses, phone numbers or opening hours as if they were real.
 *
 *   NEXT_PUBLIC_RESTAURANT_NAME      e.g. "Hearth"
 *   NEXT_PUBLIC_RESTAURANT_TAGLINE   short line under the name
 *   NEXT_PUBLIC_RESTAURANT_ABOUT     one paragraph for the About section
 *   NEXT_PUBLIC_RESTAURANT_ADDRESS   street address
 *   NEXT_PUBLIC_RESTAURANT_PHONE     public phone number
 *   NEXT_PUBLIC_RESTAURANT_EMAIL     public email
 *   NEXT_PUBLIC_RESTAURANT_HOURS     "Mon–Fri 11:00–22:00|Sat–Sun 10:00–23:00" (pipe-separated)
 *   NEXT_PUBLIC_RESTAURANT_HERO_IMAGE absolute URL of a hero photograph
 *   NEXT_PUBLIC_CURRENCY             display currency code, default "GHS"
 */
function env(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

export const brand = {
  name: env(process.env.NEXT_PUBLIC_RESTAURANT_NAME) ?? "Hearth",
  tagline: env(process.env.NEXT_PUBLIC_RESTAURANT_TAGLINE) ?? "Seasonal kitchen & bar",
  about: env(process.env.NEXT_PUBLIC_RESTAURANT_ABOUT),
  address: env(process.env.NEXT_PUBLIC_RESTAURANT_ADDRESS),
  phone: env(process.env.NEXT_PUBLIC_RESTAURANT_PHONE),
  email: env(process.env.NEXT_PUBLIC_RESTAURANT_EMAIL),
  hours: env(process.env.NEXT_PUBLIC_RESTAURANT_HOURS)?.split("|").map((line) => line.trim()).filter(Boolean) ?? [],
  heroImage: env(process.env.NEXT_PUBLIC_RESTAURANT_HERO_IMAGE),
  currency: env(process.env.NEXT_PUBLIC_CURRENCY) ?? "GHS",
};

export function formatMoney(value: string | number | undefined | null, currency = brand.currency): string {
  const amount = typeof value === "number" ? value : Number(value ?? 0);
  if (!Number.isFinite(amount)) return `${currency} 0.00`;
  return `${currency} ${amount.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

/** Restaurant identity used across the site. `brand` above holds the env fallbacks. */
export type Brand = typeof brand & {
  websiteUrl?: string;
  latitude?: number;
  longitude?: number;
  receiptFooter?: string;
};

type ProfileLike = {
  name?: string | null;
  tagline?: string | null;
  about?: string | null;
  address?: string | null;
  phone?: string | null;
  email?: string | null;
  websiteUrl?: string | null;
  heroImageUrl?: string | null;
  currency?: string | null;
  openingHours?: string[] | null;
  latitude?: number | null;
  longitude?: number | null;
  receiptFooter?: string | null;
};

/** The restaurant profile from the API wins field by field; env values fill any gaps. */
export function brandFromProfile(profile: ProfileLike | null | undefined): Brand {
  if (!profile) return { ...brand };
  const pick = (value: string | null | undefined, fallback: string | undefined) => (value && value.trim() ? value.trim() : fallback);
  return {
    name: pick(profile.name, brand.name) ?? brand.name,
    tagline: pick(profile.tagline, brand.tagline) ?? brand.tagline,
    about: pick(profile.about, brand.about),
    address: pick(profile.address, brand.address),
    phone: pick(profile.phone, brand.phone),
    email: pick(profile.email, brand.email),
    hours: profile.openingHours && profile.openingHours.length > 0 ? profile.openingHours : brand.hours,
    heroImage: pick(profile.heroImageUrl, brand.heroImage),
    currency: pick(profile.currency, brand.currency) ?? brand.currency,
    websiteUrl: profile.websiteUrl ?? undefined,
    latitude: profile.latitude ?? undefined,
    longitude: profile.longitude ?? undefined,
    receiptFooter: profile.receiptFooter ?? undefined,
  };
}
