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
