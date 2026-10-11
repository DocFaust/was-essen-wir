import { ThemeProvider } from '@mui/material/styles';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, expect, test, vi } from 'vitest';
import { App } from './App';
import type { CurrentUser } from './auth/types';
import { theme } from './theme';

const admin: CurrentUser = {
  id: 'admin-1',
  username: 'ada',
  displayName: 'Ada Lovelace',
  role: 'ADMIN',
  active: true,
  passwordChangeRequired: false,
};

const member: CurrentUser = {
  ...admin,
  id: 'member-1',
  username: 'bea',
  displayName: 'Bea Beispiel',
  role: 'MEMBER',
};

function renderApp(path = '/') {
  return render(
    <ThemeProvider theme={theme}>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function problem(detail: string, status: number, code?: string): Response {
  return jsonResponse({ title: 'Fehler', detail, status, code }, status);
}

function routeFetch(routes: Record<string, () => Response | Promise<Response>>) {
  return vi.fn<typeof fetch>(async (input) => {
    const url = String(input);
    if (url.includes('/api/members')) {
      return jsonResponse([{ id: admin.id, displayName: admin.displayName }]);
    }
    if (url.includes('/api/meal-plans')) {
      return jsonResponse([]);
    }
    if (url.includes('/api/ai/status')) {
      return jsonResponse({ enabled: false, provider: null });
    }
    const match = Object.entries(routes).find(([path]) => url.includes(path));
    if (!match) {
      return problem('Unerwartete Anfrage', 500);
    }
    return match[1]();
  });
}

beforeEach(() => {
  vi.unstubAllGlobals();
  document.cookie = 'XSRF-TOKEN=test-token';
});

test('zeigt die Startseite nach der Anmeldung', async () => {
  vi.stubGlobal(
    'fetch',
    routeFetch({
      '/api/setup/status': () => jsonResponse({ setupRequired: false }),
      '/api/auth/me': () => jsonResponse(admin),
      '/api/status': () => new Promise(() => undefined),
    }),
  );

  renderApp();

  expect(await screen.findByRole('heading', { name: 'Was essen wir heute?' })).toBeInTheDocument();
  expect(screen.getByTestId('current-user')).toHaveTextContent('Ada Lovelace');
});

test('zeigt den Ladezustand, bis das Backend antwortet', async () => {
  let resolveStatus: (value: Response) => void = () => undefined;
  vi.stubGlobal(
    'fetch',
    routeFetch({
      '/api/setup/status': () => jsonResponse({ setupRequired: false }),
      '/api/auth/me': () => jsonResponse(admin),
      '/api/status': () =>
        new Promise<Response>((resolve) => {
          resolveStatus = resolve;
        }),
    }),
  );

  renderApp();
  await userEvent.click(await screen.findByRole('link', { name: 'Mehr' }));

  expect(await screen.findByTestId('backend-status')).toHaveTextContent('Verbindung wird geprüft');
  resolveStatus(jsonResponse({ status: 'UP', application: 'was-essen-wir' }));
  expect(await screen.findByText('Backend erreichbar (was-essen-wir)')).toBeInTheDocument();
});

test('zeigt eine erfolgreiche Backend-Verbindung', async () => {
  vi.stubGlobal(
    'fetch',
    routeFetch({
      '/api/setup/status': () => jsonResponse({ setupRequired: false }),
      '/api/auth/me': () => jsonResponse(admin),
      '/api/status': () => jsonResponse({ status: 'UP', application: 'was-essen-wir' }),
    }),
  );

  renderApp();
  await userEvent.click(await screen.findByRole('link', { name: 'Mehr' }));

  expect(await screen.findByText('Backend erreichbar (was-essen-wir)')).toBeInTheDocument();
});

test('zeigt eine verständliche Meldung, wenn das Backend nicht erreichbar ist', async () => {
  vi.stubGlobal(
    'fetch',
    routeFetch({
      '/api/setup/status': () => jsonResponse({ setupRequired: false }),
      '/api/auth/me': () => jsonResponse(admin),
      '/api/status': () => {
        throw new Error('offline');
      },
    }),
  );

  renderApp();
  await userEvent.click(await screen.findByRole('link', { name: 'Mehr' }));

  expect(await screen.findByRole('alert')).toHaveTextContent('Das Backend ist gerade nicht erreichbar');
});

test('prüft die Verbindung nach einem Fehler erneut', async () => {
  let calls = 0;
  vi.stubGlobal(
    'fetch',
    routeFetch({
      '/api/setup/status': () => jsonResponse({ setupRequired: false }),
      '/api/auth/me': () => jsonResponse(admin),
      '/api/status': () => {
        calls += 1;
        if (calls === 1) {
          throw new Error('offline');
        }
        return jsonResponse({ status: 'UP', application: 'was-essen-wir' });
      },
    }),
  );

  renderApp();
  await userEvent.click(await screen.findByRole('link', { name: 'Mehr' }));

  expect(await screen.findByRole('alert')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Erneut prüfen' }));
  expect(await screen.findByText('Backend erreichbar (was-essen-wir)')).toBeInTheDocument();
});

test('zeigt den Einrichtungsassistenten und erklärt das Setup-Token', async () => {
  vi.stubGlobal('fetch', routeFetch({ '/api/setup/status': () => jsonResponse({ setupRequired: true }) }));

  renderApp();

  expect(await screen.findByRole('heading', { name: 'Einrichtung' })).toBeInTheDocument();
  expect(screen.getByText(/docker compose logs backend/)).toBeInTheDocument();
  expect(screen.getByText(/SETUP_TOKEN/)).toBeInTheDocument();
  expect(screen.queryByRole('link', { name: 'Gerichte' })).not.toBeInTheDocument();
});

test('schließt die Einrichtung ab und wechselt zur Anmeldung', async () => {
  const fetchMock = routeFetch({
    '/api/setup/status': () => jsonResponse({ setupRequired: true }),
    '/api/setup': () => jsonResponse(admin, 201),
  });
  vi.stubGlobal('fetch', fetchMock);

  renderApp();
  await screen.findByRole('heading', { name: 'Einrichtung' });
  await userEvent.type(screen.getByTestId('setup-username'), 'Ada');
  await userEvent.type(screen.getByTestId('setup-display-name'), 'Ada Lovelace');
  await userEvent.type(screen.getByTestId('setup-password'), 'ein-langes-passwort');
  await userEvent.type(screen.getByTestId('setup-password-confirm'), 'ein-langes-passwort');
  await userEvent.type(screen.getByTestId('setup-token'), 'token-wert');
  await userEvent.click(screen.getByTestId('setup-submit'));

  expect(await screen.findByRole('heading', { name: 'Anmelden' })).toBeInTheDocument();
  expect(screen.getByRole('alert')).toHaveTextContent('Die Einrichtung ist abgeschlossen');
  const setupCall = fetchMock.mock.calls.find((call) => String(call[0]).includes('/api/setup') && !String(call[0]).includes('status'));
  expect(setupCall?.[1]).toEqual(expect.objectContaining({ method: 'POST' }));
  expect(new Headers(setupCall?.[1]?.headers).get('X-XSRF-TOKEN')).toBe('test-token');
});

test('zeigt eine Fehlermeldung, wenn die Passwörter nicht übereinstimmen', async () => {
  vi.stubGlobal(
    'fetch',
    routeFetch({
      '/api/setup/status': () => jsonResponse({ setupRequired: true }),
      '/api/setup': () => problem('Das Setup-Token ist ungültig oder abgelaufen.', 401, 'SETUP_TOKEN_INVALID'),
    }),
  );

  renderApp();
  await screen.findByRole('heading', { name: 'Einrichtung' });
  await userEvent.type(screen.getByTestId('setup-username'), 'Ada');
  await userEvent.type(screen.getByTestId('setup-display-name'), 'Ada Lovelace');
  await userEvent.type(screen.getByTestId('setup-password'), 'ein-langes-passwort');
  await userEvent.type(screen.getByTestId('setup-password-confirm'), 'anderes-passwort-1');
  await userEvent.type(screen.getByTestId('setup-token'), 'token-wert');
  await userEvent.click(screen.getByTestId('setup-submit'));
  expect(await screen.findByText('Die Passwörter stimmen nicht überein.')).toBeInTheDocument();
});

test('meldet einen Benutzer an und wieder ab', async () => {
  vi.stubGlobal(
    'fetch',
    routeFetch({
      '/api/setup/status': () => jsonResponse({ setupRequired: false }),
      '/api/auth/me': () => problem('Bitte melde dich an.', 401, 'UNAUTHENTICATED'),
      '/api/auth/login': () => jsonResponse(admin),
      '/api/auth/logout': () => new Response(null, { status: 204 }),
      '/api/status': () => jsonResponse({ status: 'UP', application: 'was-essen-wir' }),
    }),
  );

  renderApp();
  expect(await screen.findByRole('heading', { name: 'Anmelden' })).toBeInTheDocument();
  await userEvent.type(screen.getByTestId('login-username'), 'ada');
  await userEvent.type(screen.getByTestId('login-password'), 'ein-langes-passwort');
  await userEvent.click(screen.getByTestId('login-submit'));
  expect(await screen.findByRole('heading', { name: 'Was essen wir heute?' })).toBeInTheDocument();
  await userEvent.click(screen.getByRole('link', { name: 'Mehr' }));
  expect(await screen.findByRole('link', { name: 'Familie' })).toBeInTheDocument();
  await userEvent.click(screen.getByTestId('logout'));
  expect(await screen.findByRole('heading', { name: 'Anmelden' })).toBeInTheDocument();
  expect(screen.getByRole('alert')).toHaveTextContent('Du bist abgemeldet.');
});

test('zeigt eine verständliche Meldung bei falschem Passwort', async () => {
  vi.stubGlobal(
    'fetch',
    routeFetch({
      '/api/setup/status': () => jsonResponse({ setupRequired: false }),
      '/api/auth/me': () => problem('Bitte melde dich an.', 401, 'UNAUTHENTICATED'),
      '/api/auth/login': () => problem('Benutzername oder Passwort ist falsch.', 401, 'LOGIN_FAILED'),
    }),
  );

  renderApp();
  await screen.findByRole('heading', { name: 'Anmelden' });
  await userEvent.type(screen.getByTestId('login-username'), 'ada');
  await userEvent.type(screen.getByTestId('login-password'), 'falsches-passwort');
  await userEvent.click(screen.getByTestId('login-submit'));
  expect(await screen.findByRole('alert')).toHaveTextContent('Benutzername oder Passwort ist falsch.');
});

test('versteckt die Administration für Familienmitglieder', async () => {
  vi.stubGlobal(
    'fetch',
    routeFetch({
      '/api/setup/status': () => jsonResponse({ setupRequired: false }),
      '/api/auth/me': () => jsonResponse(member),
      '/api/status': () => jsonResponse({ status: 'UP', application: 'was-essen-wir' }),
    }),
  );

  renderApp('/benutzer');

  expect(await screen.findByText('Diese Seite ist nur für die Administration.')).toBeInTheDocument();
  expect(screen.queryByRole('link', { name: 'Familie' })).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Mehr' })).toBeInTheDocument();
});

test('legt ein Familienmitglied an', async () => {
  const created: CurrentUser = { ...member, passwordChangeRequired: true };
  vi.stubGlobal(
    'fetch',
    routeFetch({
      '/api/setup/status': () => jsonResponse({ setupRequired: false }),
      '/api/auth/me': () => jsonResponse(admin),
      '/api/users': () => jsonResponse([admin]),
      '/api/status': () => jsonResponse({ status: 'UP', application: 'was-essen-wir' }),
    }),
  );
  const fetchMock = vi.mocked(fetch);
  fetchMock.mockImplementation(async (input, init) => {
    const url = String(input);
    if (url.endsWith('/api/users') && init?.method === 'POST') {
      return jsonResponse(created, 201);
    }
    if (url.includes('/api/users')) {
      return jsonResponse([admin]);
    }
    if (url.includes('/api/setup/status')) {
      return jsonResponse({ setupRequired: false });
    }
    if (url.includes('/api/auth/me')) {
      return jsonResponse(admin);
    }
    return jsonResponse({ status: 'UP', application: 'was-essen-wir' });
  });

  renderApp('/benutzer');
  expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument();
  await userEvent.click(screen.getByTestId('new-member'));
  await userEvent.type(screen.getByTestId('member-username'), 'Bea');
  await userEvent.type(screen.getByTestId('member-display-name'), 'Bea Beispiel');
  await userEvent.type(screen.getByTestId('member-password'), 'mitglied-passwort');
  await userEvent.click(screen.getByTestId('member-save'));
  expect(await screen.findByText(/wurde angelegt/)).toBeInTheDocument();
});

test('ändert den eigenen Anzeigenamen', async () => {
  vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.includes('/api/auth/profile') && init?.method === 'PUT') {
      return jsonResponse({ ...admin, displayName: 'Ada Neu' });
    }
    if (url.includes('/api/setup/status')) {
      return jsonResponse({ setupRequired: false });
    }
    if (url.includes('/api/auth/me')) {
      return jsonResponse(admin);
    }
    return jsonResponse({ status: 'UP', application: 'was-essen-wir' });
  });

  renderApp('/profil');
  expect(await screen.findByRole('heading', { name: 'Mein Profil' })).toBeInTheDocument();
  const field = screen.getByTestId('profile-display-name');
  await userEvent.clear(field);
  await userEvent.type(field, 'Ada Neu');
  await userEvent.click(screen.getByRole('button', { name: 'Anzeigename speichern' }));
  expect(await screen.findByText('Der Anzeigename wurde gespeichert.')).toBeInTheDocument();
  expect(screen.getByTestId('current-user')).toHaveTextContent('Ada Neu');
});

test('hebt den aktiven Bereich in der Navigation hervor', async () => {
  vi.stubGlobal(
    'fetch',
    routeFetch({
      '/api/setup/status': () => jsonResponse({ setupRequired: false }),
      '/api/auth/me': () => jsonResponse(admin),
      '/api/status': () => jsonResponse({ status: 'UP', application: 'was-essen-wir' }),
    }),
  );

  renderApp();

  expect(await screen.findByRole('link', { name: 'Heute' })).toHaveClass('Mui-selected');
  await userEvent.click(screen.getByRole('link', { name: 'Plan' }));
  expect(await screen.findByRole('heading', { name: 'Wochenplan' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Plan' })).toHaveClass('Mui-selected');
  await userEvent.click(screen.getByRole('link', { name: 'Mehr' }));
  expect(await screen.findByRole('heading', { name: 'Mehr' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Familie' })).toBeInTheDocument();
  expect(screen.getByTestId('logout')).toBeInTheDocument();
});

test('leitet bei abgelaufener Sitzung zur Anmeldung', async () => {
  vi.stubGlobal('fetch', async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.includes('/api/dishes')) {
      return problem('Die Sitzung ist nicht mehr gültig.', 401, 'SESSION_INVALID');
    }
    if (url.includes('/api/setup/status')) {
      return jsonResponse({ setupRequired: false });
    }
    if (url.includes('/api/auth/me')) {
      return jsonResponse(admin);
    }
    return jsonResponse({ status: 'UP', application: 'was-essen-wir' });
  });

  renderApp('/gerichte');
  expect(await screen.findByRole('heading', { name: 'Anmelden' })).toBeInTheDocument();
  expect(screen.getByText('Deine Sitzung ist abgelaufen. Bitte melde dich erneut an.')).toBeInTheDocument();
});
