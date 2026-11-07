export type StaffRole = "ADMIN" | "MANAGER" | "CASHIER" | "RIDER";

export function routeByRole(role: StaffRole): string {
  if (role === "ADMIN") return "/dashboard";
  if (role === "CASHIER") return "/pos";
  if (role === "RIDER") return "/rider";
  return "/dashboard";
}

export function shouldRedirectToLogin(token: string | null | undefined): boolean {
  return !token;
}

export function shouldRefreshToken(expiresAtEpochMs: number, nowEpochMs: number): boolean {
  return expiresAtEpochMs - nowEpochMs <= 30_000;
}
