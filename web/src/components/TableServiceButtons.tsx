"use client";

import { Client } from "@stomp/stompjs";
import { useCallback, useEffect, useState } from "react";
import { Icon } from "@/components/Icon";
import { useT } from "@/components/I18nProvider";
import { createTableServiceRequest, getTableServiceRequests, type ServiceRequestRecord, type ServiceRequestType } from "@/lib/apiClient";

/** Guest-side "call a waiter" / "ask for the bill" with live status for the table. */
export function TableServiceButtons({ token }: { token: string }) {
  const t = useT();
  const [requests, setRequests] = useState<ServiceRequestRecord[]>([]);
  const [sending, setSending] = useState<ServiceRequestType | null>(null);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      setRequests(await getTableServiceRequests(token));
    } catch {
      /* buttons still work */
    }
  }, [token]);

  useEffect(() => {
    const first = window.setTimeout(() => void load(), 0);
    const client = new Client({
      brokerURL: process.env.NEXT_PUBLIC_BACKEND_WS_URL ?? "ws://localhost:8080/ws",
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/public.tables.${token}.requests`, () => void load());
      },
    });
    client.activate();
    return () => {
      window.clearTimeout(first);
      void client.deactivate();
    };
  }, [load, token]);

  async function send(type: ServiceRequestType) {
    setSending(type);
    setError("");
    try {
      await createTableServiceRequest(token, type);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not send the request");
    } finally {
      setSending(null);
    }
  }

  const button = (type: ServiceRequestType, icon: "bell" | "receipt", label: string) => {
    const current = requests.find((r) => r.type === type && r.status !== "RESOLVED");
    return (
      <button
        type="button"
        className="btn btn-secondary btn-md flex-1"
        disabled={sending !== null || current?.status === "OPEN"}
        onClick={() => void send(type)}
      >
        <Icon name={current ? "checkCircle" : icon} size={18} />
        {current ? (current.status === "ACKNOWLEDGED" ? t("service.onTheWay") : t("service.requested")) : label}
      </button>
    );
  };

  return (
    <section className="rounded-2xl border border-line bg-surface p-3 shadow-sm" aria-label="Table service">
      <div className="flex gap-2">
        {button("CALL_WAITER", "bell", t("service.callWaiter"))}
        {button("BILL", "receipt", t("service.askBill"))}
      </div>
      {error ? <p className="field-error mt-2" role="alert">{error}</p> : null}
    </section>
  );
}
