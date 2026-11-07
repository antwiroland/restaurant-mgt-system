export type OtpPurpose = 'REGISTER' | 'LOGIN';

/** Normalises a phone number for the API; throws when it cannot be a valid mobile number. */
export function normalizePhone(phone: string): string {
  const compact = phone.trim().replace(/[\s-]/g, '');
  if (!/^\+?[0-9]{9,15}$/.test(compact)) {
    throw new Error('Enter a valid phone number, e.g. +233201234567');
  }
  return compact;
}

export function isValidOtpCode(code: string): boolean {
  return /^[0-9]{4,8}$/.test(code.trim());
}

/** Seconds left before another code may be requested. */
export function resendSecondsLeft(requestedAtMs: number | null, cooldownSeconds: number, nowMs = Date.now()): number {
  if (requestedAtMs === null) return 0;
  return Math.max(0, Math.ceil(cooldownSeconds - (nowMs - requestedAtMs) / 1000));
}

export function createGuestSessionToken(): { sessionTokenUsed: boolean; role: null } {
  return { sessionTokenUsed: true, role: null };
}

/**
 * Accepts the whole reset link from the email or SMS (…/reset-password?token=abc) or just the token,
 * and returns the token, or '' when nothing usable was pasted.
 */
export function extractResetToken(input: string): string {
  const text = input.trim();
  if (!text) return '';
  const match = /[?&]token=([^&\s#]+)/.exec(text);
  if (match) {
    try {
      return decodeURIComponent(match[1]);
    } catch {
      return match[1];
    }
  }
  return /^[A-Za-z0-9._~-]+$/.test(text) ? text : '';
}
