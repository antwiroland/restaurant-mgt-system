import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import { useEffect, useRef, useState } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { BASE_URL } from '../api/client';
import { fallbackPollMs, websocketUrl } from './urls';

export { fallbackPollMs, websocketUrl };

type Listener = (payload: unknown) => void;

/**
 * One shared STOMP connection for the app. Subscriptions are reference counted and re-established
 * automatically after reconnects; screens keep a slow polling fallback while disconnected.
 */
class RealtimeConnection {
  private client: Client | null = null;
  private readonly listeners = new Map<string, Set<Listener>>();
  private readonly subscriptions = new Map<string, StompSubscription>();
  private readonly statusListeners = new Set<(connected: boolean) => void>();
  connected = false;

  subscribe(topic: string, listener: Listener): () => void {
    let set = this.listeners.get(topic);
    if (!set) {
      set = new Set();
      this.listeners.set(topic, set);
    }
    set.add(listener);
    this.ensureClient();
    if (this.connected) this.attach(topic);

    return () => {
      const current = this.listeners.get(topic);
      current?.delete(listener);
      if (current && current.size === 0) {
        this.listeners.delete(topic);
        this.subscriptions.get(topic)?.unsubscribe();
        this.subscriptions.delete(topic);
      }
      if (this.listeners.size === 0) this.shutdown();
    };
  }

  onStatus(listener: (connected: boolean) => void): () => void {
    this.statusListeners.add(listener);
    listener(this.connected);
    return () => this.statusListeners.delete(listener);
  }

  private ensureClient() {
    if (this.client) return;
    const client = new Client({
      brokerURL: websocketUrl(BASE_URL),
      reconnectDelay: 5000,
      heartbeatIncoming: 20000,
      heartbeatOutgoing: 20000,
      // React Native WebSocket quirks
      forceBinaryWSFrames: true,
      appendMissingNULLonIncoming: true,
    });
    // Signed-in customers send their access token so they can follow their own orders and group carts; guests
    // connect anonymously and only use public table topics. Read on every (re)connect to pick up refreshed tokens.
    client.beforeConnect = async () => {
      const token = await AsyncStorage.getItem('accessToken').catch(() => null);
      client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
    };
    client.onConnect = () => {
      this.subscriptions.clear();
      this.setConnected(true);
      for (const topic of this.listeners.keys()) this.attach(topic);
    };
    client.onWebSocketClose = () => this.setConnected(false);
    client.onStompError = () => this.setConnected(false);
    this.client = client;
    client.activate();
  }

  private attach(topic: string) {
    if (!this.client || this.subscriptions.has(topic)) return;
    const subscription = this.client.subscribe(topic, (message: IMessage) => {
      let payload: unknown = message.body;
      try {
        payload = JSON.parse(message.body);
      } catch {
        // keep raw body
      }
      this.listeners.get(topic)?.forEach((listener) => listener(payload));
    });
    this.subscriptions.set(topic, subscription);
  }

  private setConnected(connected: boolean) {
    if (this.connected === connected) return;
    this.connected = connected;
    this.statusListeners.forEach((listener) => listener(connected));
  }

  private shutdown() {
    const client = this.client;
    this.client = null;
    this.subscriptions.clear();
    this.setConnected(false);
    void client?.deactivate();
  }
}

export const realtime = new RealtimeConnection();

/**
 * Subscribes to the given STOMP topics while mounted. Returns whether the live connection is up so callers
 * can relax their polling fallback.
 */
export function useRealtimeTopics(topics: (string | null | undefined)[], onMessage: (payload: unknown, topic: string) => void): boolean {
  const [connected, setConnected] = useState(realtime.connected);
  const handlerRef = useRef(onMessage);
  handlerRef.current = onMessage;
  const key = topics.filter(Boolean).join('|');

  useEffect(() => realtime.onStatus(setConnected), []);

  useEffect(() => {
    const active = key ? key.split('|') : [];
    const unsubscribers = active.map((topic) => realtime.subscribe(topic, (payload) => handlerRef.current(payload, topic)));
    return () => unsubscribers.forEach((unsubscribe) => unsubscribe());
  }, [key]);

  return connected;
}
