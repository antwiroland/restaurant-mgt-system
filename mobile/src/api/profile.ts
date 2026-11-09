import { apiClient } from './client';

export type RestaurantProfile = {
  name: string;
  tagline?: string | null;
  about?: string | null;
  address?: string | null;
  phone?: string | null;
  email?: string | null;
  websiteUrl?: string | null;
  heroImageUrl?: string | null;
  currency: string;
  openingHours: string[];
  latitude?: number | null;
  longitude?: number | null;
};

export async function fetchRestaurantProfile(): Promise<RestaurantProfile> {
  const { data } = await apiClient.get<RestaurantProfile>('/restaurant/profile');
  return data;
}
