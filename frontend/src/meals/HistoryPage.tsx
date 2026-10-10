import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router';
import { MEAL_TYPE_LABELS } from '../dishes/labels';
import { ConfirmDialog, ErrorState, LoadingState } from '../dishes/ui';
import { ApiError } from '../http';
import { listMembers } from '../recommendations/api';
import type { Member } from '../recommendations/types';
import { createHistory, deleteHistory, listHistory, updateHistory } from './api';
import { dayLabel, parseIsoDate, shiftIsoDate, todayIso } from './dates';
import { MealEventForm } from './MealEventForm';
import type { MealDraft, MealEvent, MealEventWrite } from './types';

export function HistoryPage({ userId }: { userId: string }) {
  const [from, setFrom] = useState(() => shiftIsoDate(todayIso(), -90));
  const [to, setTo] = useState(() => shiftIsoDate(todayIso(), 14));
  const [dinerId, setDinerId] = useState('');
  const [applied, setApplied] = useState({ from: shiftIsoDate(todayIso(), -90), to: shiftIsoDate(todayIso(), 14), dinerId: '' });
  const [events, setEvents] = useState<MealEvent[] | null>(null);
  const [members, setMembers] = useState<Member[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [draft, setDraft] = useState<MealDraft | null>(null);
  const [pendingDelete, setPendingDelete] = useState<MealEvent | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let active = true;
    listMembers()
      .then((loaded) => {
        if (active) {
          setMembers(loaded);
        }
      })
      .catch(() => {
        if (active) {
          setMembers([]);
        }
      });
    return () => {
      active = false;
    };
  }, [userId]);

  useEffect(() => {
    let active = true;
    setEvents(null);
    setError(null);
    listHistory(applied.from, applied.to, applied.dinerId || undefined)
      .then((loaded) => {
        if (active) {
          setEvents(loaded);
        }
      })
      .catch((caught: unknown) => {
        if (!active) {
          return;
        }
        setError(caught instanceof ApiError ? caught.message : 'Die Historie konnte nicht geladen werden.');
      });
    return () => {
      active = false;
    };
  }, [applied, attempt]);

  function includeDate(date: string) {
    setFrom((current) => (date < current ? date : current));
    setTo((current) => (date > current ? date : current));
    setApplied((current) => ({
      from: date < current.from ? date : current.from,
      to: date > current.to ? date : current.to,
      dinerId: current.dinerId,
    }));
  }

  async function save(body: MealEventWrite) {
    if (!draft) {
      return;
    }
    if (draft.mode === 'edit-eaten' && draft.eventId) {
      await updateHistory(draft.eventId, body);
    } else {
      await createHistory(body);
    }
    setDraft(null);
    includeDate(body.mealDate);
    setAttempt((current) => current + 1);
  }

  async function remove() {
    if (!pendingDelete || busy) {
      return;
    }
    setBusy(true);
    setActionError(null);
    try {
      await deleteHistory(pendingDelete.id);
      setPendingDelete(null);
      setAttempt((current) => current + 1);
    } catch (caught) {
      setActionError(caught instanceof ApiError ? caught.message : 'Der Eintrag konnte nicht gelöscht werden.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <Stack spacing={2} data-testid="history-page">
      <header>
        <Typography variant="h1" component="h1">
          Was haben wir gegessen?
        </Typography>
        <Typography color="text.secondary" sx={{ mt: 1 }}>
          Nur tatsächlich gegessene Mahlzeiten, die neueste zuerst.
        </Typography>
      </header>
      <Button
        type="button"
        variant="contained"
        fullWidth
        data-testid="history-create"
        onClick={() =>
          setDraft({
            mode: 'eaten',
            mealDate: todayIso(),
            mealType: 'ABENDESSEN',
            dinerIds: [userId],
          })
        }
      >
        Nachtragen
      </Button>
      <Stack spacing={1.5} component="form" onSubmit={(event) => {
        event.preventDefault();
        setApplied({ from, to, dinerId });
      }}>
        <TextField
          label="Von"
          type="date"
          value={from}
          onChange={(event) => setFrom(event.target.value)}
          fullWidth
          slotProps={{ inputLabel: { shrink: true }, htmlInput: { 'data-testid': 'history-from' } }}
        />
        <TextField
          label="Bis"
          type="date"
          value={to}
          onChange={(event) => setTo(event.target.value)}
          fullWidth
          slotProps={{ inputLabel: { shrink: true }, htmlInput: { 'data-testid': 'history-to' } }}
        />
        <Stack spacing={1}>
          <Typography variant="body2" color="text.secondary">
            Mitessende
          </Typography>
          <Button
            type="button"
            fullWidth
            variant={dinerId === '' ? 'contained' : 'outlined'}
            aria-pressed={dinerId === ''}
            onClick={() => setDinerId('')}
          >
            Alle
          </Button>
          {members.map((member) => (
            <Button
              key={member.id}
              type="button"
              fullWidth
              variant={dinerId === member.id ? 'contained' : 'outlined'}
              aria-pressed={dinerId === member.id}
              data-testid="history-diner"
              onClick={() => setDinerId(member.id)}
            >
              {member.displayName}
            </Button>
          ))}
        </Stack>
        <Button type="submit" variant="outlined" fullWidth data-testid="history-apply">
          Anzeigen
        </Button>
      </Stack>
      {actionError ? <ErrorState message={actionError} /> : null}
      {error ? <ErrorState message={error} onRetry={() => setAttempt((current) => current + 1)} /> : null}
      {!events && !error ? <LoadingState label="Historie wird geladen …" /> : null}
      {events && events.length === 0 ? (
        <Typography color="text.secondary">In diesem Zeitraum ist noch nichts eingetragen.</Typography>
      ) : null}
      {events?.map((event) => (
        <Paper key={event.id} variant="outlined" data-testid="history-entry" sx={{ p: 1.5, minWidth: 0 }}>
          <Stack spacing={1}>
            <Typography variant="body2" color="text.secondary">
              {dayLabel(parseIsoDate(event.mealDate))} · {MEAL_TYPE_LABELS[event.mealType]}
            </Typography>
            <Typography sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}>{event.name}</Typography>
            <Typography sx={{ overflowWrap: 'anywhere' }}>
              {event.participants.map((person) => person.displayName).join(', ')}
            </Typography>
            {event.comment ? <Typography sx={{ overflowWrap: 'anywhere' }}>{event.comment}</Typography> : null}
            {event.dishId ? (
              <Button component={RouterLink} to={`/gerichte/${event.dishId}`} variant="outlined" fullWidth>
                Gericht öffnen
              </Button>
            ) : null}
            <Button
              type="button"
              variant="outlined"
              fullWidth
              data-testid="history-edit"
              onClick={() =>
                setDraft({
                  mode: 'edit-eaten',
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
            >
              Bearbeiten
            </Button>
            <Button type="button" variant="outlined" color="error" fullWidth data-testid="history-delete" onClick={() => setPendingDelete(event)}>
              Löschen
            </Button>
          </Stack>
        </Paper>
      ))}
      <MealEventForm draft={draft} userId={userId} onClose={() => setDraft(null)} onSubmit={save} />
      <ConfirmDialog
        open={pendingDelete !== null}
        title="Eintrag löschen?"
        message={pendingDelete ? `„${pendingDelete.name}“ wird aus der Historie entfernt.` : ''}
        confirmLabel="Löschen"
        confirmColor="error"
        confirmTestId="confirm-delete"
        onConfirm={() => void remove()}
        onClose={() => setPendingDelete(null)}
      />
    </Stack>
  );
}
