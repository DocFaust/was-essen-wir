import { ThemeProvider } from '@mui/material/styles';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { beforeEach, expect, test, vi } from 'vitest';
import { App } from '../App';
import type { CurrentUser } from '../auth/types';
import { theme } from '../theme';
import { DishDetailPage } from './DishDetailPage';
import { DishListPage } from './DishListPage';
import type { DishDetail, DishSummary } from './types';

const admin: CurrentUser = {
  id: 'admin-1',
  username: 'ada',
  displayName: 'Ada Lovelace',
  role: 'ADMIN',
  active: true,
  passwordChangeRequired: false,
};

const member: CurrentUser = {
  id: 'member-1',
  username: 'bea',
  displayName: 'Bea Beispiel',
  role: 'MEMBER',
  active: true,
  passwordChangeRequired: false,
};

const summary: DishSummary = {
  id: 'dish-1',
  name: 'Pizza',
  mealTypes: ['ABENDESSEN'],
  effort: 'MITTEL',
  mealPrep: 'UNKNOWN',
  homeOffice: 'SUITABLE',
  variantNames: ['Margherita', 'Salami'],
  myRating: 4,
};

const detail: DishDetail = {
  id: 'dish-1',
  name: 'Pizza',
  description: null,
  mealTypes: ['ABENDESSEN'],
  categories: [],
  effort: 'MITTEL',
  ingredients: [],
  recipeText: null,
  recipeUrl: null,
  recipeSource: null,
  comments: null,
  mealPrep: 'UNKNOWN',
  homeOffice: 'UNKNOWN',
  myRating: 4,
  variants: [
    {
      id: 'variant-1',
      name: 'Margherita',
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
        effort: 'MITTEL',
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
    {
      id: 'variant-2',
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
        effort: 'MITTEL',
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

function renderList(dishes: DishSummary[]) {
  vi.stubGlobal('fetch', vi.fn<typeof fetch>().mockResolvedValue(json(dishes)));
  return render(
    <ThemeProvider theme={theme}>
      <MemoryRouter>
        <DishListPage />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

function renderDetail(body: DishDetail, fetchMock?: ReturnType<typeof vi.fn<typeof fetch>>) {
  vi.stubGlobal('fetch', fetchMock ?? vi.fn<typeof fetch>().mockResolvedValue(json(body)));
  return render(
    <ThemeProvider theme={theme}>
      <MemoryRouter initialEntries={['/gerichte/dish-1']}>
        <Routes>
          <Route path="/gerichte/:id" element={<DishDetailPage />} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  localStorage.clear();
  document.cookie = 'XSRF-TOKEN=test-token';
});

test('zeigt vorhandene und fehlende Bewertungen ohne Vorbefüllung', async () => {
  renderList([{ ...summary, myRating: 4 }, { ...summary, id: 'dish-2', name: 'Suppe', myRating: null, variantNames: [] }]);

  const pizza = await screen.findByTestId('list-rating-dish-1');
  const soup = screen.getByTestId('list-rating-dish-2');
  expect(within(pizza).getByRole('radio', { name: '4 Sterne, Esse ich gerne' })).toBeChecked();
  expect(within(pizza).getByTestId('list-rating-dish-1-status')).toHaveTextContent('Esse ich gerne');
  expect(within(soup).getByTestId('list-rating-dish-2-status')).toHaveTextContent('Nicht bewertet');
  expect(within(soup).getByRole('radio', { name: '3 Sterne, Ist okay' })).not.toBeChecked();
});

test('setzt, ändert und entfernt eine Bewertung per Klick und Tastatur', async () => {
  const fetchMock = vi.fn<typeof fetch>(async (input, init) => {
    const url = String(input);
    const method = (init?.method ?? 'GET').toUpperCase();
    if (method === 'PUT' && url.endsWith('/rating')) {
      const body = JSON.parse(String(init?.body)) as { stars: number };
      return json({ stars: body.stars });
    }
    if (method === 'DELETE') {
      return new Response(null, { status: 204 });
    }
    return json([{ ...summary, myRating: null }]);
  });
  vi.stubGlobal('fetch', fetchMock);

  render(
    <ThemeProvider theme={theme}>
      <MemoryRouter>
        <DishListPage />
      </MemoryRouter>
    </ThemeProvider>,
  );

  const group = await screen.findByTestId('list-rating-dish-1');
  await chooseStar(group, '4 Sterne, Esse ich gerne');
  await waitFor(() => expect(within(group).getByTestId('list-rating-dish-1-status')).toHaveTextContent('Esse ich gerne'));

  within(group).getByRole('radio', { name: '1 Stern, Mag ich überhaupt nicht' }).focus();
  await userEvent.keyboard('{ArrowRight}');
  await waitFor(() => expect(within(group).getByTestId('list-rating-dish-1-status')).toHaveTextContent('Esse ich ungern'));

  await userEvent.click(within(group).getByRole('button', { name: /entfernen/ }));
  await waitFor(() => expect(within(group).getByTestId('list-rating-dish-1-status')).toHaveTextContent('Nicht bewertet'));
  expect(localStorage).toHaveLength(0);
  expect(fetchMock).toHaveBeenCalledWith(
    '/api/dishes/dish-1/rating',
    expect.objectContaining({ method: 'PUT', cache: 'no-store' }),
  );
});

test('stellt die vorherige Bewertung nach einem Speicherfehler wieder her', async () => {
  const fetchMock = vi.fn<typeof fetch>(async (_input, init) => {
    const method = (init?.method ?? 'GET').toUpperCase();
    if (method === 'PUT') {
      return json({ detail: 'Die Bewertung konnte nicht gespeichert werden.' }, 500);
    }
    return json([{ ...summary, myRating: 4 }]);
  });
  vi.stubGlobal('fetch', fetchMock);

  render(
    <ThemeProvider theme={theme}>
      <MemoryRouter>
        <DishListPage />
      </MemoryRouter>
    </ThemeProvider>,
  );

  const group = await screen.findByTestId('list-rating-dish-1');
  await chooseStar(group, '1 Stern, Mag ich überhaupt nicht');

  expect(await within(group).findByRole('alert')).toHaveTextContent('Die Bewertung konnte nicht gespeichert werden.');
  expect(within(group).getByRole('radio', { name: '4 Sterne, Esse ich gerne' })).toBeChecked();
  expect(within(group).getByTestId('list-rating-dish-1-status')).toHaveTextContent('Esse ich gerne');
});

test('bewertet Varianten unabhängig vom Grundgericht', async () => {
  const fetchMock = vi.fn<typeof fetch>(async (input, init) => {
    const url = String(input);
    const method = (init?.method ?? 'GET').toUpperCase();
    if (method === 'PUT' && url.includes('/variants/variant-1/rating')) {
      return json({ stars: 5 });
    }
    return json(detail);
  });
  renderDetail(detail, fetchMock);

  expect(await screen.findByTestId('dish-rating-status')).toHaveTextContent('Esse ich gerne');
  const margherita = screen.getByTestId('variant-rating-variant-1');
  const salami = screen.getByTestId('variant-rating-variant-2');
  expect(within(margherita).getByTestId('variant-rating-variant-1-status')).toHaveTextContent('Nicht bewertet');
  expect(within(salami).getByTestId('variant-rating-variant-2-status')).toHaveTextContent('Nicht bewertet');

  await chooseStar(margherita, '5 Sterne, Lieblingsessen');

  await waitFor(() =>
    expect(within(margherita).getByTestId('variant-rating-variant-1-status')).toHaveTextContent('Lieblingsessen'),
  );
  expect(screen.getByTestId('dish-rating-status')).toHaveTextContent('Esse ich gerne');
  expect(within(salami).getByTestId('variant-rating-variant-2-status')).toHaveTextContent('Nicht bewertet');
});

test('zeigt nach einem Benutzerwechsel nur die Bewertung der angemeldeten Person', async () => {
  let session = admin;
  let adaStars: number | null = 4;
  let beaStars: number | null = null;
  const fetchMock = vi.fn<typeof fetch>(async (input, init) => {
    const url = String(input);
    const method = (init?.method ?? 'GET').toUpperCase();
    if (url.includes('/api/setup/status')) {
      return json({ setupRequired: false });
    }
    if (url.includes('/api/auth/me')) {
      return json(session);
    }
    if (url.includes('/api/auth/logout')) {
      return new Response(null, { status: 204 });
    }
    if (url.includes('/api/auth/login')) {
      const body = JSON.parse(String(init?.body)) as { username: string };
      session = body.username === 'bea' ? member : admin;
      return json(session);
    }
    if (method === 'PUT' && url.endsWith('/rating')) {
      const body = JSON.parse(String(init?.body)) as { stars: number };
      if (session.id === member.id) {
        beaStars = body.stars;
      } else {
        adaStars = body.stars;
      }
      return json({ stars: body.stars });
    }
    if (url.includes('/api/dishes')) {
      return json([{ ...summary, myRating: session.id === admin.id ? adaStars : beaStars }]);
    }
    return json({ detail: 'Unerwartete Anfrage' }, 500);
  });
  vi.stubGlobal('fetch', fetchMock);

  render(
    <ThemeProvider theme={theme}>
      <MemoryRouter initialEntries={['/gerichte']}>
        <App />
      </MemoryRouter>
    </ThemeProvider>,
  );

  expect(await screen.findByTestId('list-rating-dish-1-status')).toHaveTextContent('Esse ich gerne');
  await userEvent.click(screen.getByTestId('logout'));
  await screen.findByRole('heading', { name: 'Anmelden' });
  await userEvent.type(screen.getByTestId('login-username'), 'bea');
  await userEvent.type(screen.getByTestId('login-password'), 'ein-langes-passwort');
  await userEvent.click(screen.getByTestId('login-submit'));

  expect(await screen.findByTestId('list-rating-dish-1-status')).toHaveTextContent('Nicht bewertet');
  expect(screen.getByRole('radio', { name: '4 Sterne, Esse ich gerne' })).not.toBeChecked();
  await chooseStar(screen.getByTestId('list-rating-dish-1'), '1 Stern, Mag ich überhaupt nicht');
  await waitFor(() => expect(screen.getByTestId('list-rating-dish-1-status')).toHaveTextContent('Mag ich überhaupt nicht'));

  await userEvent.click(screen.getByTestId('logout'));
  await screen.findByRole('heading', { name: 'Anmelden' });
  await userEvent.type(screen.getByTestId('login-username'), 'ada');
  await userEvent.type(screen.getByTestId('login-password'), 'ein-langes-passwort');
  await userEvent.click(screen.getByTestId('login-submit'));

  expect(await screen.findByTestId('list-rating-dish-1-status')).toHaveTextContent('Esse ich gerne');
  expect(screen.getByRole('radio', { name: '1 Stern, Mag ich überhaupt nicht' })).not.toBeChecked();
  expect(localStorage).toHaveLength(0);
  expect(fetchMock.mock.calls.filter(([url]) => String(url).includes('/api/dishes')).length).toBeGreaterThanOrEqual(3);
});

function chooseStar(container: HTMLElement, name: string) {
  const radio = within(container).getByRole('radio', { name }) as HTMLInputElement;
  // jsdom kennt keine Sternkoordinaten. Der native Radio-Klick löst dieselbe Änderung aus wie ein Tipp auf das Label.
  radio.click();
}

function json(body: unknown, status = 200): Response {
  return new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
