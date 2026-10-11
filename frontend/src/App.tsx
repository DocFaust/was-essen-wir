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
import { HistoryPage } from './meals/HistoryPage';
import { PlanPage } from './meals/PlanPage';

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
    : location.pathname.startsWith('/plan')
      ? '/plan'
      : location.pathname.startsWith('/historie')
        ? '/historie'
        : location.pathname.startsWith('/profil') || location.pathname.startsWith('/benutzer')
          ? '/profil'
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
          sx={{ px: 2, pt: 1, alignItems: 'center', flexShrink: 0, minWidth: 0, flexWrap: 'wrap' }}
        >
          <Typography data-testid="current-user" sx={{ flex: 1, minWidth: 0, overflowWrap: 'anywhere' }}>
            {user.displayName}
          </Typography>
          {user.role === 'ADMIN' ? (
            <Button component={RouterLink} to="/benutzer" variant="text" sx={{ minWidth: 0, px: 1 }}>
              Familie
            </Button>
          ) : null}
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
              <Route path="/plan" element={<PlanPage userId={user.id} />} />
              <Route path="/historie" element={<HistoryPage userId={user.id} />} />
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
          <BottomNavigation
            showLabels
            value={section}
            sx={{
              height: 72,
              '& .MuiBottomNavigationAction-label': {
                fontSize: '0.7rem',
                lineHeight: 1.15,
                whiteSpace: 'normal',
                '&.Mui-selected': { fontSize: '0.7rem' },
              },
            }}
          >
            <BottomNavigationAction label="Start" value="/" component={RouterLink} to="/" sx={{ minWidth: 0, px: 0.25 }} />
            <BottomNavigationAction label="Plan" value="/plan" component={RouterLink} to="/plan" sx={{ minWidth: 0, px: 0.25 }} />
            <BottomNavigationAction
              label="Historie"
              value="/historie"
              component={RouterLink}
              to="/historie"
              sx={{ minWidth: 0, px: 0.25 }}
            />
            <BottomNavigationAction
              label="Gerichte"
              value="/gerichte"
              component={RouterLink}
              to="/gerichte"
              sx={{ minWidth: 0, px: 0.25 }}
            />
            <BottomNavigationAction
              label="Profil"
              value="/profil"
              component={RouterLink}
              to="/profil"
              sx={{ minWidth: 0, px: 0.25 }}
            />
          </BottomNavigation>
        </Paper>
      ) : null}
    </Box>
  );
}
