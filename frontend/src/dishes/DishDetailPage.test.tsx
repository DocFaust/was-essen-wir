import { ThemeProvider } from '@mui/material/styles';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { expect, test, vi } from 'vitest';
import { theme } from '../theme';
import { DishDetailPage } from './DishDetailPage';
import { VariantFormPage } from './VariantFormPage';
import type { DishDetail } from './types';

const dish: DishDetail = {
  id: 'dish-1',
  name: 'Pizza',
  description: null,
  mealTypes: ['ABENDESSEN'],
  categories: [],
  effort: 'MITTEL',
  ingredients: ['Teig'],
  recipeText: null,
  recipeUrl: null,
  recipeSource: 'Chefkoch',
  comments: null,
  mealPrep: 'SUITABLE',
  homeOffice: 'UNKNOWN',
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
      homeOffice: 'UNSUITABLE',
      resolved: {
        description: null,
        descriptionInherited: true,
        effort: 'MITTEL',
        effortInherited: true,
        ingredients: ['Teig'],
        ingredientsInherited: true,
        recipeText: null,
        recipeTextInherited: true,
        recipeUrl: null,
        recipeUrlInherited: true,
        recipeSource: 'Chefkoch',
        recipeSourceInherited: true,
        mealPrep: 'SUITABLE',
        mealPrepInherited: true,
        homeOffice: 'UNSUITABLE',
        homeOfficeInherited: false,
      },
      myRating: null,
    },
  ],
  myRating: null,
};

test('zeigt die übernommene Rezeptquelle einer Variante', async () => {
  vi.stubGlobal('fetch', async () => json(dish));

  render(
    <ThemeProvider theme={theme}>
      <MemoryRouter initialEntries={['/gerichte/dish-1']}>
        <Routes>
          <Route path="/gerichte/:id" element={<DishDetailPage />} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );

  expect(await screen.findByRole('heading', { name: 'Pizza' })).toBeInTheDocument();
  expect(screen.getByTestId('dish-recipe-source-value')).toHaveTextContent('Chefkoch');
  expect(screen.getByTestId('resolved-recipe-source')).toHaveTextContent('Chefkoch · vom Grundgericht');
  expect(screen.getByText('Nicht geeignet')).toBeInTheDocument();
});

test('zeigt einen Fehler, wenn das Gericht fehlt', async () => {
  vi.stubGlobal('fetch', async () => json({ detail: 'Gericht wurde nicht gefunden.' }, 404));

  render(
    <ThemeProvider theme={theme}>
      <MemoryRouter initialEntries={['/gerichte/missing']}>
        <Routes>
          <Route path="/gerichte/:id" element={<DishDetailPage />} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );

  expect(await screen.findByRole('alert')).toHaveTextContent('Gericht wurde nicht gefunden.');
});

test('verlangt einen Namen für eine Variante', async () => {
  const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(json(dish));
  vi.stubGlobal('fetch', fetchMock);

  render(
    <ThemeProvider theme={theme}>
      <MemoryRouter initialEntries={['/gerichte/dish-1/varianten/neu']}>
        <Routes>
          <Route path="/gerichte/:id/varianten/neu" element={<VariantFormPage />} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );

  expect(await screen.findByText('Grundgericht: Pizza')).toBeInTheDocument();
  await userEvent.click(screen.getByTestId('save-variant'));

  expect(screen.getByRole('alert')).toHaveTextContent('Der Name ist erforderlich.');
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
