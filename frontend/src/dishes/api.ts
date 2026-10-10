import type { DishDetail, DishQuery, DishSummary, DishWrite, VariantWrite } from './types';

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(message: string, status: number, code?: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

const UNREACHABLE = 'Das Backend ist gerade nicht erreichbar.';

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

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(path, {
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
        ...init?.headers,
      },
    });
  } catch {
    throw new ApiError(UNREACHABLE, 0);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  let payload: { detail?: string; code?: string } = {};
  if (text) {
    try {
      payload = JSON.parse(text) as { detail?: string; code?: string };
    } catch {
      throw new ApiError(
        response.ok ? 'Die Antwort konnte nicht gelesen werden.' : 'Die Anfrage ist fehlgeschlagen.',
        response.status,
      );
    }
  }

  if (!response.ok) {
    throw new ApiError(payload.detail ?? 'Die Anfrage ist fehlgeschlagen.', response.status, payload.code);
  }

  return payload as T;
}
