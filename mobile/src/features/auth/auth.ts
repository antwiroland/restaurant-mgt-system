export type RegistrationResult = { otpSent: boolean; pending: boolean };

export function registerPhone(phone: string): RegistrationResult {
  const normalized = phone.trim();
  if (!normalized.startsWith("+")) {
    throw new Error("Invalid phone");
  }
  return { otpSent: true, pending: true };
}

export function verifyOtp(code: string, expected: string): { tokenIssued: boolean; loggedIn: boolean } {
  if (code !== expected) {
    return { tokenIssued: false, loggedIn: false };
  }
  return { tokenIssued: true, loggedIn: true };
}

export function createGuestSessionToken(): { sessionTokenUsed: boolean; role: null } {
  return { sessionTokenUsed: true, role: null };
}
