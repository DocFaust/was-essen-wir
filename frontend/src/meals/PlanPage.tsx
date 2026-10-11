import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useEffect, useRef, useState } from 'react';
import { Link as RouterLink } from 'react-router';
import { MEAL_TYPE_LABELS } from '../dishes/labels';
import type { MealType } from '../dishes/types';
import { ConfirmDialog, ErrorState, LoadingState, SuccessSnackbar } from '../dishes/ui';
import { ApiError } from '../http';
import { confirmPlan, createPlan, deletePlan, listPlans, updatePlan } from './api';
import { addDays, dayLabel, isoDate, parseIsoDate, todayIso, weekDays, weekLabel, startOfWeek } from './dates';
import { MealEventForm } from './MealEventForm';
import type { MealDraft, MealEvent, MealEventWrite } from './types';

const MEAL_ORDER: MealType[] = ['FRUEHSTUECK', 'MITTAGESSEN', 'ABENDESSEN', 'SNACK'];

export function PlanPage({ userId }: { userId: string }) {
  const [weekStart, setWeekStart] = useState(() => startOfWeek(new Date()));
  const [events, setEvents] = useState<MealEvent[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [draft, setDraft] = useState<MealDraft | null>(null);
  const [pendingDelete, setPendingDelete] = useState<MealEvent | null>(null);
  const [pendingEaten, setPendingEaten] = useState<MealEvent | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const busyRef = useRef(false);

  const from = isoDate(weekStart);
  const to = isoDate(addDays(weekStart, 6));
  const currentWeek = isoDate(startOfWeek(new Date()));
  const today = todayIso();

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

  function openDraft(next: MealDraft) {
    setActionError(null);
    setDraft(next);
  }

  async function save(body: MealEventWrite) {
    if (!draft) {
      return;
    }
    const editing = draft.mode === 'edit-plan' || draft.mode === 'move-plan';
    if (editing && draft.eventId) {
      await updatePlan(draft.eventId, body);
      setNotice(draft.mode === 'move-plan' ? 'Verschoben.' : 'Planung gespeichert.');
    } else {
      await createPlan(body);
      setNotice('Eingeplant.');
    }
    setDraft(null);
    setWeekStart(startOfWeek(parseIsoDate(body.mealDate)));
    setAttempt((current) => current + 1);
  }

  async function remove() {
    if (!pendingDelete || busyRef.current) {
      return;
    }
    busyRef.current = true;
    setActionError(null);
    try {
      await deletePlan(pendingDelete.id);
      setPendingDelete(null);
      setNotice('Aus dem Plan genommen.');
      setAttempt((current) => current + 1);
    } catch (caught) {
      setActionError(caught instanceof ApiError ? caught.message : 'Die Planung konnte nicht gelöscht werden.');
    } finally {
      busyRef.current = false;
    }
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
      setAttempt((current) => current + 1);
    } catch (caught) {
      setActionError(caught instanceof ApiError ? caught.message : 'Die Mahlzeit konnte nicht bestätigt werden.');
    } finally {
      busyRef.current = false;
    }
  }

  return (
    <Stack spacing={2} data-testid="plan-page">
      <header>
        <Typography variant="h1" component="h1">
          Wochenplan
        </Typography>
      </header>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', minWidth: 0 }}>
        <Button
          type="button"
          variant="outlined"
          data-testid="plan-previous-week"
          aria-label="Vorherige Woche"
          onClick={() => setWeekStart(addDays(weekStart, -7))}
          sx={{ minWidth: 48, px: 0 }}
        >
          ‹
        </Button>
        <Typography data-testid="plan-week-label" sx={{ flex: 1, minWidth: 0, textAlign: 'center', overflowWrap: 'anywhere' }}>
          {weekLabel(weekStart)}
        </Typography>
        <Button
          type="button"
          variant="outlined"
          data-testid="plan-next-week"
          aria-label="Nächste Woche"
          onClick={() => setWeekStart(addDays(weekStart, 7))}
          sx={{ minWidth: 48, px: 0 }}
        >
          ›
        </Button>
      </Stack>
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
      <Button
        type="button"
        variant="contained"
        fullWidth
        data-testid="plan-create"
        onClick={() =>
          openDraft({
            mode: 'plan',
            mealDate: today,
            mealType: 'ABENDESSEN',
            dinerIds: [userId],
          })
        }
      >
        Einplanen
      </Button>
      {actionError ? <ErrorState message={actionError} /> : null}
      {error ? <ErrorState message={error} onRetry={() => setAttempt((current) => current + 1)} /> : null}
      {!events && !error ? <LoadingState label="Woche wird geladen …" /> : null}
      {events
        ? weekDays(weekStart).map((day) => {
            const date = isoDate(day);
            const isToday = date === today;
            const entries = events
              .filter((event) => event.mealDate === date)
              .sort((left, right) => MEAL_ORDER.indexOf(left.mealType) - MEAL_ORDER.indexOf(right.mealType));
            return (
              <Stack
                key={date}
                component="section"
                spacing={1.5}
                aria-label={dayLabel(day)}
                data-testid="plan-day"
                data-today={isToday ? 'true' : 'false'}
                sx={{
                  minWidth: 0,
                  p: 1.5,
                  border: 1,
                  borderColor: isToday ? 'primary.main' : 'divider',
                  borderRadius: 2,
                  bgcolor: isToday ? 'action.selected' : 'background.paper',
                }}
              >
                <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'baseline', flexWrap: 'wrap' }}>
                  <Typography variant="h2" sx={{ fontSize: '1.15rem' }}>
                    {dayLabel(day)}
                  </Typography>
                  {isToday ? (
                    <Typography component="span" sx={{ fontWeight: 700 }}>
                      Heute
                    </Typography>
                  ) : null}
                </Stack>
                {entries.length === 0 ? (
                  <Typography color="text.secondary" data-testid="plan-empty">
                    Noch nichts geplant
                  </Typography>
                ) : null}
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
                        dishName: event.name,
                        variantId: event.variantId,
                        dinerIds: event.participants.flatMap((person) => (person.userId ? [person.userId] : [])),
                        comment: event.comment ?? '',
                        participants: event.participants,
                      })
                    }
                    onMove={() =>
                      openDraft({
                        mode: 'move-plan',
                        eventId: event.id,
                        mealDate: event.mealDate,
                        mealType: event.mealType,
                        dishId: event.dishId ?? undefined,
                        dishName: event.name,
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
                  data-testid={`plan-add-${date}`}
                  onClick={() =>
                    openDraft({
                      mode: 'plan',
                      mealDate: date,
                      mealType: 'ABENDESSEN',
                      dinerIds: [userId],
                    })
                  }
                >
                  Einplanen
                </Button>
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

function PlanEntry({
  event,
  onEdit,
  onMove,
  onDelete,
  onEaten,
}: {
  event: MealEvent;
  onEdit: () => void;
  onMove: () => void;
  onDelete: () => void;
  onEaten: () => void;
}) {
  return (
    <Paper variant="outlined" data-testid="plan-entry" sx={{ p: 1.5, minWidth: 0, bgcolor: 'background.paper' }}>
      <Stack spacing={1}>
        <Typography variant="body2" color="text.secondary">
          {MEAL_TYPE_LABELS[event.mealType]}
        </Typography>
        <Typography sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}>{event.name}</Typography>
        <Typography sx={{ overflowWrap: 'anywhere' }}>{event.participants.map((person) => person.displayName).join(', ')}</Typography>
        {event.comment ? <Typography sx={{ overflowWrap: 'anywhere' }}>{event.comment}</Typography> : null}
        <Button type="button" variant="contained" fullWidth data-testid="plan-eaten" onClick={onEaten}>
          Als gegessen markieren
        </Button>
        <Button type="button" variant="text" fullWidth data-testid="plan-edit" onClick={onEdit}>
          Bearbeiten
        </Button>
        <Button type="button" variant="text" fullWidth data-testid="plan-move" onClick={onMove}>
          Verschieben
        </Button>
        {event.dishId ? (
          <Button component={RouterLink} to={`/gerichte/${event.dishId}`} variant="text" fullWidth>
            Gericht öffnen
          </Button>
        ) : null}
        <Button type="button" variant="text" color="error" fullWidth data-testid="plan-delete" onClick={onDelete}>
          Löschen
        </Button>
      </Stack>
    </Paper>
  );
}
