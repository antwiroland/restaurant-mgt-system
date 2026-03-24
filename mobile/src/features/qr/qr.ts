export function scanQr(payload: string): { valid: boolean; tableId?: string; error?: string } {
  if (!payload.startsWith("table:")) {
    return { valid: false, error: "Invalid QR" };
  }
  return { valid: true, tableId: payload.replace("table:", "") };
}
