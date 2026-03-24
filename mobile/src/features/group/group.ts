export type GroupParticipant = { id: string; items: number };

export function joinSharedCart(participants: GroupParticipant[], next: GroupParticipant): GroupParticipant[] {
  return [...participants, next];
}

export function groupTotal(participants: GroupParticipant[]): number {
  return participants.reduce((sum, p) => sum + p.items, 0);
}

export function submitGroupByHost(isHost: boolean): { singleOrderCreated: boolean; allNotified: boolean } {
  if (!isHost) return { singleOrderCreated: false, allNotified: false };
  return { singleOrderCreated: true, allNotified: true };
}
