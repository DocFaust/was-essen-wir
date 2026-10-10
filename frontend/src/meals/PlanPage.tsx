import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router';
import { MEAL_TYPE_LABELS } from '../dishes/labels';
import type { MealType } from '../dishes/types';
import { ConfirmDialog, ErrorState, LoadingState } from '../dishes/ui';
import { ApiError } from '../http';
import { confirmPlan, createPlan, deletePlan, listPlans, updatePlan } from './api';
import { addDays, dayLabel, isoDate, parseIsoDate, shiftIsoDate, startOfWeek, todayIso, weekDays, weekLabel } from './dates';
import { MealEventForm } from './MealEventForm';
import type { MealDraft, MealEvent, MealEventWrite } from './types';

const PRIMARY: MealType[] = ['MITTAGESSEN', 'ABENDESSEN'];
const EXTRA: MealType[] = ['FRUEHSTUECK', 'SNACK'];

export function PlanPage({ userId }: { userId: string }) {
  const [weekStart, setWeekStart] = useState(() => startOfWeek(new Date()));
  const [events, setEvents] = useState<MealEvent[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [showExtras, setShowExtras] = useState(false);
  const [draft, setDraft] = useState<MealDraft | null>(null);
  const [pendingDelete, setPendingDelete] = useState<MealEvent | null>(null);
  const [pendingEaten, setPendingEaten] = useState<MealEvent | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const from = isoDate(weekStart);
  const to = isoDate(addDays(weekStart, 6));
  const currentWeek = isoDate(startOfWeek(new Date()));

  useEffect(() => {
    let active = true;
    setEvents(null);
    setError(null);
    listPlans(from, to)
      .then((loaded) => {
        if (active) {
          setEvents(loaded);
        }
      })
      .catch((caught: unknown) => {
        if (!active) {
          return;
        }
        setError(caught instanceof ApiError ? caught.message : 'Die Planung konnte nicht geladen werden.');
      });
    return () => {
      active = false;
    };
  }, [from, to, attempt]);

  const extrasVisible = showExtras || (events ?? []).some((event) => EXTRA.includes(event.mealType));

  function openDraft(next: MealDraft) {
    setActionError(null);
    setDraft(next);
  }

  async function save(body: MealEventWrite) {
    if (!draft) {
      return;
    }
    if (draft.mode === 'edit-plan' && draft.eventId) {
      await updatePlan(draft.eventId, body);
    } else {
      await createPlan(body);
    }
    setDraft(null);
    setWeekStart(startOfWeek(parseIsoDate(body.mealDate)));
    setAttempt((current) => current + 1);
  }

  async function remove() {
    if (!pendingDelete || busy) {
      return;
    }
    setBusy(true);
    setActionError(null);
    try {
      await deletePlan(pendingDelete.id);
      setPendingDelete(null);
      setAttempt((current) => current + 1);
    } catch (caught) {
      setActionError(caught instanceof ApiError ? caught.message : 'Die Planung konnte nicht gelöscht werden.');
    } finally {
      setBusy(false);
    }
  }

  async function markEaten() {
    if (!pendingEaten || busy) {
      return;
    }
    setBusy(true);
    setActionError(null);
    try {
      await confirmPlan(pendingEaten.id);
      setPendingEaten(null);
      setAttempt((current) => current + 1);
    } catch (caught) {
      setActionError(caught instanceof ApiError ? caught.message : 'Die Mahlzeit konnte nicht bestätigt werden.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <Stack spacing={2} data-testid="plan-page">
      <header>
        <Typography variant="h1" component="h1">
          Wochenplan
        </Typography>
        <Typography color="text.secondary" sx={{ mt: 1 }} data-testid="plan-week-label">
          {weekLabel(weekStart)}
        </Typography>
      </header>
      <Stack spacing={1}>
        <Button type="button" variant="outlined" fullWidth data-testid="plan-previous-week" onClick={() => setWeekStart(addDays(weekStart, -7))}>
          Vorherige Woche
        </Button>
        <Button type="button" variant="outlined" fullWidth data-testid="plan-next-week" onClick={() => setWeekStart(addDays(weekStart, 7))}>
          Nächste Woche
        </Button>
        <Button
          type="button"
          variant="text"
          fullWidth
          data-testid="plan-current-week"
          disabled={from === currentWeek}
          onClick={() => setWeekStart(startOfWeek(new Date()))}
        >
          Aktuelle Woche
        </Button>
      </Stack>
      <Button
        type="button"
        variant="contained"
        fullWidth
        data-testid="plan-create"
        onClick={() =>
          openDraft({
            mode: 'plan',
            mealDate: shiftIsoDate(todayIso(), 1),
            mealType: 'ABENDESSEN',
            dinerIds: [userId],
          })
        }
      >
        Einplanen
      </Button>
      <Button type="button" variant="outlined" fullWidth data-testid="plan-extras" onClick={() => setShowExtras((current) => !current)}>
        {extrasVisible ? 'Frühstück und Snacks ausblenden' : 'Frühstück und Snacks'}
      </Button>
      {actionError ? <ErrorState message={actionError} /> : null}
      {error ? <ErrorState message={error} onRetry={() => setAttempt((current) => current + 1)} /> : null}
      {!events && !error ? <LoadingState label="Woche wird geladen …" /> : null}
      {events
        ? weekDays(weekStart).map((day) => {
            const date = isoDate(day);
            const slots = extrasVisible ? [...EXTRA.slice(0, 1), ...PRIMARY, EXTRA[1]] : PRIMARY;
            return (
              <Stack key={date} spacing={1.5} data-testid="plan-day" sx={{ minWidth: 0 }}>
                <Typography variant="h2" sx={{ fontSize: '1.2rem' }}>
                  {dayLabel(day)}
                </Typography>
                {slots.map((mealType) => {
                  const entries = events.filter((event) => event.mealDate === date && event.mealType === mealType);
                  return (
                    <Stack key={mealType} spacing={1} sx={{ minWidth: 0 }}>
                      <Typography variant="body2" color="text.secondary">
                        {MEAL_TYPE_LABELS[mealType]}
                      </Typography>
                      {entries.length === 0 ? <Typography color="text.secondary">Noch offen</Typography> : null}
                      {entries.map((event) => (
                        <PlanEntry
                          key={event.id}
                          event={event}
                          onEdit={() =>
                            openDraft({
                              mode: 'edit-plan',
                              eventId: event.id,
                              mealDate: event.mealDate,
                              mealType: event.mealType,
                              dishId: event.dishId ?? undefined,
                              variantId: event.variantId,
                              dinerIds: event.participants.flatMap((person) => (person.userId ? [person.userId] : [])),
                              comment: event.comment ?? '',
                              participants: event.participants,
                            })
                          }
                          onDelete={() => setPendingDelete(event)}
                          onEaten={() => setPendingEaten(event)}
                        />
                      ))}
                      <Button
                        type="button"
                        variant="text"
                        fullWidth
                        data-testid={`plan-add-${date}-${mealType}`}
                        onClick={() =>
                          openDraft({
                            mode: 'plan',
                            mealDate: date,
                            mealType,
                            dinerIds: [userId],
                          })
                        }
                      >
                        Einplanen
                      </Button>
                    </Stack>
                  );
                })}
              </Stack>
            );
          })
        : null}
      <MealEventForm draft={draft} userId={userId} onClose={() => setDraft(null)} onSubmit={save} />
      <ConfirmDialog
        open={pendingDelete !== null}
        title="Planung löschen?"
        message={pendingDelete ? `„${pendingDelete.name}“ wird aus dem Plan genommen. Die Historie bleibt unberührt.` : ''}
        confirmLabel="Löschen"
        confirmColor="error"
        confirmTestId="confirm-delete"
        onConfirm={() => void remove()}
        onClose={() => setPendingDelete(null)}
      />
      <ConfirmDialog
        open={pendingEaten !== null}
        title="Als gegessen markieren?"
        message={
          pendingEaten
            ? `„${pendingEaten.name}“ gilt danach als gegessen und verschwindet aus dem Plan.`
            : ''
        }
        confirmLabel="Als gegessen markieren"
        confirmTestId="confirm-eaten"
        onConfirm={() => void markEaten()}
        onClose={() => setPendingEaten(null)}
      />
    </Stack>
  );
}

function PlanEntry({
  event,
  onEdit,
  onDelete,
  onEaten,
}: {
  event: MealEvent;
  onEdit: () => void;
  onDelete: () => void;
  onEaten: () => void;
}) {
  return (
    <Paper variant="outlined" data-testid="plan-entry" sx={{ p: 1.5, minWidth: 0 }}>
      <Stack spacing={1}>
        <Typography sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}>{event.name}</Typography>
        <Typography sx={{ overflowWrap: 'anywhere' }}>{event.participants.map((person) => person.displayName).join(', ')}</Typography>
        {event.comment ? <Typography sx={{ overflowWrap: 'anywhere' }}>{event.comment}</Typography> : null}
        {event.dishId ? (
          <Button component={RouterLink} to={`/gerichte/${event.dishId}`} variant="outlined" fullWidth>
            Gericht öffnen
          </Button>
        ) : null}
        <Button type="button" variant="contained" fullWidth data-testid="plan-eaten" onClick={onEaten}>
          Als gegessen markieren
        </Button>
        <Button type="button" variant="outlined" fullWidth data-testid="plan-edit" onClick={onEdit}>
          Bearbeiten
        </Button>
        <Button type="button" variant="outlined" color="error" fullWidth data-testid="plan-delete" onClick={onDelete}>
          Löschen
        </Button>
      </Stack>
    </Paper>
  );
}
