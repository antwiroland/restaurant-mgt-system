/** The name and phone a guest gives on the table QR page (no account, no app). Kept on this device only. */
export type GuestContact = { name: string; phone: string };

const STORAGE_KEY = "rm-guest-contact";

/** Same rule as the server: an optional +, then 9 to 20 digits, spaces, brackets or dashes, with at least 9 digits. */
export function isValidGuestPhone(phone: string): boolean {
  const trimmed = phone.trim();
  return /^\+?[0-9 ()-]{9,20}$/.test(trimmed) && trimmed.replace(/\D/g, "").length >= 9;
}

export function loadGuestContact(): GuestContact | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<GuestContact>;
    if (typeof parsed.phone !== "string" || !isValidGuestPhone(parsed.phone)) return null;
    return { name: typeof parsed.name === "string" ? parsed.name : "", phone: parsed.phone };
  } catch {
    return null;
  }
}

export function saveGuestContact(contact: GuestContact): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(contact));
  } catch {
    // Private mode or blocked storage: the guest is asked again next visit.
  }
}
