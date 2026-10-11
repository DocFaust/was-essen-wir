import BottomNavigation from '@mui/material/BottomNavigation';
import BottomNavigationAction from '@mui/material/BottomNavigationAction';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import { useEffect, useRef } from 'react';
import { Link as RouterLink, Navigate, Route, Routes, useLocation } from 'react-router';
import { LoginPage } from './auth/LoginPage';
import { MorePage } from './auth/MorePage';
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

const NAV_ITEMS = [
  { to: '/', value: '/', label: 'Heute', icon: 'M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z' },
  {
    to: '/plan',
    value: '/plan',
    label: 'Plan',
    icon: 'M19 4h-1V2h-2v2H8V2H6v2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 16H5V9h14v11z',
  },
  {
    to: '/gerichte',
    value: '/gerichte',
    label: 'Gerichte',
    icon: 'M11 9H9V2H7v7H5V2H3v7c0 2.12 1.66 3.84 3.75 3.97V22h2.5v-9.03C11.34 12.84 13 11.12 13 9V2h-2v7zm5-3v8h2.5v8H21V2c-2.76 0-5 2.24-5 4z',
  },
  {
    to: '/historie',
    value: '/historie',
    label: 'Historie',
    icon: 'M13 3a9 9 0 0 0-9 9H1l3.89 3.89.07.14L9 12H6a7 7 0 1 1 7 7 6.9 6.9 0 0 1-4.94-2.06l-1.42 1.42A8.96 8.96 0 0 0 13 21a9 9 0 0 0 0-18zm-1 5v5l4.28 2.54.72-1.21-3.5-2.08V8z',
  },
  {
    to: '/mehr',
    value: '/mehr',
    label: 'Mehr',
    icon: 'M6 10a2 2 0 1 0 .01 4A2 2 0 0 0 6 10zm6 0a2 2 0 1 0 .01 4A2 2 0 0 0 12 10zm6 0a2 2 0 1 0 .01 4A2 2 0 0 0 18 10z',
  },
] as const;

export function App() {
  return (
    <AuthProvider>
      <AppShell />
    </AuthProvider>
  );
}

function AppShell() {
  const location = useLocation();
  const { phase, user, refresh } = useAuth();
  const section = sectionOf(location.pathname);
  const showNavigation = phase === 'ready' && user !== null && !user.passwordChangeRequired;
  const mainRef = useRef<HTMLElement>(null);
  const scrollPositions = useRef(new Map<string, number>());

  useEffect(() => {
    const main = mainRef.current;
    if (!main) {
      return;
    }
    const path = location.pathname;
    const top = scrollPositions.current.get(path) ?? 0;
    if (typeof main.scrollTo === 'function') {
      main.scrollTo(0, top);
    } else {
      main.scrollTop = top;
    }
    const onScroll = () => {
      scrollPositions.current.set(path, main.scrollTop);
    };
    main.addEventListener('scroll', onScroll, { passive: true });
    return () => main.removeEventListener('scroll', onScroll);
  }, [location.pathname]);

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
        <Box
          sx={{
            order: 0,
            px: 2,
            pt: 'max(4px, env(safe-area-inset-top))',
            display: 'flex',
            alignItems: 'center',
            flexShrink: 0,
            minWidth: 0,
            minHeight: 44,
          }}
        >
          {user.passwordChangeRequired ? (
            <Typography data-testid="current-user" sx={{ minWidth: 0, overflowWrap: 'anywhere' }}>
              {user.displayName}
            </Typography>
          ) : (
            <Button
              component={RouterLink}
              to="/mehr"
              variant="text"
              data-testid="current-user"
              sx={{ justifyContent: 'flex-start', px: 0.5, minWidth: 0, maxWidth: '100%' }}
            >
              <Box component="span" sx={{ overflowWrap: 'anywhere', textAlign: 'left' }}>
                {user.displayName}
              </Box>
            </Button>
          )}
        </Box>
      ) : null}
      {showNavigation ? <AppNav section={section} /> : null}
      <Box
        component="main"
        ref={mainRef}
        sx={{
          order: { xs: 1, md: 2 },
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
              <Route path="/mehr" element={<MorePage />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          ) : null}
        </Box>
      </Box>
    </Box>
  );
}

function AppNav({ section }: { section: string }) {
  return (
    <Paper
      component="nav"
      aria-label="Hauptnavigation"
      elevation={3}
      sx={{
        order: { xs: 2, md: 1 },
        flexShrink: 0,
        pb: { xs: 'env(safe-area-inset-bottom)', md: 0 },
      }}
    >
      <BottomNavigation
        showLabels
        value={section}
        sx={{
          height: { xs: 64, md: 56 },
          '& .MuiBottomNavigationAction-label': {
            fontSize: '0.75rem',
            lineHeight: 1.15,
            whiteSpace: 'nowrap',
            '&.Mui-selected': { fontSize: '0.75rem', fontWeight: 700 },
          },
        }}
      >
        {NAV_ITEMS.map((item) => (
          <BottomNavigationAction
            key={item.value}
            label={item.label}
            value={item.value}
            icon={<NavIcon d={item.icon} />}
            component={RouterLink}
            to={item.to}
            sx={{ minWidth: 0, px: 0.25, maxWidth: 'none' }}
          />
        ))}
      </BottomNavigation>
    </Paper>
  );
}

function NavIcon({ d }: { d: string }) {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <path fill="currentColor" d={d} />
    </svg>
  );
}

function sectionOf(pathname: string): string {
  if (pathname.startsWith('/gerichte')) {
    return '/gerichte';
  }
  if (pathname.startsWith('/plan')) {
    return '/plan';
  }
  if (pathname.startsWith('/historie')) {
    return '/historie';
  }
  if (pathname.startsWith('/mehr') || pathname.startsWith('/profil') || pathname.startsWith('/benutzer')) {
    return '/mehr';
  }
  return '/';
}
