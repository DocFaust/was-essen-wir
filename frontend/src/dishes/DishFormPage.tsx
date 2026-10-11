import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useRef, useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router';
import { ApiError, createDish, getDish, updateDish } from './api';
import { EFFORTS, EFFORT_LABELS, MEAL_TYPES, MEAL_TYPE_LABELS, SUITABILITY_LABELS, emptyToNull } from './labels';
import type { DishDetail, DishWrite, Effort, MealType, Suitability } from './types';
import { ChoiceButtons, ConfirmDialog, ErrorState, LoadingState, NameListEditor, StickyActions } from './ui';

type FormState = {
  name: string;
  description: string;
  mealTypes: MealType[];
  categories: string[];
  effort: Effort | '';
  ingredients: string[];
  recipeText: string;
  recipeUrl: string;
  recipeSource: string;
  comments: string;
  mealPrep: Suitability;
  homeOffice: Suitability;
};

const EMPTY_FORM: FormState = {
  name: '',
  description: '',
  mealTypes: [],
  categories: [],
  effort: '',
  ingredients: [],
  recipeText: '',
  recipeUrl: '',
  recipeSource: '',
  comments: '',
  mealPrep: 'UNKNOWN',
  homeOffice: 'UNKNOWN',
};

export function DishFormPage() {
  const { id } = useParams();
  const [dish, setDish] = useState<DishDetail | null>(null);
  const [loading, setLoading] = useState(Boolean(id));
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (!id) {
      return;
    }
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    getDish(id)
      .then((loaded) => {
        if (!controller.signal.aborted) {
          setDish(loaded);
          setLoading(false);
        }
      })
      .catch((caught: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        setError(caught instanceof ApiError ? caught.message : 'Das Gericht konnte nicht geladen werden.');
        setLoading(false);
      });
    return () => controller.abort();
  }, [id, attempt]);

  if (loading) {
    return <LoadingState label="Gericht wird geladen …" />;
  }
  if (error || (id && !dish)) {
    return <ErrorState message={error ?? 'Das Gericht konnte nicht geladen werden.'} onRetry={() => setAttempt((current) => current + 1)} />;
  }

  return <DishForm dishId={id} initial={dish} />;
}

function DishForm({ dishId, initial }: { dishId?: string; initial: DishDetail | null }) {
  const navigate = useNavigate();
  const [form, setForm] = useState<FormState>(() => (initial ? fromDish(initial) : EMPTY_FORM));
  const [detailsOpen, setDetailsOpen] = useState(Boolean(initial));
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [message, setMessage] = useState<string | null>(null);
  const [duplicateOpen, setDuplicateOpen] = useState(false);

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  async function save(confirmDuplicate: boolean) {
    if (savingRef.current) {
      return;
    }
    const name = form.name.trim();
    if (!name) {
      setMessage('Der Name ist erforderlich.');
      return;
    }
    savingRef.current = true;
    setSaving(true);
    setMessage(null);
    const body = toWrite(form, confirmDuplicate);
    try {
      const saved = dishId ? await updateDish(dishId, body) : await createDish(body);
      navigate(`/gerichte/${saved.id}`, { state: { notice: 'Gericht gespeichert.' } });
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === 'DUPLICATE_DISH_NAME') {
        setDuplicateOpen(true);
      } else {
        setMessage(caught instanceof ApiError ? caught.message : 'Das Gericht konnte nicht gespeichert werden.');
      }
      savingRef.current = false;
      setSaving(false);
    }
  }

  return (
    <Stack spacing={2} component="form" noValidate onSubmit={(event) => { event.preventDefault(); void save(false); }}>
      <Typography variant="h1" component="h1">
        {dishId ? 'Gericht bearbeiten' : 'Neues Gericht'}
      </Typography>
      {message ? <ErrorState message={message} /> : null}
      <TextField
        label="Name"
        value={form.name}
        onChange={(event) => update('name', event.target.value)}
        required
        fullWidth
        autoComplete="off"
        slotProps={{ htmlInput: { 'data-testid': 'dish-name' } }}
      />
      <Button type="button" variant="text" fullWidth aria-expanded={detailsOpen} onClick={() => setDetailsOpen((open) => !open)}>
        Weitere Angaben
      </Button>
      {detailsOpen ? (
        <Stack spacing={2}>
          <TextField
            label="Beschreibung"
            value={form.description}
            onChange={(event) => update('description', event.target.value)}
            fullWidth
            multiline
            minRows={2}
          />
          <ChoiceButtons
            label="Mahlzeiten"
            options={MEAL_TYPES.map((type) => ({ value: type, label: MEAL_TYPE_LABELS[type] }))}
            selected={form.mealTypes}
            onChange={(type) =>
              update(
                'mealTypes',
                form.mealTypes.includes(type)
                  ? form.mealTypes.filter((item) => item !== type)
                  : [...form.mealTypes, type],
              )
            }
          />
          <NameListEditor
            label="Kategorien"
            values={form.categories}
            onChange={(categories) => update('categories', categories)}
            addLabel="Kategorie hinzufügen"
          />
          <ChoiceButtons
            label="Aufwand"
            options={[
              { value: '' as const, label: 'Keine Angabe' },
              ...EFFORTS.map((effort) => ({ value: effort, label: EFFORT_LABELS[effort] })),
            ]}
            selected={form.effort}
            onChange={(effort) => update('effort', effort)}
          />
          <NameListEditor
            label="Zutaten"
            values={form.ingredients}
            onChange={(ingredients) => update('ingredients', ingredients)}
            addLabel="Zutat hinzufügen"
            inputTestId="ingredient-name"
          />
          <TextField
            label="Rezept"
            value={form.recipeText}
            onChange={(event) => update('recipeText', event.target.value)}
            fullWidth
            multiline
            minRows={3}
          />
          <TextField
            label="Rezeptlink"
            value={form.recipeUrl}
            onChange={(event) => update('recipeUrl', event.target.value)}
            helperText="Optional, mit http:// oder https://"
            fullWidth
            slotProps={{ htmlInput: { inputMode: 'url' } }}
          />
          <TextField
            label="Rezeptquelle"
            value={form.recipeSource}
            onChange={(event) => update('recipeSource', event.target.value)}
            helperText="Zum Beispiel Eigenkreation, Chefkoch oder Von Oma."
            fullWidth
            slotProps={{ htmlInput: { 'data-testid': 'dish-recipe-source' } }}
          />
          <TextField
            label="Kommentar"
            value={form.comments}
            onChange={(event) => update('comments', event.target.value)}
            fullWidth
            multiline
            minRows={2}
          />
          <ChoiceButtons
            label="Meal Prep"
            options={(Object.keys(SUITABILITY_LABELS) as Suitability[]).map((value) => ({
              value,
              label: SUITABILITY_LABELS[value],
            }))}
            selected={form.mealPrep}
            onChange={(mealPrep) => update('mealPrep', mealPrep)}
          />
          <ChoiceButtons
            label="Homeoffice"
            options={(Object.keys(SUITABILITY_LABELS) as Suitability[]).map((value) => ({
              value,
              label: SUITABILITY_LABELS[value],
            }))}
            selected={form.homeOffice}
            onChange={(homeOffice) => update('homeOffice', homeOffice)}
          />
        </Stack>
      ) : null}
      <StickyActions>
        <Button type="submit" variant="contained" fullWidth disabled={saving} data-testid="save-dish">
          {saving ? 'Wird gespeichert …' : 'Speichern'}
        </Button>
        <Button component={RouterLink} to={dishId ? `/gerichte/${dishId}` : '/gerichte'} variant="text" fullWidth>
          Abbrechen
        </Button>
      </StickyActions>
      <ConfirmDialog
        open={duplicateOpen}
        title="Name schon vergeben"
        message="Es gibt bereits ein Gericht mit diesem Namen. Trotzdem speichern?"
        confirmLabel="Trotzdem speichern"
        onConfirm={() => {
          setDuplicateOpen(false);
          void save(true);
        }}
        onClose={() => setDuplicateOpen(false)}
      />
    </Stack>
  );
}

function fromDish(dish: DishDetail): FormState {
  return {
    name: dish.name,
    description: dish.description ?? '',
    mealTypes: dish.mealTypes,
    categories: dish.categories,
    effort: dish.effort ?? '',
    ingredients: dish.ingredients,
    recipeText: dish.recipeText ?? '',
    recipeUrl: dish.recipeUrl ?? '',
    recipeSource: dish.recipeSource ?? '',
    comments: dish.comments ?? '',
    mealPrep: dish.mealPrep,
    homeOffice: dish.homeOffice,
  };
}

function toWrite(form: FormState, confirmDuplicate: boolean): DishWrite {
  return {
    name: form.name.trim(),
    description: emptyToNull(form.description),
    mealTypes: form.mealTypes,
    categories: form.categories,
    effort: form.effort || null,
    ingredients: form.ingredients,
    recipeText: emptyToNull(form.recipeText),
    recipeUrl: emptyToNull(form.recipeUrl),
    recipeSource: emptyToNull(form.recipeSource),
    comments: emptyToNull(form.comments),
    mealPrep: form.mealPrep,
    homeOffice: form.homeOffice,
    confirmDuplicate,
  };
}
