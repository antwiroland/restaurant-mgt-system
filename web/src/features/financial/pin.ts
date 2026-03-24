export type PinModalState = "idle" | "error" | "locked" | "success";

export function verifyPin(inputPin: string, expectedPin: string, locked: boolean): PinModalState {
  if (locked) return "locked";
  if (inputPin === expectedPin) return "success";
  return "error";
}
