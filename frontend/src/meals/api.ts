import { request } from '../http';
import type { MealEvent, MealEventWrite } from './types';

export function listPlans(from: string, to: string): Promise<MealEvent[]> {
  return request<MealEvent[]>(`/api/meal-plans?from=${from}&to=${to}`);
}

export function createPlan(body: MealEventWrite): Promise<MealEvent> {
  return request<MealEvent>('/api/meal-plans', { method: 'POST', body: JSON.stringify(body) });
}

export function updatePlan(id: string, body: MealEventWrite): Promise<MealEvent> {
  return request<MealEvent>(`/api/meal-plans/${id}`, { method: 'PUT', body: JSON.stringify(body) });
}

export function deletePlan(id: string): Promise<void> {
  return request<void>(`/api/meal-plans/${id}`, { method: 'DELETE' });
}

export function confirmPlan(id: string): Promise<MealEvent> {
  return request<MealEvent>(`/api/meal-plans/${id}/eaten`, { method: 'POST' });
}

export function listHistory(from: string, to: string, dinerId?: string): Promise<MealEvent[]> {
  const diner = dinerId ? `&dinerId=${encodeURIComponent(dinerId)}` : '';
  return request<MealEvent[]>(`/api/meal-history?from=${from}&to=${to}${diner}`);
}

export function createHistory(body: MealEventWrite): Promise<MealEvent> {
  return request<MealEvent>('/api/meal-history', { method: 'POST', body: JSON.stringify(body) });
}

export function updateHistory(id: string, body: MealEventWrite): Promise<MealEvent> {
  return request<MealEvent>(`/api/meal-history/${id}`, { method: 'PUT', body: JSON.stringify(body) });
}

export function deleteHistory(id: string): Promise<void> {
  return request<void>(`/api/meal-history/${id}`, { method: 'DELETE' });
}
