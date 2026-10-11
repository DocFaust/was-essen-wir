import Button from '@mui/material/Button';
import FormControlLabel from '@mui/material/FormControlLabel';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useRef, useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router';
import { ApiError, createVariant, getDish, updateVariant } from './api';
import { EFFORTS, EFFORT_LABELS, SUITABILITY_SETTING_LABELS, emptyToNull } from './labels';
import type { DishDetail, DishVariant, Effort, SuitabilitySetting, VariantWrite } from './types';
import { ChoiceButtons, ErrorState, LoadingState, NameListEditor, StickyActions } from './ui';

type FormState = {
  name: string;
  description: string;
  effort: Effort | '';
  ingredientsSpecified: boolean;
  ingredients: string[];
  recipeText: string;
  recipeUrl: string;
  recipeSource: string;
  mealPrep: SuitabilitySetting;
  homeOffice: SuitabilitySetting;
};

const EMPTY_FORM: FormState = {
  name: '',
  description: '',
  effort: '',
  ingredientsSpecified: false,
  ingredients: [],
  recipeText: '',
  recipeUrl: '',
  recipeSource: '',
  mealPrep: 'INHERIT',
  homeOffice: 'INHERIT',
};

export function VariantFormPage() {
  const { id, variantId } = useParams();
  const [dish, setDish] = useState<DishDetail | null>(null);
  const [loading, setLoading] = useState(true);
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
    return <LoadingState label="Variante wird geladen …" />;
  }
  if (error || !dish || !id) {
    return <ErrorState message={error ?? 'Das Gericht konnte nicht geladen werden.'} onRetry={() => setAttempt((current) => current + 1)} />;
  }

  const variant = variantId ? dish.variants.find((item) => item.id === variantId) : undefined;
  if (variantId && !variant) {
    return <ErrorState message="Variante wurde nicht gefunden." />;
  }

  return <VariantForm dish={dish} variant={variant} />;
}

function VariantForm({ dish, variant }: { dish: DishDetail; variant?: DishVariant }) {
  const navigate = useNavigate();
  const [form, setForm] = useState<FormState>(() => (variant ? fromVariant(variant) : EMPTY_FORM));
  const [detailsOpen, setDetailsOpen] = useState(Boolean(variant));
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [message, setMessage] = useState<string | null>(null);

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  async function save() {
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
    const body = toWrite(form);
    try {
      const saved = variant
        ? await updateVariant(dish.id, variant.id, body)
        : await createVariant(dish.id, body);
      navigate(`/gerichte/${saved.id}`, { state: { notice: 'Variante gespeichert.' } });
    } catch (caught) {
      setMessage(caught instanceof ApiError ? caught.message : 'Die Variante konnte nicht gespeichert werden.');
      savingRef.current = false;
      setSaving(false);
    }
  }

  const inheritedSource = dish.recipeSource ?? 'keine Angabe';

  return (
    <Stack spacing={2} component="form" noValidate onSubmit={(event) => { event.preventDefault(); void save(); }}>
      <Typography variant="h1" component="h1">
        {variant ? 'Variante bearbeiten' : 'Neue Variante'}
      </Typography>
      <Typography color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
        Grundgericht: {dish.name}
      </Typography>
      {message ? <ErrorState message={message} /> : null}
      <TextField
        label="Name"
        value={form.name}
        onChange={(event) => update('name', event.target.value)}
        required
        fullWidth
        autoComplete="off"
        slotProps={{ htmlInput: { 'data-testid': 'variant-name' } }}
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
            helperText="Leer lassen übernimmt die Beschreibung des Grundgerichts."
            fullWidth
            multiline
            minRows={2}
          />
          <ChoiceButtons
            label="Aufwand"
            options={[
              { value: '' as const, label: 'Vom Grundgericht' },
              ...EFFORTS.map((effort) => ({ value: effort, label: EFFORT_LABELS[effort] })),
            ]}
            selected={form.effort}
            onChange={(effort) => update('effort', effort)}
          />
          <FormControlLabel
            sx={{ minHeight: 48, mx: 0 }}
            control={
              <Switch
                checked={form.ingredientsSpecified}
                onChange={(event) => update('ingredientsSpecified', event.target.checked)}
              />
            }
            label="Eigene Zutatenliste"
          />
          <Typography variant="body2" color="text.secondary">
            Ohne eigene Liste gelten die Zutaten des Grundgerichts. Eine leere eigene Liste bleibt leer.
          </Typography>
          {form.ingredientsSpecified ? (
            <NameListEditor
              label="Zutaten"
              values={form.ingredients}
              onChange={(ingredients) => update('ingredients', ingredients)}
              addLabel="Zutat hinzufügen"
            />
          ) : null}
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
          />
          <TextField
            label="Rezeptquelle"
            value={form.recipeSource}
            onChange={(event) => update('recipeSource', event.target.value)}
            helperText={`Leer lassen übernimmt die Quelle des Grundgerichts (${inheritedSource}).`}
            fullWidth
            slotProps={{ htmlInput: { 'data-testid': 'variant-recipe-source' } }}
          />
          <ChoiceButtons
            label="Meal Prep"
            options={(Object.keys(SUITABILITY_SETTING_LABELS) as SuitabilitySetting[]).map((value) => ({
              value,
              label: SUITABILITY_SETTING_LABELS[value],
            }))}
            selected={form.mealPrep}
            onChange={(mealPrep) => update('mealPrep', mealPrep)}
          />
          <ChoiceButtons
            label="Homeoffice"
            options={(Object.keys(SUITABILITY_SETTING_LABELS) as SuitabilitySetting[]).map((value) => ({
              value,
              label: SUITABILITY_SETTING_LABELS[value],
            }))}
            selected={form.homeOffice}
            onChange={(homeOffice) => update('homeOffice', homeOffice)}
          />
        </Stack>
      ) : null}
      <StickyActions>
        <Button type="submit" variant="contained" fullWidth disabled={saving} data-testid="save-variant">
          {saving ? 'Wird gespeichert …' : 'Speichern'}
        </Button>
        <Button component={RouterLink} to={`/gerichte/${dish.id}`} variant="text" fullWidth>
          Abbrechen
        </Button>
      </StickyActions>
    </Stack>
  );
}

function fromVariant(variant: DishVariant): FormState {
  return {
    name: variant.name,
    description: variant.description ?? '',
    effort: variant.effort ?? '',
    ingredientsSpecified: variant.ingredientsSpecified,
    ingredients: variant.ingredients,
    recipeText: variant.recipeText ?? '',
    recipeUrl: variant.recipeUrl ?? '',
    recipeSource: variant.recipeSource ?? '',
    mealPrep: variant.mealPrep,
    homeOffice: variant.homeOffice,
  };
}

function toWrite(form: FormState): VariantWrite {
  return {
    name: form.name.trim(),
    description: emptyToNull(form.description),
    effort: form.effort || null,
    ingredientsSpecified: form.ingredientsSpecified,
    ingredients: form.ingredientsSpecified ? form.ingredients : [],
    recipeText: emptyToNull(form.recipeText),
    recipeUrl: emptyToNull(form.recipeUrl),
    recipeSource: emptyToNull(form.recipeSource),
    mealPrep: form.mealPrep,
    homeOffice: form.homeOffice,
  };
}
