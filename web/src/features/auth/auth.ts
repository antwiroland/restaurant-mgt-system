export type StaffRole = "ADMIN" | "MANAGER" | "CASHIER";

export function routeByRole(role: StaffRole): string {
  if (role === "ADMIN") return "/dashboard";
  if (role === "CASHIER") return "/pos";
  return "/dashboard";
}

export function shouldRedirectToLogin(token: string | null | undefined): boolean {
  return !token;
}

export function shouldRefreshToken(expiresAtEpochMs: number, nowEpochMs: number): boolean {
  return expiresAtEpochMs - nowEpochMs <= 30_000;
}
