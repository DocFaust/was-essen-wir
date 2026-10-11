import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState, type FormEvent } from 'react';
import { ApiError } from '../http';
import { changePassword } from './api';
import { useAuth } from './AuthProvider';

export function PasswordChangePage() {
  const { setUser, logout } = useAuth();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [passwordConfirmation, setPasswordConfirmation] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (newPassword !== passwordConfirmation) {
      setError('Die Passwörter stimmen nicht überein.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      setUser(await changePassword({ currentPassword, newPassword, passwordConfirmation }));
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Das Passwort konnte nicht geändert werden.');
      setSaving(false);
    }
  }

  return (
    <Stack component="form" spacing={2} onSubmit={onSubmit}>
      <header>
        <Typography variant="h1" component="h1">
          Passwort ändern
        </Typography>
        <Typography color="text.secondary" sx={{ mt: 1 }}>
          Das temporäre Passwort muss vor der weiteren Nutzung ersetzt werden.
        </Typography>
      </header>
      {error ? <Alert severity="error">{error}</Alert> : null}
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
      <Button type="submit" variant="contained" fullWidth disabled={saving}>
        Passwort speichern
      </Button>
      <Button type="button" variant="outlined" fullWidth onClick={() => void logout()} data-testid="logout">
        Abmelden
      </Button>
    </Stack>
  );
}
