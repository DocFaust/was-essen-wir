import { ThemeProvider } from '@mui/material/styles';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, expect, test, vi } from 'vitest';
import { theme } from '../theme';
import { HistoryPage } from './HistoryPage';
import { shiftIsoDate, todayIso } from './dates';
import type { MealEvent } from './types';

const ada = { id: 'ada', displayName: 'Ada Lovelace' };
const bea = { id: 'bea', displayName: 'Bea Beispiel' };

function eaten(overrides: Partial<MealEvent> = {}): MealEvent {
  return {
    id: 'history-1',
    mealDate: shiftIsoDate(todayIso(), -1),
    mealType: 'MITTAGESSEN',
    status: 'EATEN',
    dishId: 'soup',
    variantId: null,
    dishName: 'Suppe',
    variantName: null,
    name: 'Suppe',
    comment: 'Rest',
    participants: [
      { userId: ada.id, displayName: ada.displayName },
      { userId: bea.id, displayName: bea.displayName },
    ],
    createdAt: '2026-10-09T12:00:00Z',
    updatedAt: '2026-10-09T12:00:00Z',
    ...overrides,
  };
}

function renderHistory() {
  return render(
    <ThemeProvider theme={theme}>
      <MemoryRouter>
        <HistoryPage userId={ada.id} />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  vi.unstubAllGlobals();
  document.cookie = 'XSRF-TOKEN=test-token';
});

test('zeigt die Historie, trägt nach, bearbeitet und löscht', async () => {
  const events = [eaten()];
  const fetchMock = mockApi(events);
  vi.stubGlobal('fetch', fetchMock);
  renderHistory();

  expect(await screen.findByRole('heading', { name: 'Was haben wir gegessen?' })).toBeInTheDocument();
  expect(await screen.findByTestId('history-entry')).toHaveTextContent('Suppe');
  expect(screen.getByTestId('history-entry')).toHaveTextContent('Bea Beispiel');
  expect(screen.queryByRole('table')).not.toBeInTheDocument();

  await userEvent.click(screen.getAllByTestId('history-diner')[0]);
  await userEvent.click(screen.getByTestId('history-apply'));
  expect(await screen.findByTestId('history-entry')).toBeInTheDocument();
  const filtered = fetchMock.mock.calls.map((call) => String(call[0])).filter((url) => url.includes('dinerId='));
  expect(filtered.at(-1)).toContain('dinerId=ada');

  await userEvent.click(screen.getByTestId('history-edit'));
  const comment = await screen.findByTestId('meal-comment');
  await userEvent.clear(comment);
  await userEvent.type(comment, 'Mit Brot');
  await userEvent.click(screen.getByTestId('meal-save'));
  expect(await screen.findByText('Mit Brot')).toBeInTheDocument();

  await userEvent.click(screen.getByTestId('history-create'));
  expect(await screen.findByRole('heading', { name: 'Als gegessen eintragen' })).toBeInTheDocument();
  await userEvent.click(await screen.findByRole('button', { name: 'Pizza' }));
  await userEvent.click(screen.getByTestId('meal-save'));
  expect(await screen.findByText('Pizza')).toBeInTheDocument();
  const soupEntry = screen.getAllByTestId('history-entry').find((entry) => entry.textContent?.includes('Suppe'));
  expect(soupEntry).toBeDefined();
  await userEvent.click(within(soupEntry as HTMLElement).getByTestId('history-delete'));
  await userEvent.click(screen.getByTestId('confirm-delete'));
  expect(await screen.findByText('Pizza')).toBeInTheDocument();
  expect(screen.queryByText('Suppe')).not.toBeInTheDocument();
});

test('zeigt einen Fehlerzustand der Historie', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/members')) {
        return json([ada]);
      }
      if (url.includes('/api/meal-history')) {
        return json({ title: 'Fehler', detail: 'Die Historie ist gerade nicht erreichbar.', status: 500 }, 500);
      }
      return json({ title: 'Fehler', detail: 'Unerwartet', status: 500 }, 500);
    }),
  );
  renderHistory();
  expect(await screen.findByText('Die Historie ist gerade nicht erreichbar.')).toBeInTheDocument();
});

function mockApi(events: MealEvent[]) {
  return vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = (init?.method ?? 'GET').toUpperCase();
    if (url.includes('/api/members')) {
      return json([ada, bea]);
    }
    if (url.includes('/api/dishes/pizza') || url.includes('/api/dishes/soup')) {
      const id = url.includes('pizza') ? 'pizza' : 'soup';
      return json({
        id,
        name: id === 'pizza' ? 'Pizza' : 'Suppe',
        mealTypes: ['ABENDESSEN'],
        variants: [],
        description: null,
        categories: [],
        effort: null,
        ingredients: [],
        recipeText: null,
        recipeUrl: null,
        recipeSource: null,
        comments: null,
        mealPrep: 'UNKNOWN',
        homeOffice: 'UNKNOWN',
        myRating: null,
      });
    }
    if (url.includes('/api/dishes')) {
      return json([
        { id: 'pizza', name: 'Pizza', mealTypes: ['ABENDESSEN'], effort: null, mealPrep: 'UNKNOWN', homeOffice: 'UNKNOWN', variantNames: [], myRating: null },
        { id: 'soup', name: 'Suppe', mealTypes: ['MITTAGESSEN'], effort: null, mealPrep: 'UNKNOWN', homeOffice: 'UNKNOWN', variantNames: [], myRating: null },
      ]);
    }
    if (url.includes('/api/meal-history/') && method === 'PUT') {
      const id = url.split('/').pop();
      const body = JSON.parse(String(init?.body)) as { comment: string | null; dishId: string; mealDate: string; mealType: MealEvent['mealType'] };
      const current = events.find((item) => item.id === id);
      if (current) {
        current.comment = body.comment;
        current.dishId = body.dishId;
        current.mealDate = body.mealDate;
        current.mealType = body.mealType;
        current.name = body.dishId === 'pizza' ? 'Pizza' : current.name;
        current.dishName = current.name;
      }
      return json(current);
    }
    if (url.includes('/api/meal-history/') && method === 'DELETE') {
      const id = url.split('/').pop();
      const index = events.findIndex((item) => item.id === id);
      if (index >= 0) {
        events.splice(index, 1);
      }
      return new Response(null, { status: 204 });
    }
    if (url.includes('/api/meal-history') && method === 'POST') {
      const body = JSON.parse(String(init?.body)) as { mealDate: string; mealType: MealEvent['mealType']; dishId: string; comment: string | null; dinerIds: string[] };
      const created = eaten({
        id: 'created',
        mealDate: body.mealDate,
        mealType: body.mealType,
        dishId: body.dishId,
        dishName: 'Pizza',
        name: 'Pizza',
        comment: body.comment,
        participants: [{ userId: ada.id, displayName: ada.displayName }],
      });
      events.unshift(created);
      return json(created, 201);
    }
    if (url.includes('/api/meal-history')) {
      const params = new URL(url, 'http://localhost').searchParams;
      const dinerId = params.get('dinerId');
      const from = params.get('from') ?? '';
      const to = params.get('to') ?? '';
      return json(
        events.filter((item) => {
          const inRange = item.mealDate >= from && item.mealDate <= to;
          const dinerMatches = !dinerId || item.participants.some((person) => person.userId === dinerId);
          return inRange && dinerMatches;
        }),
      );
    }
    return json({ title: 'Fehler', detail: 'Unerwartete Anfrage', status: 500 }, 500);
  });
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
