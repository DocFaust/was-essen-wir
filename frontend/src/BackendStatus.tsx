import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Stack from '@mui/material/Stack';
import { useBackendStatus } from './useBackendStatus';

export function BackendStatus() {
  const { state, retry } = useBackendStatus();

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
        <Button type="button" variant="outlined" color="inherit" fullWidth onClick={retry}>
          Erneut prüfen
        </Button>
      </Stack>
    </Alert>
  );
}
