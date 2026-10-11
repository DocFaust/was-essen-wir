import { request } from '../http';
import type { DishDraft, DraftKind } from './draft';
import type { DishDetail, DishQuery, DishSummary, DishWrite, VariantWrite } from './types';

export { ApiError } from '../http';

export function listDishes(query: DishQuery): Promise<DishSummary[]> {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value) {
      params.set(key, value);
    }
  }
  const suffix = params.size > 0 ? `?${params.toString()}` : '';
  return request<DishSummary[]>(`/api/dishes${suffix}`);
}

export function getDish(id: string): Promise<DishDetail> {
  return request<DishDetail>(`/api/dishes/${id}`);
}

export function createDish(body: DishWrite): Promise<DishDetail> {
  return request<DishDetail>('/api/dishes', { method: 'POST', body: JSON.stringify(body) });
}

export function updateDish(id: string, body: DishWrite): Promise<DishDetail> {
  return request<DishDetail>(`/api/dishes/${id}`, { method: 'PUT', body: JSON.stringify(body) });
}

export function deleteDish(id: string): Promise<void> {
  return request<void>(`/api/dishes/${id}`, { method: 'DELETE' });
}

export function createVariant(dishId: string, body: VariantWrite): Promise<DishDetail> {
  return request<DishDetail>(`/api/dishes/${dishId}/variants`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function updateVariant(dishId: string, variantId: string, body: VariantWrite): Promise<DishDetail> {
  return request<DishDetail>(`/api/dishes/${dishId}/variants/${variantId}`, {
    method: 'PUT',
    body: JSON.stringify(body),
  });
}

export function deleteVariant(dishId: string, variantId: string): Promise<DishDetail> {
  return request<DishDetail>(`/api/dishes/${dishId}/variants/${variantId}`, { method: 'DELETE' });
}

export function createDishDraft(body: {
  description: string;
  kind: DraftKind;
  baseDishId: string | null;
}): Promise<DishDraft> {
  return request<DishDraft>('/api/ai/dish-drafts', { method: 'POST', body: JSON.stringify(body) });
}

export type RatingValue = {
  stars: number | null;
};

export function setDishRating(id: string, stars: number): Promise<RatingValue> {
  return request<RatingValue>(`/api/dishes/${id}/rating`, {
    method: 'PUT',
    body: JSON.stringify({ stars }),
  });
}

export function clearDishRating(id: string): Promise<void> {
  return request<void>(`/api/dishes/${id}/rating`, { method: 'DELETE' });
}

export function setVariantRating(dishId: string, variantId: string, stars: number): Promise<RatingValue> {
  return request<RatingValue>(`/api/dishes/${dishId}/variants/${variantId}/rating`, {
    method: 'PUT',
    body: JSON.stringify({ stars }),
  });
}

export function clearVariantRating(dishId: string, variantId: string): Promise<void> {
  return request<void>(`/api/dishes/${dishId}/variants/${variantId}/rating`, { method: 'DELETE' });
}

