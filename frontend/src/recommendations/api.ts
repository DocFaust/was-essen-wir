import { request } from '../http';
import type { Member, RecommendationRequest, RecommendationResponse } from './types';

export function listMembers(): Promise<Member[]> {
  return request<Member[]>('/api/members');
}

export function recommend(input: RecommendationRequest): Promise<RecommendationResponse> {
  return request<RecommendationResponse>('/api/recommendations', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}
