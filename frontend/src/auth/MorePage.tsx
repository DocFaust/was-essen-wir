import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router';
import { BackendStatus } from '../BackendStatus';
import { useAuth } from './AuthProvider';

export function MorePage() {
  const { user, logout } = useAuth();

  return (
    <Stack spacing={2} data-testid="more-page">
      <header>
        <Typography variant="h1" component="h1">
          Mehr
        </Typography>
        <Typography color="text.secondary" sx={{ mt: 1 }}>
          Profil, Familie und Verbindung. Die täglichen Aufgaben liegen unter Heute.
        </Typography>
      </header>
      <Button component={RouterLink} to="/profil" variant="outlined" fullWidth>
        Profil
      </Button>
      {user?.role === 'ADMIN' ? (
        <Button component={RouterLink} to="/benutzer" variant="outlined" fullWidth>
          Familie
        </Button>
      ) : null}
      <BackendStatus />
      <Button type="button" variant="outlined" fullWidth onClick={() => void logout()} data-testid="logout">
        Abmelden
      </Button>
    </Stack>
  );
}
