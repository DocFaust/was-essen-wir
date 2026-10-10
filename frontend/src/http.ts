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

type UnauthorizedHandler = (message: string) => void;

let onUnauthorized: UnauthorizedHandler | null = null;
let onPasswordChangeRequired: (() => void) | null = null;
let sessionEstablished = false;

export function setUnauthorizedHandler(handler: UnauthorizedHandler | null) {
  onUnauthorized = handler;
}

export function setPasswordChangeHandler(handler: (() => void) | null) {
  onPasswordChangeRequired = handler;
}

export function setSessionEstablished(value: boolean) {
  sessionEstablished = value;
}

function readCookie(name: string): string | null {
  const prefix = `${name}=`;
  for (const part of document.cookie.split('; ')) {
    if (part.startsWith(prefix)) {
      return decodeURIComponent(part.slice(prefix.length));
    }
  }
  return null;
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const method = (init?.method ?? 'GET').toUpperCase();
  const headers = new Headers(init?.headers);
  headers.set('Accept', 'application/json');
  if (init?.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (method !== 'GET' && method !== 'HEAD') {
    const csrf = readCookie('XSRF-TOKEN');
    if (csrf) {
      headers.set('X-XSRF-TOKEN', csrf);
    }
  }

  let response: Response;
  try {
    response = await fetch(path, {
      ...init,
      headers,
      credentials: 'same-origin',
      cache: 'no-store',
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
    if (
      response.status === 401 &&
      sessionEstablished &&
      !path.startsWith('/api/auth/login') &&
      !path.startsWith('/api/setup')
    ) {
      onUnauthorized?.('Deine Sitzung ist abgelaufen. Bitte melde dich erneut an.');
    }
    if (response.status === 403 && payload.code === 'PASSWORD_CHANGE_REQUIRED') {
      onPasswordChangeRequired?.();
    }
    throw new ApiError(payload.detail ?? 'Die Anfrage ist fehlgeschlagen.', response.status, payload.code);
  }

  return payload as T;
}
