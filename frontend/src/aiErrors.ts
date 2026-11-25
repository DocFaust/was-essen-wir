import { ApiError } from './http';

export type AiLimitKind = 'daily' | 'temporary' | null;

export function aiLimitKind(error: unknown): AiLimitKind {
  if (!(error instanceof ApiError) || error.status !== 429) {
    return null;
  }
  if (error.code === 'AI_DAILY_LIMIT') {
    return 'daily';
  }
  if (error.code === 'AI_RATE_LIMITED' || error.code === 'AI_BUSY') {
    return 'temporary';
  }
  return 'temporary';
}

export function aiErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError && error.message.trim().length > 0) {
    return error.message;
  }
  return fallback;
}

export function canRetryAiError(error: unknown): boolean {
  return aiLimitKind(error) !== 'daily';
}
