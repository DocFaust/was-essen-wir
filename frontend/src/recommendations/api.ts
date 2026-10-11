import { request } from '../http';
import type { AiRecommendationResponse, AiStatus, Member, RecommendationRequest, RecommendationResponse } from './types';

export function listMembers(): Promise<Member[]> {
  return request<Member[]>('/api/members');
}

export function recommend(input: RecommendationRequest): Promise<RecommendationResponse> {
  return request<RecommendationResponse>('/api/recommendations', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function aiStatus(): Promise<AiStatus> {
  return request<AiStatus>('/api/ai/status');
}

export function aiRecommend(message: string): Promise<AiRecommendationResponse> {
  return request<AiRecommendationResponse>('/api/ai/recommendations', {
    method: 'POST',
    body: JSON.stringify({ message, seed: null }),
  });
}
