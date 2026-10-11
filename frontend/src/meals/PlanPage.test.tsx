import { ThemeProvider } from '@mui/material/styles';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, expect, test, vi } from 'vitest';
import { theme } from '../theme';
import { addDays, isoDate, startOfWeek, weekLabel } from './dates';
import { PlanPage } from './PlanPage';
import type { MealEvent } from './types';

const ada = { id: 'ada', displayName: 'Ada Lovelace' };
const bea = { id: 'bea', displayName: 'Bea Beispiel' };

const pizza = {
  id: 'pizza',
  name: 'Pizza',
  mealTypes: ['ABENDESSEN'],
  effort: 'EINFACH',
  mealPrep: 'UNKNOWN',
  homeOffice: 'UNKNOWN',
  variantNames: ['Salami'],
  myRating: null,
};

const pizzaDetail = {
  ...pizza,
  description: null,
  categories: [],
  ingredients: [],
  recipeText: null,
  recipeUrl: null,
  recipeSource: null,
  comments: null,
  variants: [
    {
      id: 'salami',
      name: 'Salami',
      description: null,
      effort: null,
      ingredientsSpecified: false,
      ingredients: [],
      recipeText: null,
      recipeUrl: null,
      recipeSource: null,
      mealPrep: 'INHERIT',
      homeOffice: 'INHERIT',
      resolved: {
        description: null,
        descriptionInherited: true,
        effort: 'EINFACH',
        effortInherited: true,
        ingredients: [],
        ingredientsInherited: true,
        recipeText: null,
        recipeTextInherited: true,
        recipeUrl: null,
        recipeUrlInherited: true,
        recipeSource: null,
        recipeSourceInherited: true,
        mealPrep: 'UNKNOWN',
        mealPrepInherited: true,
        homeOffice: 'UNKNOWN',
        homeOfficeInherited: true,
      },
      myRating: null,
    },
  ],
};

function event(overrides: Partial<MealEvent> = {}): MealEvent {
  return {
    id: 'meal-1',
    mealDate: isoDate(startOfWeek(new Date())),
    mealType: 'ABENDESSEN',
    status: 'PLANNED',
    dishId: 'pizza',
    variantId: 'salami',
    dishName: 'Pizza',
    variantName: 'Salami',
    name: 'Pizza · Salami',
    comment: null,
    participants: [{ userId: ada.id, displayName: ada.displayName }],
    createdAt: '2026-10-10T12:00:00Z',
    updatedAt: '2026-10-10T12:00:00Z',
    ...overrides,
  };
}

function renderPlan() {
  return render(
    <ThemeProvider theme={theme}>
      <MemoryRouter>
        <PlanPage userId={ada.id} />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  vi.unstubAllGlobals();
  document.cookie = 'XSRF-TOKEN=test-token';
});

test('wechselt die Wochen und bleibt vertikal aufgebaut', async () => {
  const events: MealEvent[] = [];
  vi.stubGlobal('fetch', mockApi(events));
  renderPlan();

  const monday = startOfWeek(new Date());
  expect(await screen.findByTestId('plan-week-label')).toHaveTextContent(weekLabel(monday));
  expect(screen.getAllByTestId('plan-day')).toHaveLength(7);
  expect(screen.queryByRole('table')).not.toBeInTheDocument();
  expect(screen.getByTestId('plan-create')).toHaveClass('MuiButton-fullWidth');
  expect(screen.getByTestId('plan-current-week')).toBeDisabled();

  await userEvent.click(screen.getByTestId('plan-next-week'));
  expect(screen.getByTestId('plan-week-label')).toHaveTextContent(weekLabel(addDays(monday, 7)));
  expect(screen.getByTestId('plan-current-week')).toBeEnabled();
  await userEvent.click(screen.getByTestId('plan-current-week'));
  expect(screen.getByTestId('plan-week-label')).toHaveTextContent(weekLabel(monday));
});

test('plant eine Mahlzeit mit Variante und Mitessenden, ändert und löscht sie', async () => {
  const events: MealEvent[] = [];
  const fetchMock = mockApi(events);
  vi.stubGlobal('fetch', fetchMock);
  renderPlan();
  await screen.findByTestId('plan-week-label');

  await userEvent.click(screen.getByTestId('plan-create'));
  expect(await screen.findByRole('heading', { name: 'Mahlzeit einplanen' })).toBeInTheDocument();
  await userEvent.click(await screen.findByRole('button', { name: 'Pizza' }));
  await userEvent.click(await screen.findByRole('button', { name: 'Salami' }));
  await userEvent.click(screen.getByRole('button', { name: 'Bea Beispiel' }));
  await userEvent.click(screen.getByTestId('meal-comment-toggle'));
  await userEvent.type(screen.getByTestId('meal-comment'), 'Knusprig');
  await userEvent.click(screen.getByTestId('meal-save'));

  expect(await screen.findByTestId('plan-entry')).toHaveTextContent('Pizza · Salami');
  expect(screen.getByTestId('plan-entry')).toHaveTextContent('Bea Beispiel');
  const post = fetchMock.mock.calls.find((call) => String(call[0]).includes('/api/meal-plans') && call[1]?.method === 'POST');
  const body = JSON.parse(String(post?.[1]?.body)) as { variantId: string; dinerIds: string[]; comment: string };
  expect(body.variantId).toBe('salami');
  expect(body.dinerIds).toEqual([ada.id, bea.id]);
  expect(body.comment).toBe('Knusprig');
  expect(new Headers(post?.[1]?.headers).get('X-XSRF-TOKEN')).toBe('test-token');

  await userEvent.click(screen.getByTestId('plan-edit'));
  const comment = await screen.findByTestId('meal-comment');
  await userEvent.clear(comment);
  await userEvent.type(comment, 'Mehr Käse');
  await userEvent.click(screen.getByTestId('meal-save'));
  expect(await screen.findByText('Mehr Käse')).toBeInTheDocument();

  await userEvent.click(screen.getByTestId('plan-delete'));
  await userEvent.click(screen.getByTestId('confirm-delete'));
  await waitFor(() => {
    expect(screen.queryByTestId('plan-entry')).not.toBeInTheDocument();
  });
});

test('bestätigt eine Planung als gegessen', async () => {
  const events = [event()];
  vi.stubGlobal('fetch', mockApi(events));
  renderPlan();
  const entry = await screen.findByTestId('plan-entry');
  await userEvent.click(within(entry).getByTestId('plan-eaten'));
  await userEvent.click(screen.getByTestId('confirm-eaten'));
  await waitFor(() => {
    expect(screen.queryByTestId('plan-entry')).not.toBeInTheDocument();
  });
  expect(events[0]?.status).toBe('EATEN');
});

test('verschiebt eine Planung innerhalb der Woche', async () => {
  const events = [event()];
  const fetchMock = mockApi(events);
  vi.stubGlobal('fetch', fetchMock);
  renderPlan();
  await userEvent.click(await screen.findByTestId('plan-move'));
  expect(await screen.findByRole('heading', { name: 'Mahlzeit verschieben' })).toBeInTheDocument();
  const next = isoDate(addDays(startOfWeek(new Date()), 1));
  fireEvent.change(screen.getByTestId('meal-date'), { target: { value: next } });
  await userEvent.click(screen.getByTestId('meal-save'));
  expect(await screen.findByTestId('success-notice')).toHaveTextContent('Verschoben.');
  const put = fetchMock.mock.calls.find((call) => String(call[0]).includes('/api/meal-plans/') && call[1]?.method === 'PUT');
  const body = JSON.parse(String(put?.[1]?.body)) as { mealDate: string };
  expect(body.mealDate).toBe(next);
});

test('zeigt einen Ladefehler und erlaubt einen neuen Versuch', async () => {
  let fail = true;
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/meal-plans')) {
        if (fail) {
          fail = false;
          return problem('Die Planung ist gerade nicht erreichbar.');
        }
        return json([]);
      }
      return problem('Unerwartet');
    }),
  );
  renderPlan();
  expect(await screen.findByText('Die Planung ist gerade nicht erreichbar.')).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Erneut versuchen' }));
  expect(await screen.findAllByText('Noch nichts geplant')).not.toHaveLength(0);
});

function mockApi(events: MealEvent[]) {
  return vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = (init?.method ?? 'GET').toUpperCase();
    if (url.includes('/api/members')) {
      return json([ada, bea]);
    }
    if (url.includes('/api/dishes/pizza')) {
      return json(pizzaDetail);
    }
    if (url.includes('/api/dishes')) {
      return json([pizza]);
    }
    if (url.includes('/api/meal-plans/') && url.endsWith('/eaten') && method === 'POST') {
      const id = url.split('/').at(-2);
      const current = events.find((item) => item.id === id);
      if (current) {
        current.status = 'EATEN';
      }
      return json(current);
    }
    if (url.includes('/api/meal-plans/') && method === 'PUT') {
      const id = url.split('/').pop();
      const body = JSON.parse(String(init?.body)) as {
        mealDate: string;
        mealType: MealEvent['mealType'];
        comment: string | null;
        dinerIds: string[];
        variantId: string | null;
      };
      const current = events.find((item) => item.id === id);
      if (current) {
        current.comment = body.comment;
        current.mealDate = body.mealDate;
        current.mealType = body.mealType;
        current.variantId = body.variantId;
      }
      return json(current);
    }
    if (url.includes('/api/meal-plans/') && method === 'DELETE') {
      const id = url.split('/').pop();
      const index = events.findIndex((item) => item.id === id);
      if (index >= 0) {
        events.splice(index, 1);
      }
      return new Response(null, { status: 204 });
    }
    if (url.includes('/api/meal-plans') && method === 'POST') {
      const body = JSON.parse(String(init?.body)) as {
        mealDate: string;
        mealType: MealEvent['mealType'];
        dishId: string;
        variantId: string | null;
        dinerIds: string[];
        comment: string | null;
      };
      const created = event({
        id: 'created',
        mealDate: body.mealDate,
        mealType: body.mealType,
        variantId: body.variantId,
        comment: body.comment,
        participants: body.dinerIds.map((id) => ({
          userId: id,
          displayName: id === bea.id ? bea.displayName : ada.displayName,
        })),
      });
      events.push(created);
      return json(created, 201);
    }
    if (url.includes('/api/meal-plans')) {
      const params = new URL(url, 'http://localhost').searchParams;
      const from = params.get('from') ?? '';
      const to = params.get('to') ?? '';
      return json(events.filter((item) => item.status === 'PLANNED' && item.mealDate >= from && item.mealDate <= to));
    }
    return problem('Unerwartete Anfrage');
  });
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function problem(detail: string): Response {
  return json({ title: 'Fehler', detail, status: 500 }, 500);
}
