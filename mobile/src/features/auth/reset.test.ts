import { describe, expect, test } from 'vitest';
import { extractResetToken } from './auth';

describe('Reset link parsing', () => {
  test('givenFullResetLink_whenParsed_thenTokenReturned', () => {
    expect(extractResetToken('https://shop.example/reset-password?token=Ab9_-xY')).toBe('Ab9_-xY');
    expect(extractResetToken('Reset here: https://shop.example/reset-password?foo=1&token=Zz12 thanks')).toBe('Zz12');
  });

  test('givenBareToken_whenParsed_thenReturnedTrimmed', () => {
    expect(extractResetToken('  Ab9_-xY  ')).toBe('Ab9_-xY');
  });

  test('givenEmptyOrJunk_whenParsed_thenEmpty', () => {
    expect(extractResetToken('')).toBe('');
    expect(extractResetToken('not a link at all')).toBe('');
  });
});
