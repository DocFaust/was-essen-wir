import { expect, test } from 'vitest';
import { isoDate, startOfWeek, weekLabel } from './dates';

test('beginnt die Woche am Montag und bleibt im lokalen Kalender', () => {
  expect(isoDate(startOfWeek(new Date(2026, 9, 10)))).toBe('2026-10-05');
  expect(isoDate(startOfWeek(new Date(2026, 9, 11)))).toBe('2026-10-05');
  expect(isoDate(startOfWeek(new Date(2026, 9, 12)))).toBe('2026-10-12');
  expect(weekLabel(new Date(2026, 9, 5))).toBe('5.–11. Oktober 2026');
});
