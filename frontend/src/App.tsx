import BottomNavigation from '@mui/material/BottomNavigation';
import BottomNavigationAction from '@mui/material/BottomNavigationAction';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { Link as RouterLink, Navigate, Route, Routes, useLocation } from 'react-router';
import { LoginPage } from './auth/LoginPage';
import { PasswordChangePage } from './auth/PasswordChangePage';
import { ProfilePage } from './auth/ProfilePage';
import { SetupPage } from './auth/SetupPage';
import { AuthProvider, useAuth } from './auth/AuthProvider';
import { UsersPage } from './auth/UsersPage';
import { DishDetailPage } from './dishes/DishDetailPage';
import { DishFormPage } from './dishes/DishFormPage';
import { DishListPage } from './dishes/DishListPage';
import { VariantFormPage } from './dishes/VariantFormPage';
import { ErrorState, LoadingState } from './dishes/ui';
import { HomePage } from './HomePage';

export function App() {
  return (
    <AuthProvider>
      <AppShell />
    </AuthProvider>
  );
}

function AppShell() {
  const location = useLocation();
  const { phase, user, refresh, logout } = useAuth();
  const section = location.pathname.startsWith('/gerichte')
    ? '/gerichte'
    : location.pathname.startsWith('/profil')
      ? '/profil'
      : location.pathname.startsWith('/benutzer')
        ? '/benutzer'
        : '/';
  const showNavigation = phase === 'ready' && user !== null && !user.passwordChangeRequired;

  return (
    <Box
      sx={{
        height: '100dvh',
        width: '100%',
        maxWidth: '100%',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        boxSizing: 'border-box',
      }}
    >
      {user ? (
        <Stack
          direction="row"
          spacing={1}
          sx={{ px: 2, pt: 1, alignItems: 'center', flexShrink: 0, minWidth: 0 }}
        >
          <Typography data-testid="current-user" sx={{ flex: 1, minWidth: 0, overflowWrap: 'anywhere' }}>
            {user.displayName}
          </Typography>
          <Button type="button" variant="outlined" onClick={() => void logout()} data-testid="logout">
            Abmelden
          </Button>
        </Stack>
      ) : null}
      <Box
        component="main"
        sx={{
          flex: 1,
          minHeight: 0,
          overflowY: 'auto',
          overflowX: 'hidden',
          px: 2,
          py: 2,
          boxSizing: 'border-box',
          width: '100%',
        }}
      >
        <Box sx={{ maxWidth: 720, mx: 'auto', minWidth: 0 }}>
          {phase === 'loading' ? <LoadingState label="Anmeldung wird geprüft …" /> : null}
          {phase === 'error' ? (
            <ErrorState message="Das Backend ist gerade nicht erreichbar." onRetry={() => void refresh()} />
          ) : null}
          {phase === 'setup' ? <SetupPage /> : null}
          {phase === 'anonymous' ? <LoginPage /> : null}
          {phase === 'ready' && user?.passwordChangeRequired ? <PasswordChangePage /> : null}
          {showNavigation && user ? (
            <Routes key={user.id}>
              <Route path="/" element={<HomePage />} />
              <Route path="/gerichte" element={<DishListPage />} />
              <Route path="/gerichte/neu" element={<DishFormPage />} />
              <Route path="/gerichte/:id" element={<DishDetailPage />} />
              <Route path="/gerichte/:id/bearbeiten" element={<DishFormPage />} />
              <Route path="/gerichte/:id/varianten/neu" element={<VariantFormPage />} />
              <Route path="/gerichte/:id/varianten/:variantId/bearbeiten" element={<VariantFormPage />} />
              <Route path="/profil" element={<ProfilePage />} />
              <Route path="/benutzer" element={<UsersPage />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          ) : null}
        </Box>
      </Box>
      {showNavigation ? (
        <Paper component="nav" aria-label="Hauptnavigation" elevation={3} sx={{ flexShrink: 0 }}>
          <BottomNavigation showLabels value={section} sx={{ height: 64 }}>
            <BottomNavigationAction label="Start" value="/" component={RouterLink} to="/" sx={{ minWidth: 0, px: 0.5 }} />
            <BottomNavigationAction
              label="Gerichte"
              value="/gerichte"
              component={RouterLink}
              to="/gerichte"
              sx={{ minWidth: 0, px: 0.5 }}
            />
            <BottomNavigationAction
              label="Profil"
              value="/profil"
              component={RouterLink}
              to="/profil"
              sx={{ minWidth: 0, px: 0.5 }}
            />
            {user?.role === 'ADMIN' ? (
              <BottomNavigationAction
                label="Familie"
                value="/benutzer"
                component={RouterLink}
                to="/benutzer"
                sx={{ minWidth: 0, px: 0.5 }}
              />
            ) : null}
          </BottomNavigation>
        </Paper>
      ) : null}
    </Box>
  );
}
