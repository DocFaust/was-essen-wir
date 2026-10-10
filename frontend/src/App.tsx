import BottomNavigation from '@mui/material/BottomNavigation';
import BottomNavigationAction from '@mui/material/BottomNavigationAction';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import { Link as RouterLink, Route, Routes, useLocation } from 'react-router';
import { DishDetailPage } from './dishes/DishDetailPage';
import { DishFormPage } from './dishes/DishFormPage';
import { DishListPage } from './dishes/DishListPage';
import { VariantFormPage } from './dishes/VariantFormPage';
import { HomePage } from './HomePage';

export function App() {
  const location = useLocation();
  const section = location.pathname.startsWith('/gerichte') ? '/gerichte' : '/';

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
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/gerichte" element={<DishListPage />} />
            <Route path="/gerichte/neu" element={<DishFormPage />} />
            <Route path="/gerichte/:id" element={<DishDetailPage />} />
            <Route path="/gerichte/:id/bearbeiten" element={<DishFormPage />} />
            <Route path="/gerichte/:id/varianten/neu" element={<VariantFormPage />} />
            <Route path="/gerichte/:id/varianten/:variantId/bearbeiten" element={<VariantFormPage />} />
          </Routes>
        </Box>
      </Box>
      <Paper
        component="nav"
        aria-label="Hauptnavigation"
        elevation={3}
        sx={{ flexShrink: 0 }}
      >
        <BottomNavigation showLabels value={section} sx={{ height: 64 }}>
          <BottomNavigationAction label="Start" value="/" component={RouterLink} to="/" />
          <BottomNavigationAction label="Gerichte" value="/gerichte" component={RouterLink} to="/gerichte" />
        </BottomNavigation>
      </Paper>
    </Box>
  );
}
