export type Member = {
  id: string;
  displayName: string;
};

export type Suggestion = {
  dishId: string;
  variantId: string | null;
  name: string;
  dishName: string;
  variantName: string | null;
  reason: string;
  effort: 'EINFACH' | 'MITTEL' | 'AUFWENDIG' | null;
  mealPrep: 'UNKNOWN' | 'SUITABLE' | 'UNSUITABLE';
  homeOffice: 'UNKNOWN' | 'SUITABLE' | 'UNSUITABLE';
};

export type RecommendationResponse = {
  suggestions: Suggestion[];
  explanation: string | null;
};

export type RecommendationRequest = {
  mealType: string;
  dinerIds: string[];
  preferSimple: boolean;
  preferHomeOffice: boolean;
  preferMealPrep: boolean;
  excludedDishIds: string[];
  seed: number;
};
