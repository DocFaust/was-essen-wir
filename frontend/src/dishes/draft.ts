import type { Effort, MealType } from './types';

export type DraftKind = 'DISH' | 'VARIANT';
export type DraftOutcome = 'DRAFT' | 'NEEDS_BASE_DISH';

export type SimilarDish = {
  dishId: string;
  variantId: string | null;
  name: string;
  message: string;
};

export type BaseDishChoice = {
  id: string;
  name: string;
};

export type DishDraft = {
  schemaVersion: number;
  outcome: DraftOutcome;
  kind: DraftKind;
  baseDishId: string | null;
  baseDishName: string | null;
  name: string | null;
  description: string | null;
  mealTypes: MealType[];
  categories: string[];
  effort: Effort | null;
  ingredients: string[];
  ingredientsSpecified: boolean | null;
  recipeText: string | null;
  recipeUrl: string | null;
  recipeSource: string | null;
  comments: string | null;
  mealPrep: boolean | null;
  homeOffice: boolean | null;
  recognized: string[];
  unknown: string[];
  warnings: string[];
  duplicates: SimilarDish[];
  candidates: BaseDishChoice[];
};

export const DRAFT_FIELD_LABELS: Record<string, string> = {
  name: 'Name',
  description: 'Beschreibung',
  mealTypes: 'Mahlzeiten',
  categories: 'Kategorien',
  effort: 'Aufwand',
  ingredients: 'Zutaten',
  recipeText: 'Rezept',
  recipeUrl: 'Rezeptlink',
  recipeSource: 'Rezeptquelle',
  comments: 'Kommentar',
  mealPrep: 'Meal Prep',
  homeOffice: 'Homeoffice',
};
