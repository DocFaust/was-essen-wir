import { ThemeProvider } from '@mui/material/styles';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, expect, test, vi } from 'vitest';
import { theme } from '../theme';
import { AiAssistant } from './AiAssistant';
import type { AiRecommendationResponse, Suggestion } from './types';

const suggestion: Suggestion = {
  dishId: 'dish-soup',
  variantId: null,
  name: 'Suppe',
  dishName: 'Suppe',
  variantName: null,
  reason: 'Eine unkomplizierte Alternative mit geringem Aufwand.',
  effort: 'EINFACH',
  mealPrep: 'UNKNOWN',
  homeOffice: 'UNKNOWN',
};

const success: AiRecommendationResponse = {
  reply: 'Ich habe nach einem schnellen Abendessen gesucht.',
  clarificationQuestion: null,
  criteria: {
    mealType: 'ABENDESSEN',
    date: '2026-10-11',
    dinerIds: ['ada'],
    participantNames: ['Ada Lovelace'],
    preferSimple: true,
    preferHomeOffice: false,
    preferMealPrep: false,
    dishSearchTerms: ['Nudeln'],
    avoidDishTerms: [],
    matchedDishes: ['Suppe'],
    limitations: [],
  },
  suggestions: [suggestion],
  explanation: null,
};

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=test-token';
});

test('blendet den Assistenten aus, wenn die Funktion deaktiviert ist', async () => {
  const fetchMock = vi.fn(async () => json({ enabled: false, provider: null }));
  vi.stubGlobal('fetch', fetchMock);
  renderAssistant();

  await waitFor(() => expect(fetchMock).toHaveBeenCalled());
  expect(screen.queryByTestId('ai-assistant')).not.toBeInTheDocument();
});

test('sendet eine Anfrage und zeigt Kriterien und Vorschläge', async () => {
  const fetchMock = mockFetch({ status: { enabled: true, provider: 'ollama' }, result: success });
  renderAssistant();

  expect(await screen.findByTestId('ai-assistant')).toBeInTheDocument();
  expect(screen.getByText(/Ollama-Server/)).toBeInTheDocument();
  await userEvent.type(screen.getByTestId('ai-message'), 'Heute Abend etwas Schnelles');
  await userEvent.click(screen.getByTestId('ai-submit'));

  expect(await screen.findByTestId('ai-reply')).toHaveTextContent('schnellen Abendessen');
  expect(screen.getByTestId('ai-criteria')).toHaveTextContent('Abendessen');
  expect(screen.getByTestId('ai-criteria')).toHaveTextContent('11.10.2026');
  expect(screen.getByTestId('ai-criteria')).toHaveTextContent('Ada Lovelace');
  expect(screen.getByTestId('ai-criteria')).toHaveTextContent('Schnell oder einfach');
  expect(screen.getByTestId('ai-criteria')).toHaveTextContent('Nudeln');
  expect(screen.getByTestId('suggestion-card')).toHaveTextContent('Suppe');
  expect(screen.getByTestId('suggestion-plan')).toBeInTheDocument();

  const call = fetchMock.mock.calls.find((entry) => String(entry[0]).includes('/api/ai/recommendations'));
  expect(call?.[1]).toEqual(expect.objectContaining({ method: 'POST' }));
  expect(new Headers(call?.[1]?.headers).get('X-XSRF-TOKEN')).toBe('test-token');
  expect(JSON.parse(String(call?.[1]?.body))).toEqual({ message: 'Heute Abend etwas Schnelles', seed: null });
});

test('zeigt eine Rückfrage ohne Vorschläge', async () => {
  mockFetch({
    status: { enabled: true, provider: 'fixture' },
    result: {
      ...success,
      reply: 'Wen meinst du?',
      clarificationQuestion: 'Wen meinst du?',
      suggestions: [],
    },
  });
  renderAssistant();
  await screen.findByTestId('ai-message');
  await userEvent.type(screen.getByTestId('ai-message'), 'mit Alex');
  await userEvent.click(screen.getByTestId('ai-submit'));

  expect(await screen.findByTestId('ai-clarification')).toHaveTextContent('Wen meinst du?');
  expect(screen.queryByTestId('suggestion-card')).not.toBeInTheDocument();
});

test('zeigt einen Fehler und erlaubt einen neuen Versuch', async () => {
  const fetchMock = mockFetch({
    status: { enabled: true, provider: 'openai' },
    fail: true,
  });
  renderAssistant();
  await screen.findByTestId('ai-message');
  expect(screen.getByText(/OpenAI/)).toBeInTheDocument();
  await userEvent.type(screen.getByTestId('ai-message'), 'Was essen wir?');
  await userEvent.click(screen.getByTestId('ai-submit'));

  expect(await screen.findByTestId('ai-error')).toHaveTextContent('nicht erreichbar');
  expect(screen.getByText(/Vorschlagsauswahl darüber/)).toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Erneut versuchen' }));
  expect(fetchMock.mock.calls.filter((entry) => String(entry[0]).includes('/api/ai/recommendations'))).toHaveLength(2);

  await userEvent.click(screen.getByRole('button', { name: 'Neue Anfrage' }));
  expect(screen.queryByTestId('ai-error')).not.toBeInTheDocument();
  expect(screen.getByTestId('ai-message')).toHaveValue('');
});

test('zeigt das Tageslimit ohne erneuten Versuch und verhindert Doppelklicks', async () => {
  let resolveRecommendation: (value: Response) => void = () => undefined;
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.includes('/api/ai/status')) {
      return json({ enabled: true, provider: 'ollama' });
    }
    if (url.includes('/api/members')) {
      return json([{ id: 'ada', displayName: 'Ada Lovelace' }]);
    }
    if (url.includes('/api/ai/recommendations')) {
      return new Promise<Response>((resolve) => {
        resolveRecommendation = resolve;
      });
    }
    return json({ title: 'Fehler', detail: 'Unerwartete Anfrage', status: 500 }, 500);
  });
  vi.stubGlobal('fetch', fetchMock);
  renderAssistant();
  await screen.findByTestId('ai-message');
  await userEvent.type(screen.getByTestId('ai-message'), 'Nudeln');
  await userEvent.click(screen.getByTestId('ai-submit'));
  expect(screen.getByTestId('ai-submit')).toBeDisabled();
  expect(fetchMock.mock.calls.filter((entry) => String(entry[0]).includes('/api/ai/recommendations'))).toHaveLength(1);

  resolveRecommendation(
    json(
      {
        title: 'Zu viele Versuche',
        detail: 'Das Tageslimit für den Essensassistenten ist erreicht. Bitte versuche es morgen erneut.',
        status: 429,
        code: 'AI_DAILY_LIMIT',
      },
      429,
    ),
  );

  expect(await screen.findByTestId('ai-error')).toHaveTextContent('Tageslimit');
  expect(screen.queryByRole('button', { name: 'Erneut versuchen' })).not.toBeInTheDocument();
});

test('zeigt vorübergehende Auslastung mit manuellem erneuten Versuch', async () => {
  mockFetch({
    status: { enabled: true, provider: 'ollama' },
    rateLimited: true,
  });
  renderAssistant();
  await screen.findByTestId('ai-message');
  await userEvent.type(screen.getByTestId('ai-message'), 'Was essen wir?');
  await userEvent.click(screen.getByTestId('ai-submit'));

  expect(await screen.findByTestId('ai-error')).toHaveTextContent('zu oft genutzt');
  expect(screen.getByRole('button', { name: 'Erneut versuchen' })).toBeInTheDocument();
});

test('zeigt einen Ladezustand und startet danach eine neue Anfrage', async () => {
  let resolveRecommendation: (value: Response) => void = () => undefined;
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.includes('/api/ai/status')) {
        return json({ enabled: true, provider: 'ollama' });
      }
      if (url.includes('/api/members')) {
        return json([{ id: 'ada', displayName: 'Ada Lovelace' }]);
      }
      return new Promise<Response>((resolve) => {
        resolveRecommendation = resolve;
      });
    }),
  );
  renderAssistant();
  await screen.findByTestId('ai-message');
  await userEvent.type(screen.getByTestId('ai-message'), 'Nudeln');
  await userEvent.click(screen.getByTestId('ai-submit'));

  expect(await screen.findByTestId('ai-loading')).toHaveTextContent('sucht Vorschläge');
  resolveRecommendation(json(success));
  expect(await screen.findByTestId('suggestion-card')).toBeInTheDocument();
  await userEvent.click(screen.getByTestId('ai-new-request'));
  expect(screen.queryByTestId('suggestion-card')).not.toBeInTheDocument();
  expect(screen.getByTestId('ai-message')).toHaveValue('');
});

function renderAssistant() {
  return render(
    <ThemeProvider theme={theme}>
      <MemoryRouter>
        <AiAssistant userId="ada" />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

function mockFetch(options: {
  status: { enabled: boolean; provider: string | null };
  result?: AiRecommendationResponse;
  fail?: boolean;
  rateLimited?: boolean;
}) {
  const fetchMock = vi.fn<typeof fetch>(async (input) => {
    const url = String(input);
    if (url.includes('/api/ai/status')) {
      return json(options.status);
    }
    if (url.includes('/api/members')) {
      return json([{ id: 'ada', displayName: 'Ada Lovelace' }]);
    }
    if (url.includes('/api/ai/recommendations')) {
      if (options.fail) {
        return json({ title: 'Fehler', detail: 'Der Essensassistent ist gerade nicht erreichbar.', status: 503 }, 503);
      }
      if (options.rateLimited) {
        return json(
          {
            title: 'Zu viele Versuche',
            detail: 'Du hast den Essensassistenten zu oft genutzt. Bitte warte einen Moment.',
            status: 429,
            code: 'AI_RATE_LIMITED',
          },
          429,
        );
      }
      return json(options.result);
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
