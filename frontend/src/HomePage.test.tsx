import { ThemeProvider } from '@mui/material/styles';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, expect, test, vi } from 'vitest';
import { AuthProvider } from './auth/AuthProvider';
import { HomePage } from './HomePage';
import { todayIso } from './meals/dates';
import { theme } from './theme';
import type { CurrentUser } from './auth/types';

const ada: CurrentUser = {
  id: 'ada',
  username: 'ada',
  displayName: 'Ada Lovelace',
  role: 'ADMIN',
  active: true,
  passwordChangeRequired: false,
};

beforeEach(() => {
  vi.unstubAllGlobals();
  document.cookie = 'XSRF-TOKEN=test-token';
});

test('zeigt einen leeren Tag, Standardwerte und Schnellaktionen ohne KI', async () => {
  stubHome({ plans: [], aiEnabled: false });
  renderHome();

  expect(await screen.findByRole('heading', { name: 'Was essen wir heute?' })).toBeInTheDocument();
  expect(await screen.findByTestId('today-empty')).toHaveTextContent('Heute ist noch nichts geplant.');
  expect(screen.getByTestId('quick-eaten')).toBeInTheDocument();
  expect(screen.getByTestId('quick-new-dish')).toHaveAttribute('href', '/gerichte/neu');
  expect(screen.getByTestId('quick-plan')).toHaveAttribute('href', '/plan');
  expect(screen.getByRole('button', { name: 'Abendessen' })).toHaveAttribute('aria-pressed', 'true');
  expect(screen.getByRole('button', { name: 'Ada Lovelace' })).toHaveAttribute('aria-pressed', 'true');
  expect(screen.queryByTestId('ai-assistant')).not.toBeInTheDocument();
});

test('bestätigt eine heutige Planung als gegessen', async () => {
  stubHome({
    plans: [
      {
        id: 'meal-1',
        mealDate: todayIso(),
        mealType: 'ABENDESSEN',
        status: 'PLANNED',
        dishId: 'pizza',
        variantId: null,
        dishName: 'Pizza',
        variantName: null,
        name: 'Pizza',
        comment: null,
        participants: [{ userId: 'ada', displayName: 'Ada Lovelace' }],
        createdAt: '2026-10-11T12:00:00Z',
        updatedAt: '2026-10-11T12:00:00Z',
      },
    ],
    aiEnabled: false,
  });
  renderHome();

  expect(await screen.findByTestId('today-entry')).toHaveTextContent('Pizza');
  await userEvent.click(screen.getByTestId('today-eaten'));
  await userEvent.click(screen.getByTestId('confirm-eaten'));
  expect(await screen.findByTestId('today-empty')).toBeInTheDocument();
  expect(screen.getByTestId('success-notice')).toHaveTextContent('Als gegessen bestätigt.');
});

test('öffnet das Erfassen mit heutigem Datum und ohne Kommentarfeld', async () => {
  stubHome({ plans: [], aiEnabled: false });
  renderHome();
  await screen.findByTestId('quick-eaten');
  await userEvent.click(screen.getByTestId('quick-eaten'));
  expect(await screen.findByRole('heading', { name: 'Als gegessen eintragen' })).toBeInTheDocument();
  expect(screen.getByTestId('meal-date')).toHaveValue(todayIso());
  expect(screen.getByRole('button', { name: 'Abendessen' })).toHaveAttribute('aria-pressed', 'true');
  expect(screen.queryByTestId('meal-comment')).not.toBeInTheDocument();
  expect(screen.getByTestId('meal-save')).toBeInTheDocument();
});

test('zeigt die KI-Eingabe nur, wenn sie aktiv ist', async () => {
  stubHome({ plans: [], aiEnabled: true });
  renderHome();
  expect(await screen.findByTestId('ai-assistant')).toBeInTheDocument();
  expect(screen.getByTestId('show-suggestions')).toBeInTheDocument();
});

function renderHome() {
  return render(
    <ThemeProvider theme={theme}>
      <MemoryRouter>
        <AuthProvider>
          <HomePage />
        </AuthProvider>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

function stubHome({
  plans,
  aiEnabled,
}: {
  plans: unknown[];
  aiEnabled: boolean;
}) {
  let plansLeft = plans;
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const method = (init?.method ?? 'GET').toUpperCase();
      if (url.includes('/api/setup/status')) {
        return json({ setupRequired: false });
      }
      if (url.includes('/api/auth/me')) {
        return json(ada);
      }
      if (url.includes('/api/members')) {
        return json([{ id: 'ada', displayName: 'Ada Lovelace' }]);
      }
      if (url.includes('/api/ai/status')) {
        return json({ enabled: aiEnabled, provider: aiEnabled ? 'fixture' : null });
      }
      if (url.includes('/api/meal-plans/') && url.endsWith('/eaten') && method === 'POST') {
        plansLeft = [];
        return json({ id: 'meal-1' });
      }
      if (url.includes('/api/meal-plans')) {
        return json(plansLeft);
      }
      if (url.includes('/api/dishes')) {
        return json([]);
      }
      return json({ title: 'Fehler', detail: 'Unerwartete Anfrage', status: 500 }, 500);
    }),
  );
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
