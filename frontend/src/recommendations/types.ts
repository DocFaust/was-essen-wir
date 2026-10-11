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

export type AiStatus = {
  enabled: boolean;
  provider: 'ollama' | 'openai' | 'fixture' | null;
};

export type InterpretedCriteria = {
  mealType: 'FRUEHSTUECK' | 'MITTAGESSEN' | 'ABENDESSEN' | 'SNACK';
  date: string;
  dinerIds: string[];
  participantNames: string[];
  preferSimple: boolean;
  preferHomeOffice: boolean;
  preferMealPrep: boolean;
  dishSearchTerms: string[];
  avoidDishTerms: string[];
  matchedDishes: string[];
  limitations: string[];
};

export type AiRecommendationResponse = {
  reply: string;
  clarificationQuestion: string | null;
  criteria: InterpretedCriteria;
  suggestions: Suggestion[];
  explanation: string | null;
};
