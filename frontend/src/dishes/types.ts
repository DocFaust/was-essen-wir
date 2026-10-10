export type MealType = 'FRUEHSTUECK' | 'MITTAGESSEN' | 'ABENDESSEN' | 'SNACK';
export type Effort = 'EINFACH' | 'MITTEL' | 'AUFWENDIG';
export type Suitability = 'UNKNOWN' | 'SUITABLE' | 'UNSUITABLE';
export type SuitabilitySetting = 'INHERIT' | 'SUITABLE' | 'UNSUITABLE';

export type ResolvedProperties = {
  description: string | null;
  descriptionInherited: boolean;
  effort: Effort | null;
  effortInherited: boolean;
  ingredients: string[];
  ingredientsInherited: boolean;
  recipeText: string | null;
  recipeTextInherited: boolean;
  recipeUrl: string | null;
  recipeUrlInherited: boolean;
  recipeSource: string | null;
  recipeSourceInherited: boolean;
  mealPrep: Suitability;
  mealPrepInherited: boolean;
  homeOffice: Suitability;
  homeOfficeInherited: boolean;
};

export type DishVariant = {
  id: string;
  name: string;
  description: string | null;
  effort: Effort | null;
  ingredientsSpecified: boolean;
  ingredients: string[];
  recipeText: string | null;
  recipeUrl: string | null;
  recipeSource: string | null;
  mealPrep: SuitabilitySetting;
  homeOffice: SuitabilitySetting;
  resolved: ResolvedProperties;
  myRating: number | null;
};

export type DishDetail = {
  id: string;
  name: string;
  description: string | null;
  mealTypes: MealType[];
  categories: string[];
  effort: Effort | null;
  ingredients: string[];
  recipeText: string | null;
  recipeUrl: string | null;
  recipeSource: string | null;
  comments: string | null;
  mealPrep: Suitability;
  homeOffice: Suitability;
  myRating: number | null;
  variants: DishVariant[];
};

export type DishSummary = {
  id: string;
  name: string;
  mealTypes: MealType[];
  effort: Effort | null;
  mealPrep: Suitability;
  homeOffice: Suitability;
  variantNames: string[];
  myRating: number | null;
};

export type DishWrite = {
  name: string;
  description: string | null;
  mealTypes: MealType[];
  categories: string[];
  effort: Effort | null;
  ingredients: string[];
  recipeText: string | null;
  recipeUrl: string | null;
  recipeSource: string | null;
  comments: string | null;
  mealPrep: Suitability;
  homeOffice: Suitability;
  confirmDuplicate: boolean;
};

export type VariantWrite = {
  name: string;
  description: string | null;
  effort: Effort | null;
  ingredientsSpecified: boolean;
  ingredients: string[];
  recipeText: string | null;
  recipeUrl: string | null;
  recipeSource: string | null;
  mealPrep: SuitabilitySetting;
  homeOffice: SuitabilitySetting;
};

export type DishQuery = {
  q?: string;
  mealType?: string;
  effort?: string;
  mealPrep?: string;
  homeOffice?: string;
};
