import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import FormControlLabel from '@mui/material/FormControlLabel';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import { ApiError } from '../http';
import { MEAL_TYPE_LABELS, MEAL_TYPES } from '../dishes/labels';
import type { MealType } from '../dishes/types';
import { ChoiceButtons, ErrorState, LoadingState } from '../dishes/ui';
import { listMembers, recommend } from './api';
import { SuggestionCard } from './SuggestionCard';
import type { Member, RecommendationResponse } from './types';
import { MealEventForm } from '../meals/MealEventForm';
import { createHistory, createPlan } from '../meals/api';
import { todayIso } from '../meals/dates';
import type { MealDraft, MealEventWrite } from '../meals/types';

type ExcludedDish = {
  id: string;
  name: string;
};

export function SuggestForm({ userId }: { userId: string }) {
  const [attempt, setAttempt] = useState(0);
  const [members, setMembers] = useState<Member[] | null>(null);
  const [membersError, setMembersError] = useState<string | null>(null);
  const [mealType, setMealType] = useState<MealType>('ABENDESSEN');
  const [dinerIds, setDinerIds] = useState<string[] | null>(null);
  const [preferSimple, setPreferSimple] = useState(false);
  const [preferHomeOffice, setPreferHomeOffice] = useState(false);
  const [preferMealPrep, setPreferMealPrep] = useState(false);
  const [excluded, setExcluded] = useState<ExcludedDish[]>([]);
  const [result, setResult] = useState<RecommendationResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [mealDraft, setMealDraft] = useState<MealDraft | null>(null);

  useEffect(() => {
    let active = true;
    setMembers(null);
    setMembersError(null);
    listMembers()
      .then((loaded) => {
        if (!active) {
          return;
        }
        setMembers(loaded);
        setDinerIds(loaded.some((member) => member.id === userId) ? [userId] : loaded.slice(0, 1).map((member) => member.id));
      })
      .catch((caught: unknown) => {
        if (!active) {
          return;
        }
        setMembersError(caught instanceof ApiError ? caught.message : 'Die Mitessenden konnten nicht geladen werden.');
      });
    return () => {
      active = false;
    };
  }, [attempt, userId]);

  const selectedDiners = dinerIds ?? [];

  async function showSuggestions(nextExcluded: ExcludedDish[]) {
    setLoading(true);
    setError(null);
    setExcluded(nextExcluded);
    try {
      const response = await recommend({
        mealType,
        dinerIds: selectedDiners,
        preferSimple,
        preferHomeOffice,
        preferMealPrep,
        excludedDishIds: nextExcluded.map((dish) => dish.id),
        seed: nextSeed(),
      });
      setResult(response);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Die Vorschläge konnten nicht berechnet werden.');
    } finally {
      setLoading(false);
    }
  }

  function toggleDiner(id: string) {
    setDinerIds((current) => {
      const ids = current ?? [];
      return ids.includes(id) ? ids.filter((item) => item !== id) : [...ids, id];
    });
  }

  if (membersError) {
    return <ErrorState message={membersError} onRetry={() => setAttempt((current) => current + 1)} />;
  }
  if (!members || dinerIds === null) {
    return <LoadingState label="Mitessende werden geladen …" />;
  }

  return (
    <Stack spacing={2.5} data-testid="suggest-form">
      <ChoiceButtons
        label="Mahlzeit"
        options={MEAL_TYPES.map((type) => ({ value: type, label: MEAL_TYPE_LABELS[type] }))}
        selected={mealType}
        onChange={setMealType}
      />
      <ChoiceButtons
        label="Mitessende"
        options={members.map((member) => ({ value: member.id, label: member.displayName }))}
        selected={selectedDiners}
        onChange={toggleDiner}
      />
      {selectedDiners.length === 0 ? (
        <Typography color="error" role="alert">
          Mindestens eine Person muss ausgewählt sein.
        </Typography>
      ) : null}
      <Stack spacing={0.5} component="fieldset" sx={{ border: 0, p: 0, m: 0, minWidth: 0 }}>
        <Typography component="legend" variant="body2" color="text.secondary">
          Optionaler Kontext
        </Typography>
        <PreferenceSwitch
          label="Einfaches oder schnelles Essen"
          checked={preferSimple}
          onChange={setPreferSimple}
        />
        <PreferenceSwitch
          label="Für Homeoffice geeignet"
          checked={preferHomeOffice}
          onChange={setPreferHomeOffice}
        />
        <PreferenceSwitch
          label="Für Meal Prep geeignet"
          checked={preferMealPrep}
          onChange={setPreferMealPrep}
        />
      </Stack>
      <Button
        type="button"
        variant="contained"
        fullWidth
        disabled={loading || selectedDiners.length === 0}
        data-testid="show-suggestions"
        onClick={() => void showSuggestions(excluded)}
      >
        {loading ? 'Wird berechnet …' : 'Vorschläge anzeigen'}
      </Button>
      {error ? <ErrorState message={error} onRetry={() => void showSuggestions(excluded)} /> : null}
      {loading ? <LoadingState label="Vorschläge werden berechnet …" /> : null}
      {excluded.length > 0 ? (
        <Alert severity="info">
          <Stack spacing={1}>
            <span>
              {excluded.length === 1
                ? `${excluded[0].name} ist ausgeschlossen.`
                : `${excluded.length} Gerichte sind ausgeschlossen.`}
            </span>
            <Button
              type="button"
              color="inherit"
              variant="outlined"
              fullWidth
              disabled={loading}
              onClick={() => void showSuggestions([])}
            >
              Ausschlüsse aufheben
            </Button>
          </Stack>
        </Alert>
      ) : null}
      {result && !loading ? (
        <Stack spacing={1.5} data-testid="suggestions" aria-busy={false}>
          {result.explanation ? (
            <Alert severity="info" data-testid="suggestion-explanation">
              {result.explanation}
            </Alert>
          ) : null}
          {result.suggestions.map((suggestion) => (
            <SuggestionCard
              key={`${suggestion.dishId}:${suggestion.variantId ?? 'base'}`}
              suggestion={suggestion}
              disabled={false}
              onExclude={() =>
                void showSuggestions([
                  ...excluded.filter((dish) => dish.id !== suggestion.dishId),
                  { id: suggestion.dishId, name: suggestion.dishName },
                ])
              }
              onPlan={() =>
                setMealDraft({
                  mode: 'plan',
                  mealDate: todayIso(),
                  mealType,
                  dishId: suggestion.dishId,
                  variantId: suggestion.variantId,
                  dinerIds: selectedDiners,
                })
              }
              onEaten={() =>
                setMealDraft({
                  mode: 'eaten',
                  mealDate: todayIso(),
                  mealType,
                  dishId: suggestion.dishId,
                  variantId: suggestion.variantId,
                  dinerIds: selectedDiners,
                })
              }
            />
          ))}
          {result.suggestions.length > 0 ? (
            <Button
              type="button"
              variant="outlined"
              fullWidth
              data-testid="suggest-again"
              onClick={() => void showSuggestions(excluded)}
            >
              Neu vorschlagen
            </Button>
          ) : null}
        </Stack>
      ) : null}
      <MealEventForm
        draft={mealDraft}
        userId={userId}
        onClose={() => setMealDraft(null)}
        onSubmit={async (body: MealEventWrite) => {
          if (mealDraft?.mode === 'plan') {
            await createPlan(body);
          } else {
            await createHistory(body);
          }
          setMealDraft(null);
        }}
      />
    </Stack>
  );
}

function PreferenceSwitch({
  label,
  checked,
  onChange,
}: {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <FormControlLabel
      sx={{ mx: 0, minHeight: 48, alignItems: 'center' }}
      control={<Switch checked={checked} onChange={(_, value) => onChange(value)} />}
      label={label}
    />
  );
}

function nextSeed(): number {
  const values = new Uint32Array(1);
  crypto.getRandomValues(values);
  return values[0];
}
