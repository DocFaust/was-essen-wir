import { ThemeProvider } from '@mui/material/styles';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, expect, test, vi } from 'vitest';
import { App } from './App';
import { theme } from './theme';

function renderApp() {
  return render(
    <ThemeProvider theme={theme}>
      <MemoryRouter>
        <App />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

test('zeigt die Startseite', () => {
  vi.stubGlobal(
    'fetch',
    () => new Promise(() => undefined),
  );

  renderApp();

  expect(screen.getByRole('heading', { name: 'Was essen wir?' })).toBeInTheDocument();
});

test('zeigt den Ladezustand, bis das Backend antwortet', async () => {
  let resolveFetch: (value: Response) => void = () => undefined;
  vi.stubGlobal(
    'fetch',
    () =>
      new Promise<Response>((resolve) => {
        resolveFetch = resolve;
      }),
  );

  renderApp();

  expect(screen.getByTestId('backend-status')).toHaveTextContent('Verbindung wird geprüft');

  resolveFetch(jsonResponse({ status: 'UP', application: 'was-essen-wir' }));

  expect(await screen.findByText('Backend erreichbar (was-essen-wir)')).toBeInTheDocument();
});

test('zeigt eine erfolgreiche Backend-Verbindung', async () => {
  vi.stubGlobal('fetch', async () => jsonResponse({ status: 'UP', application: 'was-essen-wir' }));

  renderApp();

  expect(await screen.findByText('Backend erreichbar (was-essen-wir)')).toBeInTheDocument();
});

test('zeigt eine verständliche Meldung, wenn das Backend nicht erreichbar ist', async () => {
  vi.stubGlobal('fetch', async () => {
    throw new Error('offline');
  });

  renderApp();

  expect(await screen.findByRole('alert')).toHaveTextContent(
    'Das Backend ist gerade nicht erreichbar',
  );
});

test('prüft die Verbindung nach einem Fehler erneut', async () => {
  const fetchMock = vi
    .fn<typeof fetch>()
    .mockRejectedValueOnce(new Error('offline'))
    .mockResolvedValueOnce(jsonResponse({ status: 'UP', application: 'was-essen-wir' }));
  vi.stubGlobal('fetch', fetchMock);

  renderApp();

  expect(await screen.findByRole('alert')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Erneut prüfen' }));

  expect(await screen.findByText('Backend erreichbar (was-essen-wir)')).toBeInTheDocument();
});

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}
