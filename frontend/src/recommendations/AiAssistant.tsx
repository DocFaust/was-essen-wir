import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useRef, useState } from 'react';
import { MEAL_TYPE_LABELS } from '../dishes/labels';
import { ErrorState, LoadingState, SuccessSnackbar } from '../dishes/ui';
import { ApiError } from '../http';
import { createHistory, createPlan } from '../meals/api';
import { MealEventForm } from '../meals/MealEventForm';
import type { MealDraft, MealEventWrite } from '../meals/types';
import { aiRecommend, aiStatus, listMembers } from './api';
import { SuggestionCard } from './SuggestionCard';
import type { AiRecommendationResponse, AiStatus, Member, Suggestion } from './types';

export function AiAssistant({ userId, onRecorded }: { userId: string; onRecorded?: () => void }) {
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [members, setMembers] = useState<Member[]>([]);
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<AiRecommendationResponse | null>(null);
  const [excluded, setExcluded] = useState<string[]>([]);
  const [mealDraft, setMealDraft] = useState<MealDraft | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const submitting = useRef(false);

  useEffect(() => {
    let active = true;
    aiStatus()
      .then((loaded) => {
        if (active) {
          setStatus(loaded);
        }
      })
      .catch(() => {
        if (active) {
          setStatus({ enabled: false, provider: null });
        }
      });
    return () => {
      active = false;
    };
  }, []);

  if (!status?.enabled) {
    return null;
  }

  async function submit() {
    const text = message.trim();
    if (!text || submitting.current) {
      return;
    }
    submitting.current = true;
    setLoading(true);
    setError(null);
    setResult(null);
    setExcluded([]);
    try {
      setResult(await aiRecommend(text));
    } catch (caught) {
      setResult(null);
      setError(caught instanceof ApiError ? caught.message : 'Der Essensassistent ist gerade nicht erreichbar.');
    } finally {
      submitting.current = false;
      setLoading(false);
    }
  }

  async function openDraft(suggestion: Suggestion, mode: 'plan' | 'eaten') {
    if (!result) {
      return;
    }
    let known = members;
    if (known.length === 0) {
      try {
        known = await listMembers();
        setMembers(known);
      } catch {
        known = [];
      }
    }
    setMealDraft(draftFor(result, suggestion, known, userId, mode));
  }

  function reset() {
    setMessage('');
    setResult(null);
    setError(null);
    setExcluded([]);
  }

  const visibleSuggestions = (result?.suggestions ?? []).filter((suggestion) => !excluded.includes(suggestion.dishId));

  return (
    <Stack spacing={2} data-testid="ai-assistant" sx={{ minWidth: 0 }}>
      <Box>
        <Typography variant="h2" sx={{ fontSize: '1.15rem' }}>
          Frag den Essensassistenten
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
          Optional. Die Vorschläge darüber bleiben der normale Weg. {privacyNote(status.provider)}
        </Typography>
      </Box>
      <Box
        component="form"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
        sx={{ minWidth: 0 }}
      >
        <Stack spacing={1.5}>
          <TextField
            fullWidth
            label="Was möchtest du heute essen?"
            value={message}
            onChange={(event) => setMessage(event.target.value)}
            placeholder="Heute Abend etwas Schnelles, aber keine Pizza."
            slotProps={{ htmlInput: { 'data-testid': 'ai-message', maxLength: 500, enterKeyHint: 'go' } }}
          />
          <Button type="submit" variant="contained" fullWidth disabled={loading || message.trim().length === 0} data-testid="ai-submit">
            {loading ? 'Wird gesucht …' : 'Vorschläge finden'}
          </Button>
        </Stack>
      </Box>
      {loading ? (
        <Box data-testid="ai-loading">
          <LoadingState label="Der Assistent sucht Vorschläge …" />
        </Box>
      ) : null}
      {error ? (
        <Stack spacing={1.5} data-testid="ai-error">
          <ErrorState message={error} onRetry={() => void submit()} />
          <Typography variant="body2">Die gewohnte Vorschlagsauswahl darüber funktioniert weiterhin.</Typography>
          <Button type="button" variant="text" fullWidth onClick={reset}>
            Neue Anfrage
          </Button>
        </Stack>
      ) : null}
      {result && !loading ? (
        <Stack spacing={1.5} data-testid="ai-results" sx={{ minWidth: 0 }}>
          <Alert severity="info" data-testid="ai-reply">
            {result.reply}
          </Alert>
          {result.clarificationQuestion ? (
            <Alert severity="warning" data-testid="ai-clarification">
              {result.clarificationQuestion}
            </Alert>
          ) : null}
          <Criteria criteria={result.criteria} />
          {result.explanation ? <Alert severity="info">{result.explanation}</Alert> : null}
          {visibleSuggestions.map((suggestion) => (
            <SuggestionCard
              key={`${suggestion.dishId}:${suggestion.variantId ?? 'base'}`}
              suggestion={suggestion}
              disabled={false}
              onExclude={() => setExcluded((current) => [...current, suggestion.dishId])}
              onPlan={() => void openDraft(suggestion, 'plan')}
              onEaten={() => void openDraft(suggestion, 'eaten')}
            />
          ))}
          <Button type="button" variant="outlined" fullWidth onClick={reset} data-testid="ai-new-request">
            Neue Anfrage
          </Button>
        </Stack>
      ) : null}
      <MealEventForm
        draft={mealDraft}
        userId={userId}
        onClose={() => setMealDraft(null)}
        onSubmit={async (body: MealEventWrite) => {
          if (mealDraft?.mode === 'plan') {
            await createPlan(body);
            setNotice('Eingeplant.');
          } else {
            await createHistory(body);
            setNotice('Als gegessen eingetragen.');
          }
          setMealDraft(null);
          onRecorded?.();
        }}
      />
      <SuccessSnackbar message={notice} onClose={() => setNotice(null)} />
    </Stack>
  );
}

function Criteria({ criteria }: { criteria: AiRecommendationResponse['criteria'] }) {
  const lines = [
    `Mahlzeit: ${MEAL_TYPE_LABELS[criteria.mealType]}`,
    `Datum: ${formatDate(criteria.date)}`,
    `Mitessende: ${criteria.participantNames.join(', ') || 'noch offen'}`,
  ];
  if (criteria.preferSimple) {
    lines.push('Schnell oder einfach');
  }
  if (criteria.preferHomeOffice) {
    lines.push('Homeoffice');
  }
  if (criteria.preferMealPrep) {
    lines.push('Meal Prep');
  }
  if (criteria.dishSearchTerms.length > 0) {
    lines.push(`Wunsch: ${criteria.dishSearchTerms.join(', ')}`);
  }
  if (criteria.avoidDishTerms.length > 0) {
    lines.push(`Ohne: ${criteria.avoidDishTerms.join(', ')}`);
  }
  if (criteria.matchedDishes.length > 0) {
    lines.push(`Im Katalog: ${criteria.matchedDishes.join(', ')}`);
  }
  return (
    <Stack spacing={0.5} data-testid="ai-criteria" sx={{ minWidth: 0 }}>
      <Typography variant="body2" color="text.secondary">
        Verwendete Kriterien
      </Typography>
      {lines.map((line) => (
        <Typography key={line} sx={{ overflowWrap: 'anywhere' }}>
          {line}
        </Typography>
      ))}
      {criteria.limitations.map((limitation) => (
        <Typography key={limitation} color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
          {limitation}
        </Typography>
      ))}
    </Stack>
  );
}

function draftFor(
  result: AiRecommendationResponse,
  suggestion: Suggestion,
  members: Member[],
  userId: string,
  mode: 'plan' | 'eaten',
): MealDraft {
  const known = new Set(members.map((member) => member.id));
  const dinerIds = result.criteria.dinerIds.filter((id) => known.has(id));
  return {
    mode,
    mealDate: result.criteria.date,
    mealType: result.criteria.mealType,
    dishId: suggestion.dishId,
    dishName: suggestion.name,
    variantId: suggestion.variantId,
    dinerIds: dinerIds.length > 0 ? dinerIds : [userId],
  };
}

function formatDate(iso: string): string {
  const [year, month, day] = iso.split('-');
  if (!year || !month || !day) {
    return iso;
  }
  return `${day}.${month}.${year}`;
}

function privacyNote(provider: AiStatus['provider']): string {
  if (provider === 'openai') {
    return 'Die Anfrage geht an OpenAI. Übertragen werden dein Text, das Datum und die erlaubten Kriterien, keine Bewertungen und keine Essenshistorie.';
  }
  if (provider === 'ollama') {
    return 'Die Anfrage geht an den konfigurierten Ollama-Server. Übertragen werden dein Text, das Datum und die erlaubten Kriterien, keine Bewertungen und keine Essenshistorie.';
  }
  return 'Testmodus: die Anfrage bleibt im Backend und wird nicht an einen externen Dienst gesendet.';
}
