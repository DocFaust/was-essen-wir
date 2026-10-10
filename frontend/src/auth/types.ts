export type Role = 'ADMIN' | 'MEMBER';

export type CurrentUser = {
  id: string;
  username: string;
  displayName: string;
  role: Role;
  active: boolean;
  passwordChangeRequired: boolean;
};

export type SetupInput = {
  username: string;
  displayName: string;
  password: string;
  passwordConfirmation: string;
  setupToken: string;
};

export type PasswordChangeInput = {
  currentPassword: string;
  newPassword: string;
  passwordConfirmation: string;
};
