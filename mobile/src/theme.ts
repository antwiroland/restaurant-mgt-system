/**
 * Hearth design tokens for the customer app.
 * Mirrors web/src/app/globals.css so web and mobile share one brand.
 * Screens import `colors`, `radius`, `space`, `type` — never raw hex.
 */
export const colors = {
  // Surfaces
  canvas: '#FBF7F0', // warm ivory — screen background
  surface: '#FFFFFF',
  sunken: '#F1EBE1',
  cream: '#F4EAD9',
  overlay: 'rgba(28,21,17,0.55)',

  // Espresso (dark surfaces)
  espresso: '#231A15',
  espressoRaised: '#2F241E',
  espressoInk: '#F6EFE6',
  espressoSoft: '#C2B3A5',

  // Ink
  ink: '#241B16',
  inkSoft: '#65574D',
  inkMuted: '#8A7B70',
  inkDisabled: '#B9ADA2',
  white: '#FFFFFF',
  black: '#000000',

  // Brand — terracotta
  brand: '#B4532A',
  brandPressed: '#9C4521',
  brandSubtle: '#F8EBE3',
  brandSoft: '#EFD3C3',

  // Accent — muted gold
  gold: '#C9A45C',
  goldDeep: '#8F6F2F',
  goldSubtle: '#F6EEDB',

  // Lines
  line: '#E6DDD0',
  lineStrong: '#D3C6B5',

  // Status
  success: '#4F7A4A',
  successSubtle: '#E6EFE2',
  warning: '#B07418',
  warningSubtle: '#FBF0D8',
  warningOn: '#744B0B',
  danger: '#B3261E',
  dangerSubtle: '#F9E4E1',
  dangerOn: '#8A1C16',
  info: '#3D6A87',
  infoSubtle: '#E5EEF4',

  // Order status (matches web badges)
  pending: '#9A6612',
  confirmed: '#2F5F7E',
  preparing: '#A2481F',
  ready: '#24705F',
  completed: '#46693F',
  cancelled: '#A3241C',
} as const;

export const radius = { sm: 6, md: 10, lg: 14, xl: 20, pill: 999 } as const;

export const space = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 } as const;

export const type = {
  display: { fontSize: 28, fontWeight: '800' as const, letterSpacing: -0.5, color: colors.ink },
  title: { fontSize: 20, fontWeight: '800' as const, letterSpacing: -0.3, color: colors.ink },
  heading: { fontSize: 17, fontWeight: '700' as const, color: colors.ink },
  body: { fontSize: 15, color: colors.ink },
  caption: { fontSize: 13, color: colors.inkSoft },
  eyebrow: { fontSize: 11, fontWeight: '700' as const, letterSpacing: 1.6, textTransform: 'uppercase' as const, color: colors.brand },
} as const;

export const shadow = {
  card: {
    shadowColor: '#241B16',
    shadowOpacity: 0.08,
    shadowRadius: 14,
    shadowOffset: { width: 0, height: 6 },
    elevation: 3,
  },
  lifted: {
    shadowColor: '#241B16',
    shadowOpacity: 0.16,
    shadowRadius: 22,
    shadowOffset: { width: 0, height: 10 },
    elevation: 8,
  },
} as const;

/** Status → colour for order badges. */
export const statusColor: Record<string, { fg: string; bg: string }> = {
  PENDING: { fg: colors.pending, bg: colors.warningSubtle },
  CONFIRMED: { fg: colors.confirmed, bg: colors.infoSubtle },
  PREPARING: { fg: colors.preparing, bg: colors.brandSubtle },
  READY: { fg: colors.ready, bg: '#DCEFE9' },
  OUT_FOR_DELIVERY: { fg: colors.info, bg: colors.infoSubtle },
  DELIVERED: { fg: colors.completed, bg: colors.successSubtle },
  COMPLETED: { fg: colors.completed, bg: colors.successSubtle },
  CANCELLED: { fg: colors.cancelled, bg: colors.dangerSubtle },
  VOIDED: { fg: colors.inkSoft, bg: colors.sunken },
};

/** Brand copy — set EXPO_PUBLIC_RESTAURANT_NAME / _TAGLINE to match the web app. */
export const brand = {
  name: process.env.EXPO_PUBLIC_RESTAURANT_NAME ?? 'Hearth',
  tagline: process.env.EXPO_PUBLIC_RESTAURANT_TAGLINE ?? 'Seasonal kitchen & bar',
  currency: process.env.EXPO_PUBLIC_CURRENCY ?? 'GHS',
};

export function money(value: string | number): string {
  const amount = typeof value === 'number' ? value : Number.parseFloat(value);
  return `${brand.currency} ${(Number.isFinite(amount) ? amount : 0).toFixed(2)}`;
}
