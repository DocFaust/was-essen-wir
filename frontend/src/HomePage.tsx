import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useEffect, useRef, useState } from 'react';
import { Link as RouterLink } from 'react-router';
import { useAuth } from './auth/AuthProvider';
import { MEAL_TYPE_LABELS } from './dishes/labels';
import { ConfirmDialog, ErrorState, LoadingState, SuccessSnackbar } from './dishes/ui';
import { ApiError } from './http';
import { confirmPlan, createHistory, listPlans } from './meals/api';
import { dayLabel, todayIso } from './meals/dates';
import { MealEventForm } from './meals/MealEventForm';
import type { MealDraft, MealEvent, MealEventWrite } from './meals/types';
import { AiAssistant } from './recommendations/AiAssistant';
import { SuggestForm } from './recommendations/SuggestForm';

export function HomePage() {
  const { user } = useAuth();
  const [attempt, setAttempt] = useState(0);
  const [events, setEvents] = useState<MealEvent[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [pendingEaten, setPendingEaten] = useState<MealEvent | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [draft, setDraft] = useState<MealDraft | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const busyRef = useRef(false);
  const today = todayIso();

  useEffect(() => {
    let active = true;
    setError(null);
    listPlans(today, today)
      .then((loaded) => {
        if (active) {
          setEvents(loaded);
        }
      })
      .catch((caught: unknown) => {
        if (!active) {
          return;
        }
        setError(caught instanceof ApiError ? caught.message : 'Der heutige Plan konnte nicht geladen werden.');
      });
    return () => {
      active = false;
    };
  }, [today, attempt]);

  if (!user) {
    return null;
  }
  const currentUser = user;

  function refresh() {
    setAttempt((current) => current + 1);
  }

  function openEaten() {
    setDraft({
      mode: 'eaten',
      mealDate: today,
      mealType: 'ABENDESSEN',
      dinerIds: [currentUser.id],
    });
  }

  async function markEaten() {
    if (!pendingEaten || busyRef.current) {
      return;
    }
    busyRef.current = true;
    setActionError(null);
    try {
      await confirmPlan(pendingEaten.id);
      setPendingEaten(null);
      setNotice('Als gegessen bestätigt.');
      refresh();
    } catch (caught) {
      setActionError(caught instanceof ApiError ? caught.message : 'Die Mahlzeit konnte nicht bestätigt werden.');
    } finally {
      busyRef.current = false;
    }
  }

  async function saveEaten(body: MealEventWrite) {
    await createHistory(body);
    setDraft(null);
    setNotice('Als gegessen eingetragen.');
  }

  return (
    <Stack spacing={2.5}>
      <header>
        <Typography variant="h1" component="h1">
          Was essen wir heute?
        </Typography>
        <Typography color="text.secondary" sx={{ mt: 0.5 }} data-testid="today-date">
          {dayLabel(new Date())}
        </Typography>
      </header>

      <Stack component="section" spacing={1.5} aria-label="Heute" data-testid="today-panel">
        {error ? <ErrorState message={error} onRetry={refresh} /> : null}
        {actionError ? <ErrorState message={actionError} /> : null}
        {!events && !error ? <LoadingState label="Heute wird geladen …" /> : null}
        {events && events.length === 0 ? (
          <Typography color="text.secondary" data-testid="today-empty">
            Heute ist noch nichts geplant.
          </Typography>
        ) : null}
        {events?.map((event) => (
          <Paper key={event.id} variant="outlined" data-testid="today-entry" sx={{ p: 1.5, minWidth: 0 }}>
            <Stack spacing={1}>
              <Typography variant="body2" color="text.secondary">
                {MEAL_TYPE_LABELS[event.mealType]}
              </Typography>
              <Typography sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}>{event.name}</Typography>
              <Typography sx={{ overflowWrap: 'anywhere' }}>
                {event.participants.map((person) => person.displayName).join(', ')}
              </Typography>
              <Button type="button" variant="contained" fullWidth data-testid="today-eaten" onClick={() => setPendingEaten(event)}>
                Als gegessen bestätigen
              </Button>
            </Stack>
          </Paper>
        ))}
        <Button component={RouterLink} to="/plan" variant="text" fullWidth>
          Planung öffnen
        </Button>
      </Stack>

      <Stack spacing={1} component="section" aria-label="Schnellaktionen">
        <Button type="button" variant="outlined" fullWidth data-testid="quick-eaten" onClick={openEaten}>
          Gegessenes eintragen
        </Button>
        <Button component={RouterLink} to="/gerichte/neu" variant="outlined" fullWidth data-testid="quick-new-dish">
          Gericht hinzufügen
        </Button>
        <Button component={RouterLink} to="/plan" variant="outlined" fullWidth data-testid="quick-plan">
          Wochenplan ansehen
        </Button>
      </Stack>

      <SuggestForm userId={currentUser.id} onRecorded={refresh} />
      <AiAssistant userId={currentUser.id} onRecorded={refresh} />

      <MealEventForm draft={draft} userId={currentUser.id} onClose={() => setDraft(null)} onSubmit={saveEaten} />
      <ConfirmDialog
        open={pendingEaten !== null}
        title="Als gegessen markieren?"
        message={pendingEaten ? `„${pendingEaten.name}“ gilt danach als gegessen und verschwindet aus dem Plan.` : ''}
        confirmLabel="Als gegessen markieren"
        confirmTestId="confirm-eaten"
        onConfirm={() => void markEaten()}
        onClose={() => setPendingEaten(null)}
      />
      <SuccessSnackbar message={notice} onClose={() => setNotice(null)} />
    </Stack>
  );
}
