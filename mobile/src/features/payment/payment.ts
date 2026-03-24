export type PaymentMethod = "MOBILE_MONEY" | "CARD";

export function defaultMethod(): PaymentMethod {
  return "MOBILE_MONEY";
}

export function pendingState(): string {
  return "waiting for MoMo approval";
}

export function handlePaystackEvent(status: "success" | "failed"): "TRACKING" | "FAILURE" {
  return status === "success" ? "TRACKING" : "FAILURE";
}

export function retryFailedPayment(): { initiated: boolean } {
  return { initiated: true };
}
