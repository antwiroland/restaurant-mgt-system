import { apiClient } from './client';
import type { MenuItem } from '../types/api';

export async function fetchFavourites(): Promise<MenuItem[]> {
  const { data } = await apiClient.get<MenuItem[]>('/users/me/favourites');
  return data;
}

export async function setFavourite(menuItemId: string, favourite: boolean): Promise<void> {
  if (favourite) {
    await apiClient.put(`/users/me/favourites/${menuItemId}`);
  } else {
    await apiClient.delete(`/users/me/favourites/${menuItemId}`);
  }
}
