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

export const STAR_MEANINGS: Record<number, string> = {
  1: 'Mag ich überhaupt nicht',
  2: 'Esse ich ungern',
  3: 'Ist okay',
  4: 'Esse ich gerne',
  5: 'Lieblingsessen',
};

export function starLabel(value: number): string {
  const meaning = STAR_MEANINGS[value];
  const unit = value === 1 ? 'Stern' : 'Sterne';
  return meaning ? `${value} ${unit}, ${meaning}` : `${value} ${unit}`;
}

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
