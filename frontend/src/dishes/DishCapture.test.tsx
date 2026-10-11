import { ThemeProvider } from '@mui/material/styles';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { expect, test, vi } from 'vitest';
import { theme } from '../theme';
import { DishFormPage } from './DishFormPage';
import { VariantFormPage } from './VariantFormPage';
import type { DishDetail } from './types';
import type { DishDraft } from './draft';

const dish: DishDetail = {
  id: 'dish-1',
  name: 'Lasagne',
  description: null,
  mealTypes: ['ABENDESSEN'],
  categories: [],
  effort: 'MITTEL',
  ingredients: ['Hackfleisch', 'Tomaten'],
  recipeText: null,
  recipeUrl: null,
  recipeSource: null,
  comments: null,
  mealPrep: 'SUITABLE',
  homeOffice: 'UNKNOWN',
  myRating: null,
  variants: [],
};

const draft: DishDraft = {
  schemaVersion: 1,
  outcome: 'DRAFT',
  kind: 'DISH',
  baseDishId: null,
  baseDishName: null,
  name: 'Kartoffelgratin',
  description: null,
  mealTypes: [],
  categories: [],
  effort: 'MITTEL',
  ingredients: [],
  ingredientsSpecified: null,
  recipeText: null,
  recipeUrl: null,
  recipeSource: null,
  comments: null,
  mealPrep: true,
  homeOffice: null,
  recognized: ['name', 'effort', 'mealPrep'],
  unknown: ['mealTypes', 'ingredients', 'homeOffice'],
  warnings: ['Die genannte Dauer entspricht dem Aufwand Mittel.'],
  duplicates: [
    {
      dishId: 'dish-9',
      variantId: null,
      name: 'Kartoffelgratin',
      message: 'Ein ähnliches Gericht existiert bereits: Kartoffelgratin.',
    },
  ],
  candidates: [],
};

function renderDish() {
  return render(
    <ThemeProvider theme={theme}>
      <MemoryRouter initialEntries={['/gerichte/neu']}>
        <Routes>
          <Route path="/gerichte/neu" element={<DishFormPage />} />
          <Route path="/gerichte" element={<p>Liste</p>} />
          <Route path="/gerichte/:id" element={<p>Gespeichert</p>} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

test('wechselt zwischen manueller Erfassung und KI und legt den Entwurf ins Formular', async () => {
  const user = userEvent.setup();
  const fetchMock = vi.fn<typeof fetch>(async (input) => {
    const url = String(input);
    if (url.includes('/api/ai/status')) {
      return json({ enabled: true, provider: 'fixture' });
    }
    if (url.includes('/api/ai/dish-drafts')) {
      return json(draft);
    }
    return json({ ...dish, id: 'dish-neu', name: 'Ofen-Kartoffelgratin' }, 201);
  });
  vi.stubGlobal('fetch', fetchMock);

  renderDish();
  expect(await screen.findByTestId('capture-ai')).toBeInTheDocument();
  expect(screen.getByTestId('dish-name')).toBeInTheDocument();

  await user.click(screen.getByTestId('capture-ai'));
  expect(screen.queryByTestId('dish-name')).not.toBeInTheDocument();
  await user.click(screen.getByTestId('capture-manual'));
  expect(screen.getByTestId('dish-name')).toBeInTheDocument();

  await user.click(screen.getByTestId('capture-ai'));
  await user.type(screen.getByTestId('draft-description'), 'Wir machen oft Kartoffelgratin.');
  await user.click(screen.getByTestId('draft-submit'));

  expect(await screen.findByTestId('dish-name')).toHaveValue('Kartoffelgratin');
  expect(screen.getByTestId('draft-summary')).toHaveTextContent('Aus der Beschreibung: Name, Aufwand, Meal Prep.');
  expect(screen.getByTestId('draft-summary')).toHaveTextContent('Noch nicht angegeben: Mahlzeiten, Zutaten, Homeoffice.');
  expect(screen.getByTestId('duplicate-hint')).toHaveTextContent('Ein ähnliches Gericht existiert bereits: Kartoffelgratin.');
  expect(screen.getByRole('group', { name: 'Aufwand' }).querySelector('[aria-pressed="true"]')).toHaveTextContent('Mittel');
  expect(screen.getByRole('group', { name: 'Meal Prep' }).querySelector('[aria-pressed="true"]')).toHaveTextContent('Geeignet');
  expect(screen.getByRole('group', { name: 'Homeoffice' }).querySelector('[aria-pressed="true"]')).toHaveTextContent('Nicht geprüft');

  await user.clear(screen.getByTestId('dish-name'));
  await user.type(screen.getByTestId('dish-name'), 'Ofen-Kartoffelgratin');
  await user.click(within(screen.getByRole('group', { name: 'Aufwand' })).getByRole('button', { name: 'Einfach' }));
  expect(screen.getByTestId('draft-summary')).toHaveTextContent('Von dir ergänzt: Name, Aufwand.');

  await user.click(screen.getByTestId('save-dish'));
  expect(await screen.findByText('Gespeichert')).toBeInTheDocument();
  const post = fetchMock.mock.calls.find((call) => String(call[0]) === '/api/dishes' && call[1]?.method === 'POST');
  expect(JSON.parse(String(post?.[1]?.body))).toMatchObject({
    name: 'Ofen-Kartoffelgratin',
    effort: 'EINFACH',
    mealPrep: 'SUITABLE',
    homeOffice: 'UNKNOWN',
    confirmDuplicate: false,
  });
  expect(fetchMock.mock.calls.filter((call) => String(call[0]).includes('/api/ai/dish-drafts'))).toHaveLength(1);
});

test('bricht einen Entwurf ab, ohne ein Gericht zu speichern', async () => {
  const user = userEvent.setup();
  const fetchMock = vi.fn<typeof fetch>(async (input) => {
    const url = String(input);
    if (url.includes('/api/ai/status')) {
      return json({ enabled: true, provider: 'fixture' });
    }
    return json(draft);
  });
  vi.stubGlobal('fetch', fetchMock);

  renderDish();
  await user.click(await screen.findByTestId('capture-ai'));
  await user.type(screen.getByTestId('draft-description'), 'Wir machen oft Kartoffelgratin.');
  await user.click(screen.getByTestId('draft-submit'));
  expect(await screen.findByTestId('dish-name')).toHaveValue('Kartoffelgratin');
  await user.click(screen.getByRole('link', { name: 'Abbrechen' }));

  expect(await screen.findByText('Liste')).toBeInTheDocument();
  expect(fetchMock.mock.calls.some((call) => String(call[0]) === '/api/dishes')).toBe(false);
});

test('behält die Beschreibung bei einem KI-Fehler und bietet die manuelle Erfassung an', async () => {
  const user = userEvent.setup();
  vi.stubGlobal('fetch', vi.fn<typeof fetch>(async (input) => {
    if (String(input).includes('/api/ai/status')) {
      return json({ enabled: true, provider: 'fixture' });
    }
    return json({ detail: 'Der Essensassistent ist gerade nicht erreichbar.', code: 'AI_UNAVAILABLE' }, 503);
  }));

  renderDish();
  await user.click(await screen.findByTestId('capture-ai'));
  await user.type(screen.getByTestId('draft-description'), 'Wir machen oft Kartoffelgratin.');
  await user.click(screen.getByTestId('draft-submit'));

  expect(await screen.findByRole('alert')).toHaveTextContent('nicht erreichbar');
  expect(screen.getByTestId('draft-description')).toHaveValue('Wir machen oft Kartoffelgratin.');
  await user.click(screen.getByTestId('draft-manual'));
  expect(screen.getByTestId('dish-name')).toHaveValue('');
});

test('bereitet eine Variante vor und speichert erst auf ausdrückliche Bestätigung', async () => {
  const user = userEvent.setup();
  const variantDraft: DishDraft = {
    ...draft,
    kind: 'VARIANT',
    baseDishId: 'dish-1',
    baseDishName: 'Lasagne',
    name: 'Mit roten Linsen',
    effort: null,
    mealPrep: null,
    ingredients: ['Tomaten', 'roten Linsen'],
    ingredientsSpecified: true,
    recognized: ['name', 'ingredients'],
    unknown: ['effort', 'mealPrep', 'homeOffice'],
    warnings: [],
    duplicates: [],
  };
  const fetchMock = vi.fn<typeof fetch>(async (input, init) => {
    const url = String(input);
    if (url.includes('/api/ai/status')) {
      return json({ enabled: true, provider: 'fixture' });
    }
    if (url.includes('/api/ai/dish-drafts')) {
      return json(variantDraft);
    }
    if (init?.method === 'POST') {
      return json({ ...dish, variants: [{ id: 'variant-1', name: 'Mit roten Linsen' }] });
    }
    return json(dish);
  });
  vi.stubGlobal('fetch', fetchMock);

  render(
    <ThemeProvider theme={theme}>
      <MemoryRouter initialEntries={['/gerichte/dish-1/varianten/neu?erfassung=ki']}>
        <Routes>
          <Route path="/gerichte/:id/varianten/neu" element={<VariantFormPage />} />
          <Route path="/gerichte/:id" element={<p>Variante gespeichert</p>} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );

  expect(await screen.findByTestId('draft-description')).toBeInTheDocument();
  await user.type(screen.getByTestId('draft-description'), 'Mit roten Linsen statt Hackfleisch.');
  await user.click(screen.getByTestId('draft-submit'));

  expect(await screen.findByTestId('variant-name')).toHaveValue('Mit roten Linsen');
  expect(screen.getByText('roten Linsen')).toBeInTheDocument();
  expect(screen.queryByText('Hackfleisch')).not.toBeInTheDocument();
  expect(screen.getByRole('group', { name: 'Aufwand' }).querySelector('[aria-pressed="true"]')).toHaveTextContent('Vom Grundgericht');
  expect(screen.getByRole('group', { name: 'Meal Prep' }).querySelector('[aria-pressed="true"]')).toHaveTextContent('Vom Grundgericht');
  expect(fetchMock.mock.calls.some((call) => String(call[0]).includes('/variants') && call[1]?.method === 'POST')).toBe(false);

  await user.click(screen.getByTestId('save-variant'));
  expect(await screen.findByText('Variante gespeichert')).toBeInTheDocument();
  const post = fetchMock.mock.calls.find((call) => String(call[0]).includes('/variants') && call[1]?.method === 'POST');
  expect(JSON.parse(String(post?.[1]?.body))).toMatchObject({
    name: 'Mit roten Linsen',
    effort: null,
    ingredientsSpecified: true,
    ingredients: ['Tomaten', 'roten Linsen'],
    mealPrep: 'INHERIT',
    homeOffice: 'INHERIT',
  });
});

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
