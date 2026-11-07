import { describe, expect, test } from 'vitest';
import { fallbackPollMs, websocketUrl } from './urls';

describe('realtime helpers', () => {
  test('givenHttpBaseUrl_whenDerivingSocketUrl_thenWsSchemeAndPath', () => {
    expect(websocketUrl('http://10.0.2.2:8080')).toBe('ws://10.0.2.2:8080/ws');
    expect(websocketUrl('https://api.example.com/')).toBe('wss://api.example.com/ws');
  });

  test('givenLiveConnection_whenPolling_thenFallbackIsSlower', () => {
    expect(fallbackPollMs(false, 15000)).toBe(15000);
    expect(fallbackPollMs(true, 15000)).toBe(60000);
    expect(fallbackPollMs(true, 30000)).toBe(120000);
  });
});
