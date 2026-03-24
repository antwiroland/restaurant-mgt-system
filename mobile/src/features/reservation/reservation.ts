export type Reservation = { id: string; cancelled: boolean };

export function createReservation(slotAvailable: boolean): { confirmationShown: boolean } {
  return { confirmationShown: slotAvailable };
}

export function cancelReservation(reservation: Reservation): { status: number; removedFromList: boolean } {
  return { status: 204, removedFromList: !reservation.cancelled };
}
