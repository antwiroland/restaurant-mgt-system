import { apiClient } from './client';
import type { Reservation, CreateReservationRequest, GuestReservationLookupRequest } from '../types/api';

export async function createReservation(req: CreateReservationRequest): Promise<Reservation> {
  const { data } = await apiClient.post<Reservation>('/reservations', req);
  return data;
}

export async function fetchMyReservations(): Promise<Reservation[]> {
  const { data } = await apiClient.get<Reservation[]>('/reservations/mine');
  return data;
}

export async function cancelReservation(id: string): Promise<void> {
  await apiClient.delete(`/reservations/${id}`);
}

export async function fetchGuestReservations(request: GuestReservationLookupRequest): Promise<Reservation[]> {
  const { data } = await apiClient.post<Reservation[]>('/reservations/guest/lookup', request);
  return data;
}

export async function cancelGuestReservation(id: string, phone: string): Promise<void> {
  await apiClient.post(`/reservations/${id}/guest-cancel`, { phone });
}
