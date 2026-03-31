import { describe, expect, test } from 'vitest';
import { normalizeBaseUrl } from './client';

describe('normalizeBaseUrl', () => {
  test('removes trailing slash from backend root URL', () => {
    expect(normalizeBaseUrl('http://10.0.2.2:8080/')).toBe('http://10.0.2.2:8080');
  });

  test('strips legacy api v1 suffix from configured base URL', () => {
    expect(normalizeBaseUrl('http://10.0.2.2:8080/api/v1')).toBe('http://10.0.2.2:8080');
  });
});
