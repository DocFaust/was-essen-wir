import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import { useEffect, useRef, useState } from 'react';
import { Link as RouterLink } from 'react-router';
import { listMembers } from '../recommendations/api';
import type { Member } from '../recommendations/types';
import { getDish, listDishes } from '../dishes/api';
import { MEAL_TYPE_LABELS, MEAL_TYPES, emptyToNull } from '../dishes/labels';
import type { DishSummary, DishVariant, MealType } from '../dishes/types';
import { ChoiceButtons, ErrorState, LoadingState } from '../dishes/ui';
import { ApiError } from '../http';
import type { MealDraft, MealEventWrite } from './types';

const TITLES: Record<MealDraft['mode'], string> = {
  plan: 'Mahlzeit einplanen',
  eaten: 'Als gegessen eintragen',
  'edit-plan': 'Planung bearbeiten',
  'edit-eaten': 'Eintrag bearbeiten',
};

const SUBMIT_LABELS: Record<MealDraft['mode'], string> = {
  plan: 'Einplanen',
  eaten: 'Als gegessen eintragen',
  'edit-plan': 'Speichern',
  'edit-eaten': 'Speichern',
};

export function MealEventForm({
  draft,
  userId,
  onClose,
  onSubmit,
}: {
  draft: MealDraft | null;
  userId: string;
  onClose: () => void;
  onSubmit: (body: MealEventWrite) => Promise<void>;
}) {
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'));
  const open = draft !== null;
  const [mealDate, setMealDate] = useState('');
  const [mealType, setMealType] = useState<MealType>('ABENDESSEN');
  const [dishId, setDishId] = useState('');
  const [variantId, setVariantId] = useState('');
  const [dinerIds, setDinerIds] = useState<string[]>([]);
  const [comment, setComment] = useState('');
  const [dishQuery, setDishQuery] = useState('');
  const [dishes, setDishes] = useState<DishSummary[] | null>(null);
  const [variants, setVariants] = useState<DishVariant[]>([]);
  const [members, setMembers] = useState<Member[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const savingRef = useRef(false);

  useEffect(() => {
    if (!draft) {
      savingRef.current = false;
      setSaving(false);
      return;
    }
    savingRef.current = false;
    setSaving(false);
    setMealDate(draft.mealDate);
    setMealType(draft.mealType);
    setDishId(draft.dishId ?? '');
    setVariantId(draft.variantId ?? '');
    setDinerIds(draft.dinerIds.length > 0 ? draft.dinerIds : [userId]);
    setComment(draft.comment ?? '');
    setDishQuery('');
    setError(null);
    setVariants([]);
  }, [draft, userId]);

  useEffect(() => {
    if (!open) {
      return;
    }
    let active = true;
    setLoading(true);
    setLoadError(null);
    Promise.all([listDishes({}), listMembers()])
      .then(([loadedDishes, loadedMembers]) => {
        if (!active) {
          return;
        }
        setDishes(loadedDishes);
        setMembers(loadedMembers);
        setLoading(false);
      })
      .catch((caught: unknown) => {
        if (!active) {
          return;
        }
        setLoadError(caught instanceof ApiError ? caught.message : 'Die Angaben konnten nicht geladen werden.');
        setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [open]);

  useEffect(() => {
    if (!open || !dishId) {
      setVariants([]);
      return;
    }
    let active = true;
    getDish(dishId)
      .then((dish) => {
        if (active) {
          setVariants(dish.variants);
        }
      })
      .catch(() => {
        if (active) {
          setVariants([]);
        }
      });
    return () => {
      active = false;
    };
  }, [open, dishId]);

  const people = memberOptions(members ?? [], draft?.participants ?? []);
  const query = dishQuery.trim().toLowerCase();
  const visibleDishes = (dishes ?? [])
    .filter((dish) => dish.id === dishId || dish.name.toLowerCase().includes(query))
    .slice(0, 30);

  async function save() {
    if (!draft || savingRef.current) {
      return;
    }
    if (!mealDate) {
      setError('Bitte wähle ein Datum.');
      return;
    }
    if (!dishId) {
      setError('Bitte wähle ein Gericht.');
      return;
    }
    if (dinerIds.length === 0) {
      setError('Mindestens eine Person muss ausgewählt sein.');
      return;
    }
    savingRef.current = true;
    setSaving(true);
    setError(null);
    try {
      await onSubmit({
        mealDate,
        mealType,
        dishId,
        variantId: variantId || null,
        dinerIds,
        comment: emptyToNull(comment),
      });
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Die Mahlzeit konnte nicht gespeichert werden.');
      savingRef.current = false;
      setSaving(false);
    }
  }

  function toggleDiner(id: string) {
    setDinerIds((current) => (current.includes(id) ? current.filter((item) => item !== id) : [...current, id]));
  }

  return (
    <Dialog
      open={open}
      onClose={saving ? undefined : onClose}
      fullWidth
      fullScreen={fullScreen}
      aria-labelledby="meal-form-title"
    >
      <DialogTitle id="meal-form-title">{draft ? TITLES[draft.mode] : ''}</DialogTitle>
      <DialogContent>
        {loading ? <LoadingState label="Gerichte werden geladen …" /> : null}
        {loadError ? <ErrorState message={loadError} /> : null}
        {!loading && !loadError && dishes && members ? (
          <Stack spacing={2} sx={{ pt: 0.5 }}>
            <TextField
              label="Datum"
              type="date"
              value={mealDate}
              onChange={(event) => setMealDate(event.target.value)}
              fullWidth
              slotProps={{ inputLabel: { shrink: true }, htmlInput: { 'data-testid': 'meal-date' } }}
            />
            <ChoiceButtons
              label="Mahlzeit"
              options={MEAL_TYPES.map((type) => ({ value: type, label: MEAL_TYPE_LABELS[type] }))}
              selected={mealType}
              onChange={setMealType}
            />
            <TextField
              label="Gericht suchen"
              value={dishQuery}
              onChange={(event) => setDishQuery(event.target.value)}
              fullWidth
              slotProps={{ htmlInput: { 'data-testid': 'meal-dish-search' } }}
            />
            {visibleDishes.length === 0 ? (
              <Stack spacing={1}>
                <Typography color="text.secondary">Kein passendes Gericht.</Typography>
                <Button component={RouterLink} to="/gerichte/neu" variant="outlined" fullWidth>
                  Gericht anlegen
                </Button>
              </Stack>
            ) : (
              <Stack spacing={1}>
                {visibleDishes.map((dish) => (
                  <Button
                    key={dish.id}
                    type="button"
                    fullWidth
                    variant={dish.id === dishId ? 'contained' : 'outlined'}
                    aria-pressed={dish.id === dishId}
                    data-testid="meal-dish-option"
                    onClick={() => {
                      setDishId(dish.id);
                      setVariantId('');
                    }}
                    sx={{ justifyContent: 'flex-start', textAlign: 'left', whiteSpace: 'normal' }}
                  >
                    {dish.name}
                  </Button>
                ))}
              </Stack>
            )}
            {dishId && variants.length > 0 ? (
              <ChoiceButtons
                label="Variante"
                options={[
                  { value: '', label: 'Grundgericht' },
                  ...variants.map((variant) => ({ value: variant.id, label: variant.name })),
                ]}
                selected={variantId}
                onChange={setVariantId}
              />
            ) : null}
            <ChoiceButtons
              label="Mitessende"
              options={people}
              selected={dinerIds}
              onChange={toggleDiner}
            />
            <TextField
              label="Kommentar"
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              fullWidth
              multiline
              minRows={2}
              slotProps={{ htmlInput: { 'data-testid': 'meal-comment', maxLength: 500 } }}
            />
            {error ? <ErrorState message={error} /> : null}
          </Stack>
        ) : null}
      </DialogContent>
      <DialogActions sx={{ flexDirection: 'column', alignItems: 'stretch', gap: 1, px: 3, pb: 2 }}>
        <Button
          type="button"
          variant="contained"
          fullWidth
          disabled={saving || loading || Boolean(loadError)}
          data-testid="meal-save"
          onClick={() => void save()}
        >
          {saving ? 'Wird gespeichert …' : draft ? SUBMIT_LABELS[draft.mode] : 'Speichern'}
        </Button>
        <Button type="button" variant="outlined" fullWidth disabled={saving} onClick={onClose}>
          Abbrechen
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function memberOptions(
  members: Member[],
  participants: { userId: string | null; displayName: string }[],
): { value: string; label: string }[] {
  const options = new Map<string, string>();
  for (const member of members) {
    options.set(member.id, member.displayName);
  }
  for (const participant of participants) {
    if (participant.userId && !options.has(participant.userId)) {
      options.set(participant.userId, `${participant.displayName} (nicht aktiv)`);
    }
  }
  return [...options.entries()].map(([value, label]) => ({ value, label }));
}
