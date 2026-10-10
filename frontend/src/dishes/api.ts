import { request } from '../http';
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

