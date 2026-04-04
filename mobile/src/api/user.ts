import { apiClient } from './client';
import type {
  CurrentUser,
  CustomerAddress,
  CustomerAddressRequest,
  CustomerProfileUpdateRequest,
} from '../types/api';

export async function fetchCurrentUser(): Promise<CurrentUser> {
  const { data } = await apiClient.get<CurrentUser>('/users/me');
  return data;
}

export async function updateCurrentUser(payload: CustomerProfileUpdateRequest): Promise<CurrentUser> {
  const { data } = await apiClient.patch<CurrentUser>('/users/me', payload);
  return data;
}

export async function fetchMyAddresses(): Promise<CustomerAddress[]> {
  const { data } = await apiClient.get<CustomerAddress[]>('/users/me/addresses');
  return data;
}

export async function saveMyAddress(payload: CustomerAddressRequest): Promise<CustomerAddress> {
  const { data } = await apiClient.post<CustomerAddress>('/users/me/addresses', payload);
  return data;
}
