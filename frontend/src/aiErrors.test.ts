import { expect, test } from 'vitest';
import { aiErrorMessage, aiLimitKind, canRetryAiError } from './aiErrors';
import { ApiError } from './http';

test('unterscheidet Tageslimit und vorübergehende Auslastung', () => {
  const daily = new ApiError('Das Tageslimit ist erreicht.', 429, 'AI_DAILY_LIMIT');
  const minute = new ApiError('Zu oft genutzt.', 429, 'AI_RATE_LIMITED');
  const busy = new ApiError('Ausgelastet.', 429, 'AI_BUSY');
  const other = new ApiError('Fehler', 503, 'AI_UNAVAILABLE');

  expect(aiLimitKind(daily)).toBe('daily');
  expect(aiLimitKind(minute)).toBe('temporary');
  expect(aiLimitKind(busy)).toBe('temporary');
  expect(aiLimitKind(other)).toBeNull();

  expect(canRetryAiError(daily)).toBe(false);
  expect(canRetryAiError(minute)).toBe(true);
  expect(canRetryAiError(busy)).toBe(true);
  expect(aiErrorMessage(daily, 'Fallback')).toBe('Das Tageslimit ist erreicht.');
});
