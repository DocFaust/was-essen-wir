import type { Effort, MealType, Suitability, SuitabilitySetting } from './types';

export const MEAL_TYPE_LABELS: Record<MealType, string> = {
  FRUEHSTUECK: 'Frühstück',
  MITTAGESSEN: 'Mittagessen',
  ABENDESSEN: 'Abendessen',
  SNACK: 'Snack',
};

export const EFFORT_LABELS: Record<Effort, string> = {
  EINFACH: 'Einfach',
  MITTEL: 'Mittel',
  AUFWENDIG: 'Aufwendig',
};

export const SUITABILITY_LABELS: Record<Suitability, string> = {
  UNKNOWN: 'Nicht geprüft',
  SUITABLE: 'Geeignet',
  UNSUITABLE: 'Nicht geeignet',
};

export const SUITABILITY_SETTING_LABELS: Record<SuitabilitySetting, string> = {
  INHERIT: 'Vom Grundgericht',
  SUITABLE: 'Geeignet',
  UNSUITABLE: 'Nicht geeignet',
};

export const MEAL_TYPES = Object.keys(MEAL_TYPE_LABELS) as MealType[];
export const EFFORTS = Object.keys(EFFORT_LABELS) as Effort[];

export function noticeFrom(state: unknown): string | null {
  if (typeof state !== 'object' || state === null || !('notice' in state)) {
    return null;
  }
  const notice = (state as { notice?: unknown }).notice;
  return typeof notice === 'string' ? notice : null;
}

export function emptyToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}
