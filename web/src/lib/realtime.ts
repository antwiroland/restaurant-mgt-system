"use client";

import { Client, type StompConfig } from "@stomp/stompjs";
import { useCallback, useEffect, useRef } from "react";
import { useStaffSession } from "@/components/SessionProvider";

export const REALTIME_URL = process.env.NEXT_PUBLIC_BACKEND_WS_URL ?? "ws://localhost:8080/ws";

/**
 * A STOMP client for the live-update socket. Pass `getToken` on staff screens: the current access token is sent on
 * every (re)connect, so staff topics are allowed and a refreshed token is picked up after a reconnect. Public screens
 * (table QR, bill, pickup and table boards) leave it out and only use public topics.
 */
export function createRealtimeClient(config: StompConfig, getToken?: () => string | null | undefined): Client {
  const client = new Client({ brokerURL: REALTIME_URL, reconnectDelay: 5000, ...config });
  if (getToken) {
    client.beforeConnect = () => {
      const token = getToken();
      client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
    };
  }
  return client;
}

/** A stable getter for the signed-in staff member's latest access token (for {@link createRealtimeClient}). */
export function useAccessTokenGetter(): () => string | undefined {
  const { session } = useStaffSession();
  const token = useRef(session?.accessToken);
  useEffect(() => {
    token.current = session?.accessToken;
  }, [session?.accessToken]);
  return useCallback(() => token.current, []);
}
