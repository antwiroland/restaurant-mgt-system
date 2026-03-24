import { apiClient } from './client';
import type { MenuCategory, MenuItem } from '../types/api';

type MenuCategoryResponse = Omit<MenuCategory, 'items'>;

type MenuItemResponse = {
  id: string;
  categoryId: string;
  categoryName: string;
  name: string;
  description?: string;
  price: string;
  imageUrl?: string;
  available: boolean;
  active: boolean;
};

function toMenuItem(item: MenuItemResponse): MenuItem {
  return {
    id: item.id,
    categoryId: item.categoryId,
    name: item.name,
    description: item.description ?? '',
    price: String(item.price),
    imageUrl: item.imageUrl,
    available: item.available,
  };
}

export async function fetchCategories(): Promise<MenuCategory[]> {
  const [{ data: categories }, { data: items }] = await Promise.all([
    apiClient.get<MenuCategoryResponse[]>('/menu/categories'),
    apiClient.get<MenuItemResponse[]>('/menu/items'),
  ]);

  const itemsByCategory = items.reduce<Record<string, MenuItem[]>>((acc, item) => {
    const bucket = acc[item.categoryId] ?? [];
    bucket.push(toMenuItem(item));
    acc[item.categoryId] = bucket;
    return acc;
  }, {});

  return categories.map((category) => ({
    ...category,
    description: category.description ?? undefined,
    items: itemsByCategory[category.id] ?? [],
  }));
}

export async function fetchMenuItems(params?: { categoryId?: string; available?: boolean; q?: string }): Promise<MenuItem[]> {
  const { data } = await apiClient.get<MenuItemResponse[]>('/menu/items', { params });
  return data.map(toMenuItem);
}
