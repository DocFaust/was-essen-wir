import { CssBaseline, ThemeProvider } from '@mui/material';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router';
import '@fontsource/roboto/400.css';
import '@fontsource/roboto/700.css';
import { App } from './App';
import { theme } from './theme';

const root = document.getElementById('root');
if (!root) {
  throw new Error('Root-Element fehlt');
}

createRoot(root).render(
  <StrictMode>
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </ThemeProvider>
  </StrictMode>,
);
