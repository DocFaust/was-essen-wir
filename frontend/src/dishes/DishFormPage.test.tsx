import { ThemeProvider } from '@mui/material/styles';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { expect, test, vi } from 'vitest';
import { theme } from '../theme';
import { DishFormPage } from './DishFormPage';
import type { DishDetail } from './types';

const saved: DishDetail = {
  id: 'dish-1',
  name: 'Linsensuppe',
  description: null,
  mealTypes: [],
  categories: [],
  effort: null,
  ingredients: ['Linsen'],
  recipeText: null,
  recipeUrl: null,
  recipeSource: 'Eigenkreation',
  comments: null,
  mealPrep: 'UNKNOWN',
  homeOffice: 'UNKNOWN',
  myRating: null,
  variants: [],
};

function renderCreate() {
  return render(
    <ThemeProvider theme={theme}>
      <MemoryRouter initialEntries={['/gerichte/neu']}>
        <Routes>
          <Route path="/gerichte/neu" element={<DishFormPage />} />
          <Route path="/gerichte/:id" element={<p>Gespeichert</p>} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

test('verlangt einen Namen', async () => {
  const fetchMock = vi.fn<typeof fetch>(async (input) => {
    if (String(input).includes('/api/ai/status')) {
      return json({ enabled: false, provider: null });
    }
    return json({});
  });
  vi.stubGlobal('fetch', fetchMock);

  renderCreate();
  await userEvent.click(screen.getByTestId('save-dish'));

  expect(screen.getByRole('alert')).toHaveTextContent('Der Name ist erforderlich.');
  expect(fetchMock.mock.calls.some((call) => String(call[0]).includes('/api/dishes'))).toBe(false);
});

test('legt ein Gericht mit Rezeptquelle und Zutat an', async () => {
  const fetchMock = vi.fn<typeof fetch>(async (input) => {
    if (String(input).includes('/api/ai/status')) {
      return json({ enabled: false, provider: null });
    }
    return json(saved, 201);
  });
  vi.stubGlobal('fetch', fetchMock);

  renderCreate();
  await userEvent.type(screen.getByTestId('dish-name'), 'Linsensuppe');
  await userEvent.click(screen.getByRole('button', { name: 'Weitere Angaben' }));
  await userEvent.type(screen.getByTestId('dish-recipe-source'), 'Eigenkreation');
  await userEvent.type(screen.getByTestId('ingredient-name'), 'Linsen');
  await userEvent.click(screen.getByRole('button', { name: 'Zutat hinzufügen' }));
  await userEvent.click(screen.getByTestId('save-dish'));

  expect(await screen.findByText('Gespeichert')).toBeInTheDocument();
  const post = fetchMock.mock.calls.find((call) => call[1]?.method === 'POST');
  const body = JSON.parse(String(post?.[1]?.body));
  expect(body).toMatchObject({
    name: 'Linsensuppe',
    recipeSource: 'Eigenkreation',
    ingredients: ['Linsen'],
    confirmDuplicate: false,
  });
});

test('warnt vor einem gleichnamigen Gericht und speichert erst nach Bestätigung', async () => {
  const posts: string[] = [];
  const fetchMock = vi.fn<typeof fetch>(async (input, init) => {
    if (String(input).includes('/api/ai/status')) {
      return json({ enabled: false, provider: null });
    }
    posts.push(String(init?.body));
    if (posts.length === 1) {
      return json({ detail: 'Es gibt bereits ein Gericht mit diesem Namen.', code: 'DUPLICATE_DISH_NAME' }, 409);
    }
    return json(saved, 201);
  });
  vi.stubGlobal('fetch', fetchMock);

  renderCreate();
  await userEvent.type(screen.getByTestId('dish-name'), 'Linsensuppe');
  await userEvent.click(screen.getByTestId('save-dish'));

  expect(await screen.findByRole('dialog')).toHaveTextContent('Trotzdem speichern?');
  await userEvent.click(screen.getByRole('button', { name: 'Trotzdem speichern' }));

  expect(await screen.findByText('Gespeichert')).toBeInTheDocument();
  const body = JSON.parse(posts[1]);
  expect(body.confirmDuplicate).toBe(true);
});

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
