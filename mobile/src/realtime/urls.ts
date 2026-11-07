/** ws(s)://host/ws derived from the REST base URL. */
export function websocketUrl(baseUrl: string): string {
  return baseUrl.replace(/\/+$/, '').replace(/^http/i, 'ws') + '/ws';
}

/** Polling interval to use as a fallback: slow when live updates are flowing, faster otherwise. */
export function fallbackPollMs(connected: boolean, disconnectedMs: number): number {
  return connected ? Math.max(disconnectedMs * 4, 60000) : disconnectedMs;
}
