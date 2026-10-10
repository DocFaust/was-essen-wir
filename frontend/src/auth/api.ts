import { request } from '../http';
import type { CurrentUser, PasswordChangeInput, Role, SetupInput } from './types';

export type SetupStatus = {
  setupRequired: boolean;
};

export function getSetupStatus(): Promise<SetupStatus> {
  return request<SetupStatus>('/api/setup/status');
}

export function completeSetup(input: SetupInput): Promise<CurrentUser> {
  return request<CurrentUser>('/api/setup', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function login(username: string, password: string): Promise<CurrentUser> {
  return request<CurrentUser>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
}

export function logout(): Promise<void> {
  return request<void>('/api/auth/logout', { method: 'POST' });
}

export function getMe(): Promise<CurrentUser> {
  return request<CurrentUser>('/api/auth/me');
}

export function updateProfile(displayName: string): Promise<CurrentUser> {
  return request<CurrentUser>('/api/auth/profile', {
    method: 'PUT',
    body: JSON.stringify({ displayName }),
  });
}

export function changePassword(input: PasswordChangeInput): Promise<CurrentUser> {
  return request<CurrentUser>('/api/auth/password', {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}

export function listUsers(): Promise<CurrentUser[]> {
  return request<CurrentUser[]>('/api/users');
}

export function createUser(input: {
  username: string;
  displayName: string;
  password: string;
  role: Role;
}): Promise<CurrentUser> {
  return request<CurrentUser>('/api/users', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

export function updateUser(
  id: string,
  input: { displayName: string; role: Role; active: boolean },
): Promise<CurrentUser> {
  return request<CurrentUser>(`/api/users/${id}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  });
}

export function resetPassword(id: string, password: string): Promise<CurrentUser> {
  return request<CurrentUser>(`/api/users/${id}/password`, {
    method: 'POST',
    body: JSON.stringify({ password }),
  });
}
