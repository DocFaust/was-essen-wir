import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useAuth } from './auth/AuthProvider';
import { SuggestForm } from './recommendations/SuggestForm';
import { useBackendStatus } from './useBackendStatus';

export function HomePage() {
  const { user } = useAuth();
  const { state, retry } = useBackendStatus();

  return (
    <Stack spacing={2.5}>
      <header>
        <Typography variant="h1" component="h1">
          Was essen wir?
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mt: 1 }}>
          Drei Vorschläge für die Personen, die mitessen.
        </Typography>
      </header>
      {user ? <SuggestForm userId={user.id} /> : null}
      <BackendStatus state={state} onRetry={retry} />
    </Stack>
  );
}

function BackendStatus({
  state,
  onRetry,
}: {
  state: ReturnType<typeof useBackendStatus>['state'];
  onRetry: () => void;
}) {
  if (state.phase === 'loading') {
    return (
      <Alert
        role="status"
        severity="info"
        data-testid="backend-status"
        icon={<CircularProgress size={22} color="inherit" aria-hidden="true" />}
      >
        Verbindung wird geprüft …
      </Alert>
    );
  }

  if (state.phase === 'up') {
    return (
      <Alert role="status" severity="success" data-testid="backend-status">
        Backend erreichbar ({state.application})
      </Alert>
    );
  }

  return (
    <Alert role="alert" severity="error" data-testid="backend-status">
      <Stack spacing={1.5}>
        <span>{state.message}</span>
        <Button type="button" variant="outlined" color="inherit" fullWidth onClick={onRetry}>
          Erneut prüfen
        </Button>
      </Stack>
    </Alert>
  );
}
