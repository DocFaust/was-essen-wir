import type { MealType } from '../dishes/types';

export type MealStatus = 'PLANNED' | 'EATEN';

export type MealParticipant = {
  userId: string | null;
  displayName: string;
};

export type MealEvent = {
  id: string;
  mealDate: string;
  mealType: MealType;
  status: MealStatus;
  dishId: string | null;
  variantId: string | null;
  dishName: string;
  variantName: string | null;
  name: string;
  comment: string | null;
  participants: MealParticipant[];
  createdAt: string;
  updatedAt: string;
};

export type MealEventWrite = {
  mealDate: string;
  mealType: MealType;
  dishId: string;
  variantId: string | null;
  dinerIds: string[];
  comment: string | null;
};

export type MealDraft = {
  mode: 'plan' | 'eaten' | 'edit-plan' | 'edit-eaten' | 'move-plan';
  eventId?: string;
  mealDate: string;
  mealType: MealType;
  dishId?: string;
  dishName?: string;
  variantId?: string | null;
  dinerIds: string[];
  comment?: string;
  participants?: MealParticipant[];
};
