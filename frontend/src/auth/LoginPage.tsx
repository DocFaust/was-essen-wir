import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState, type FormEvent } from 'react';
import { ApiError } from '../http';
import { login } from './api';
import { useAuth } from './AuthProvider';

export function LoginPage() {
  const { notice, setUser, clearNotice } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    clearNotice();
    try {
      setUser(await login(username, password));
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Die Anmeldung ist fehlgeschlagen.');
      setSaving(false);
    }
  }

  return (
    <Stack component="form" spacing={2} onSubmit={onSubmit}>
      <header>
        <Typography variant="h1" component="h1">
          Anmelden
        </Typography>
        <Typography color="text.secondary" sx={{ mt: 1 }}>
          Melde dich mit deinem Familienkonto an.
        </Typography>
      </header>
      {notice ? <Alert severity="info">{notice}</Alert> : null}
      {error ? <Alert severity="error">{error}</Alert> : null}
      <TextField
        label="Benutzername"
        value={username}
        onChange={(event) => setUsername(event.target.value)}
        autoComplete="username"
        required
        fullWidth
        slotProps={{ htmlInput: { 'data-testid': 'login-username' } }}
      />
      <TextField
        label="Passwort"
        type="password"
        value={password}
        onChange={(event) => setPassword(event.target.value)}
        autoComplete="current-password"
        required
        fullWidth
        slotProps={{ htmlInput: { 'data-testid': 'login-password' } }}
      />
      <Button type="submit" variant="contained" fullWidth disabled={saving} data-testid="login-submit">
        Anmelden
      </Button>
    </Stack>
  );
}
