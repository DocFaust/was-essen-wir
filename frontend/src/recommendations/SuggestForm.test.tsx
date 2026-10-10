import { ThemeProvider } from '@mui/material/styles';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, expect, test, vi } from 'vitest';
import { theme } from '../theme';
import { SuggestForm } from './SuggestForm';
import type { RecommendationResponse, Suggestion } from './types';

const ada = { id: 'ada', displayName: 'Ada Lovelace' };
const bea = { id: 'bea', displayName: 'Bea Beispiel' };

const suggestions: Suggestion[] = [
  {
    dishId: 'dish-pizza',
    variantId: 'variant-margherita',
    name: 'Pizza · Margherita',
    dishName: 'Pizza',
    variantName: 'Margherita',
    reason: 'Passt gut zu den Vorlieben der ausgewählten Personen.',
    effort: 'MITTEL',
    mealPrep: 'UNKNOWN',
    homeOffice: 'SUITABLE',
  },
  {
    dishId: 'dish-gratin',
    variantId: null,
    name: 'Kartoffelgratin',
    dishName: 'Kartoffelgratin',
    variantName: null,
    reason: 'Wird von mehreren Mitessenden gerne gegessen.',
    effort: 'AUFWENDIG',
    mealPrep: 'SUITABLE',
    homeOffice: 'UNKNOWN',
  },
  {
    dishId: 'dish-pan',
    variantId: null,
    name: 'Gemüsepfanne',
    dishName: 'Gemüsepfanne',
    variantName: null,
    reason: 'Eine abwechslungsreiche Alternative mit geringem Aufwand.',
    effort: 'EINFACH',
    mealPrep: 'UNKNOWN',
    homeOffice: 'UNKNOWN',
  },
];

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

test('wählt Mahlzeit, Mitessende und Präferenzen und zeigt drei Vorschläge', async () => {
  const fetchMock = mockFetch({ members: [ada, bea], recommendation: { suggestions, explanation: null } });
  renderForm();

  expect(await screen.findByRole('button', { name: 'Ada Lovelace' })).toHaveAttribute('aria-pressed', 'true');
  expect(screen.getByRole('button', { name: 'Abendessen' })).toHaveAttribute('aria-pressed', 'true');
  await userEvent.click(screen.getByRole('button', { name: 'Frühstück' }));
  await userEvent.click(screen.getByRole('button', { name: 'Bea Beispiel' }));
  await userEvent.click(screen.getByRole('switch', { name: 'Einfaches oder schnelles Essen' }));
  await userEvent.click(screen.getByRole('switch', { name: 'Für Homeoffice geeignet' }));
  await userEvent.click(screen.getByRole('switch', { name: 'Für Meal Prep geeignet' }));
  await userEvent.click(screen.getByTestId('show-suggestions'));

  expect(await screen.findByText('Pizza · Margherita')).toBeInTheDocument();
  expect(screen.getByText('Kartoffelgratin')).toBeInTheDocument();
  expect(screen.getByText('Gemüsepfanne')).toBeInTheDocument();
  expect(screen.getByText('Passt gut zu den Vorlieben der ausgewählten Personen.')).toBeInTheDocument();
  expect(screen.getByText('Aufwand: Einfach')).toBeInTheDocument();
  expect(screen.getByText('Homeoffice')).toBeInTheDocument();
  expect(screen.getAllByTestId('suggestion-card')).toHaveLength(3);
  expect(screen.queryByText(/Stern/)).not.toBeInTheDocument();
  expect(screen.getAllByRole('link', { name: 'Gericht öffnen' })[0]).toHaveAttribute('href', '/gerichte/dish-pizza');
  expect(screen.getByTestId('show-suggestions')).toHaveClass('MuiButton-fullWidth');

  const recommendationCall = fetchMock.mock.calls.find((call) => String(call[0]).includes('/api/recommendations'));
  expect(recommendationCall?.[1]).toEqual(expect.objectContaining({ method: 'POST' }));
  expect(new Headers(recommendationCall?.[1]?.headers).get('X-XSRF-TOKEN')).toBe('test-token');
  const body = JSON.parse(String(recommendationCall?.[1]?.body)) as {
    mealType: string;
    dinerIds: string[];
    preferSimple: boolean;
    preferHomeOffice: boolean;
    preferMealPrep: boolean;
    excludedDishIds: string[];
  };
  expect(body.mealType).toBe('FRUEHSTUECK');
  expect(body.dinerIds).toEqual(['ada', 'bea']);
  expect(body.preferSimple).toBe(true);
  expect(body.preferHomeOffice).toBe(true);
  expect(body.preferMealPrep).toBe(true);
  expect(body.excludedDishIds).toEqual([]);
});

test('fordert neue Vorschläge an und schließt ein Gericht aus', async () => {
  const fetchMock = mockFetch({ members: [ada, bea], recommendation: { suggestions, explanation: null } });
  renderForm();
  await screen.findByRole('button', { name: 'Ada Lovelace' });
  await userEvent.click(screen.getByTestId('show-suggestions'));
  await screen.findByText('Pizza · Margherita');

  await userEvent.click(screen.getByTestId('suggest-again'));
  expect(fetchMock.mock.calls.filter((call) => String(call[0]).includes('/api/recommendations'))).toHaveLength(2);

  await userEvent.click(screen.getAllByTestId('exclude-suggestion')[0]);
  const calls = fetchMock.mock.calls.filter((call) => String(call[0]).includes('/api/recommendations'));
  const excluded = JSON.parse(String(calls.at(-1)?.[1]?.body)) as { excludedDishIds: string[]; dinerIds: string[] };
  expect(excluded.excludedDishIds).toEqual(['dish-pizza']);
  expect(excluded.dinerIds).toEqual(['ada']);
  expect(await screen.findByText('Pizza ist ausgeschlossen.')).toBeInTheDocument();
});

test('zeigt eine Erklärung, wenn es keine Vorschläge gibt', async () => {
  mockFetch({
    members: [ada],
    recommendation: {
      suggestions: [],
      explanation: 'Für diese Mahlzeit gibt es noch keine passenden Gerichte.',
    },
  });
  renderForm();
  await screen.findByRole('button', { name: 'Ada Lovelace' });
  await userEvent.click(screen.getByTestId('show-suggestions'));

  expect(await screen.findByTestId('suggestion-explanation')).toHaveTextContent(
    'Für diese Mahlzeit gibt es noch keine passenden Gerichte.',
  );
  expect(screen.queryByTestId('suggestion-card')).not.toBeInTheDocument();
});

test('verlangt mindestens eine Person und zeigt Fehler', async () => {
  mockFetch({
    members: [ada, bea],
    recommendation: { suggestions, explanation: null },
    failRecommendation: true,
  });
  renderForm();
  const adaButton = await screen.findByRole('button', { name: 'Ada Lovelace' });
  await userEvent.click(adaButton);
  expect(screen.getByRole('alert')).toHaveTextContent('Mindestens eine Person muss ausgewählt sein.');
  expect(screen.getByTestId('show-suggestions')).toBeDisabled();

  await userEvent.click(adaButton);
  await userEvent.click(screen.getByTestId('show-suggestions'));
  expect(await screen.findByRole('alert')).toHaveTextContent('Die Vorschläge sind gerade nicht verfügbar.');
});

test('zeigt einen Fehler, wenn die Mitessenden nicht geladen werden können', async () => {
  vi.stubGlobal('fetch', vi.fn(async () => {
    throw new Error('offline');
  }));
  renderForm();
  expect(await screen.findByRole('alert')).toHaveTextContent('Das Backend ist gerade nicht erreichbar.');
});

function renderForm() {
  return render(
    <ThemeProvider theme={theme}>
      <MemoryRouter>
        <SuggestForm userId="ada" />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

function mockFetch(options: {
  members: { id: string; displayName: string }[];
  recommendation: RecommendationResponse;
  failRecommendation?: boolean;
}) {
  const fetchMock = vi.fn<typeof fetch>(async (input) => {
    const url = String(input);
    if (url.includes('/api/members')) {
      return json(options.members);
    }
    if (url.includes('/api/recommendations')) {
      if (options.failRecommendation) {
        return json({ title: 'Fehler', detail: 'Die Vorschläge sind gerade nicht verfügbar.', status: 500 }, 500);
      }
      return json(options.recommendation);
    }
    return json({ title: 'Fehler', detail: 'Unerwartete Anfrage', status: 500 }, 500);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
