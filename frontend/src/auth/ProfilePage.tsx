import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState, type FormEvent } from 'react';
import { ApiError } from '../http';
import { changePassword, updateProfile } from './api';
import { useAuth } from './AuthProvider';

export function ProfilePage() {
  const { user, setUser } = useAuth();
  const [displayName, setDisplayName] = useState(user?.displayName ?? '');
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [passwordConfirmation, setPasswordConfirmation] = useState('');
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [savingName, setSavingName] = useState(false);
  const [savingPassword, setSavingPassword] = useState(false);

  async function onName(event: FormEvent) {
    event.preventDefault();
    setSavingName(true);
    setError(null);
    setNotice(null);
    try {
      setUser(await updateProfile(displayName));
      setNotice('Der Anzeigename wurde gespeichert.');
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Der Anzeigename konnte nicht gespeichert werden.');
    } finally {
      setSavingName(false);
    }
  }

  async function onPassword(event: FormEvent) {
    event.preventDefault();
    if (newPassword !== passwordConfirmation) {
      setError('Die Passwörter stimmen nicht überein.');
      setNotice(null);
      return;
    }
    setSavingPassword(true);
    setError(null);
    setNotice(null);
    try {
      setUser(await changePassword({ currentPassword, newPassword, passwordConfirmation }));
      setCurrentPassword('');
      setNewPassword('');
      setPasswordConfirmation('');
      setNotice('Das Passwort wurde geändert.');
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Das Passwort konnte nicht geändert werden.');
    } finally {
      setSavingPassword(false);
    }
  }

  return (
    <Stack spacing={3}>
      <header>
        <Typography variant="h1" component="h1">
          Mein Profil
        </Typography>
        <Typography color="text.secondary" sx={{ mt: 1 }}>
          Benutzername: {user?.username}
        </Typography>
      </header>
      {notice ? <Alert severity="success">{notice}</Alert> : null}
      {error ? <Alert severity="error">{error}</Alert> : null}
      <Stack component="form" spacing={2} onSubmit={onName}>
        <TextField
          label="Anzeigename"
          value={displayName}
          onChange={(event) => setDisplayName(event.target.value)}
          required
          fullWidth
          slotProps={{ htmlInput: { 'data-testid': 'profile-display-name' } }}
        />
        <Button type="submit" variant="contained" fullWidth disabled={savingName}>
          Anzeigename speichern
        </Button>
      </Stack>
      <Stack component="form" spacing={2} onSubmit={onPassword}>
        <Typography component="h2" variant="h2" sx={{ fontSize: '1.25rem' }}>
          Passwort
        </Typography>
        <TextField
          label="Bisheriges Passwort"
          type="password"
          value={currentPassword}
          onChange={(event) => setCurrentPassword(event.target.value)}
          autoComplete="current-password"
          required
          fullWidth
        />
        <TextField
          label="Neues Passwort"
          type="password"
          value={newPassword}
          onChange={(event) => setNewPassword(event.target.value)}
          autoComplete="new-password"
          required
          fullWidth
          helperText="Mindestens 12 Zeichen"
        />
        <TextField
          label="Neues Passwort wiederholen"
          type="password"
          value={passwordConfirmation}
          onChange={(event) => setPasswordConfirmation(event.target.value)}
          autoComplete="new-password"
          required
          fullWidth
        />
        <Button type="submit" variant="contained" fullWidth disabled={savingPassword} data-testid="profile-save-password">
          Passwort ändern
        </Button>
      </Stack>
    </Stack>
  );
}
