import { ThemeProvider } from '@mui/material/styles';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { expect, test, vi } from 'vitest';
import { theme } from '../theme';
import { DishListPage } from './DishListPage';
import type { DishSummary } from './types';

const dishes: DishSummary[] = [
  {
    id: 'dish-1',
    name: 'Pizza',
    mealTypes: ['ABENDESSEN'],
    effort: 'MITTEL',
    mealPrep: 'UNKNOWN',
    homeOffice: 'SUITABLE',
    variantNames: ['Salami'],
    myRating: null,
  },
];

function renderPage() {
  return render(
    <ThemeProvider theme={theme}>
      <MemoryRouter>
        <DishListPage />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

test('zeigt Gerichte und sucht nach dem Namen', async () => {
  const fetchMock = vi.fn<typeof fetch>().mockResolvedValue(json(dishes));
  vi.stubGlobal('fetch', fetchMock);

  renderPage();

  expect(await screen.findByRole('link', { name: /Pizza/ })).toBeInTheDocument();
  await userEvent.type(screen.getByTestId('dish-search'), 'Pizza');
  await userEvent.click(screen.getByRole('button', { name: 'Suchen' }));

  expect(fetchMock).toHaveBeenLastCalledWith(
    '/api/dishes?q=Pizza',
    expect.objectContaining({ headers: expect.any(Object) }),
  );
});

test('zeigt den Leerzustand', async () => {
  vi.stubGlobal('fetch', async () => json([]));

  renderPage();

  expect(await screen.findByText('Noch keine Gerichte. Ein Name genügt für den Anfang.')).toBeInTheDocument();
});

test('zeigt einen Fehler, wenn die Liste nicht geladen werden kann', async () => {
  vi.stubGlobal('fetch', async () => {
    throw new Error('offline');
  });

  renderPage();

  expect(await screen.findByRole('alert')).toHaveTextContent('Das Backend ist gerade nicht erreichbar.');
});

function json(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}
