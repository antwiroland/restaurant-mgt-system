import { apiClient } from './client';
import type { Reservation, CreateReservationRequest } from '../types/api';

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
