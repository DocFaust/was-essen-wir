import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState, type FormEvent } from 'react';
import { ApiError } from '../http';
import { completeSetup } from './api';
import { useAuth } from './AuthProvider';

export function SetupPage() {
  const { finishSetup } = useAuth();
  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [passwordConfirmation, setPasswordConfirmation] = useState('');
  const [setupToken, setSetupToken] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (password !== passwordConfirmation) {
      setError('Die Passwörter stimmen nicht überein.');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await completeSetup({ username, displayName, password, passwordConfirmation, setupToken });
      finishSetup();
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Die Einrichtung ist fehlgeschlagen.');
      setSaving(false);
    }
  }

  return (
    <Stack component="form" spacing={2} onSubmit={onSubmit}>
      <header>
        <Typography variant="h1" component="h1">
          Einrichtung
        </Typography>
        <Typography color="text.secondary" sx={{ mt: 1 }}>
          Lege den ersten Administrator für diese Familie an. Das geht nur einmal.
        </Typography>
      </header>
      <Alert severity="info">
        Das Setup-Token steht einmalig in den Backend-Logs. Mit Docker Compose siehst du es über{' '}
        <strong>docker compose logs backend</strong> und suchst nach <strong>SETUP_TOKEN</strong>.
      </Alert>
      {error ? <Alert severity="error">{error}</Alert> : null}
      <TextField
        label="Benutzername"
        value={username}
        onChange={(event) => setUsername(event.target.value)}
        autoComplete="username"
        required
        fullWidth
        slotProps={{ htmlInput: { 'data-testid': 'setup-username' } }}
      />
      <TextField
        label="Anzeigename"
        value={displayName}
        onChange={(event) => setDisplayName(event.target.value)}
        autoComplete="name"
        required
        fullWidth
        slotProps={{ htmlInput: { 'data-testid': 'setup-display-name' } }}
      />
      <TextField
        label="Passwort"
        type="password"
        value={password}
        onChange={(event) => setPassword(event.target.value)}
        autoComplete="new-password"
        required
        fullWidth
        helperText="Mindestens 12 Zeichen"
        slotProps={{ htmlInput: { 'data-testid': 'setup-password' } }}
      />
      <TextField
        label="Passwort wiederholen"
        type="password"
        value={passwordConfirmation}
        onChange={(event) => setPasswordConfirmation(event.target.value)}
        autoComplete="new-password"
        required
        fullWidth
        slotProps={{ htmlInput: { 'data-testid': 'setup-password-confirm' } }}
      />
      <TextField
        label="Setup-Token"
        value={setupToken}
        onChange={(event) => setSetupToken(event.target.value)}
        required
        fullWidth
        slotProps={{ htmlInput: { 'data-testid': 'setup-token', autoComplete: 'off' } }}
      />
      <Button type="submit" variant="contained" fullWidth disabled={saving} data-testid="setup-submit">
        Einrichtung abschließen
      </Button>
    </Stack>
  );
}
