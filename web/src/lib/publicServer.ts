// Server-only module: imported exclusively from Server Components.
import type { CategoryRecord, MenuItemRecord, MenuModifierGroupRecord, PublicOfferRecord, RestaurantProfileRecord, TablePublicStatusView } from "@/lib/apiClient";
import { brandFromProfile, type Brand } from "@/lib/brand";

/**
 * Server-side reads of PUBLIC backend endpoints for customer pages.
 * Every call fails soft (returns null) so a backend outage renders an
 * honest "menu unavailable" state rather than a crashed page.
 */
const BACKEND_BASE_URL = process.env.RESTAURANT_MANAGER_BACKEND_URL ?? "http://localhost:8080";

async function getJson<T>(path: string): Promise<T | null> {
  try {
    const response = await fetch(`${BACKEND_BASE_URL}${path}`, {
      cache: "no-store",
      signal: AbortSignal.timeout(4000),
    });
    if (!response.ok) return null;
    return (await response.json()) as T;
  } catch {
    return null;
  }
}

export type PublicMenu = {
  items: MenuItemRecord[];
  categories: CategoryRecord[];
};

export async function fetchPublicMenu(): Promise<PublicMenu | null> {
  const [items, categories] = await Promise.all([
    getJson<MenuItemRecord[]>("/menu/items"),
    getJson<CategoryRecord[]>("/menu/categories"),
  ]);
  if (!items || !categories) return null;
  const activeCategories = categories
    .filter((category) => category.active)
    .sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name));
  return {
    items: items.filter((item) => item.active),
    categories: activeCategories,
  };
}

export async function fetchPublicMenuItem(id: string) {
  const [item, modifiers] = await Promise.all([
    getJson<MenuItemRecord>(`/menu/items/${encodeURIComponent(id)}`),
    getJson<MenuModifierGroupRecord[]>(`/menu/items/${encodeURIComponent(id)}/modifiers`),
  ]);
  return item ? { item, modifiers: modifiers ?? [] } : null;
}

/** Offers customers can see, with the dishes they involve. Empty when the backend is unreachable. */
export async function fetchPublicOffers(): Promise<PublicOfferRecord[]> {
  return (await getJson<PublicOfferRecord[]>("/promotions/offers/public")) ?? [];
}

export async function fetchPublicTables(): Promise<TablePublicStatusView[] | null> {
  return getJson<TablePublicStatusView[]>("/tables/public");
}

export async function fetchRestaurantProfile(): Promise<RestaurantProfileRecord | null> {
  return getJson<RestaurantProfileRecord>("/restaurant/profile");
}

export type PublicVacancy = {
  id: string;
  reference: string;
  title: string;
  branchName: string;
  department: string | null;
  employmentType: "FULL_TIME" | "PART_TIME" | "CASUAL" | "CONTRACT" | "TEMPORARY" | "INTERN" | "SEASONAL";
  openings: number;
  closingDate: string | null;
  postedAt: string | null;
  description: string | null;
  requirements: string | null;
};

/** Open jobs for the careers page; null when the backend can't be reached. */
export async function fetchCareers(): Promise<PublicVacancy[] | null> {
  return getJson<PublicVacancy[]>("/careers");
}

export async function fetchCareer(id: string): Promise<PublicVacancy | null> {
  return getJson<PublicVacancy>(`/careers/${encodeURIComponent(id)}`);
}

/** Restaurant identity for server components: the profile from the API, with env values as fallback. */
export async function getBrand(): Promise<Brand> {
  return brandFromProfile(await fetchRestaurantProfile());
}
