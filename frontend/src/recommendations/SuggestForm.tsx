import Accordion from '@mui/material/Accordion';
import AccordionDetails from '@mui/material/AccordionDetails';
import AccordionSummary from '@mui/material/AccordionSummary';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import FormControlLabel from '@mui/material/FormControlLabel';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import Typography from '@mui/material/Typography';
import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../http';
import { MEAL_TYPE_LABELS, MEAL_TYPES } from '../dishes/labels';
import type { MealType } from '../dishes/types';
import { ChoiceButtons, ErrorState, LoadingState, SuccessSnackbar } from '../dishes/ui';
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

export function SuggestForm({ userId, onRecorded }: { userId: string; onRecorded?: () => void }) {
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
  const [notice, setNotice] = useState<string | null>(null);
  const requestId = useRef(0);
  const inFlight = useRef(false);

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
  const activePreferences = [preferSimple, preferHomeOffice, preferMealPrep].filter(Boolean).length;

  async function showSuggestions(nextExcluded: ExcludedDish[]) {
    if (inFlight.current || selectedDiners.length === 0) {
      return;
    }
    const id = requestId.current + 1;
    requestId.current = id;
    inFlight.current = true;
    setLoading(true);
    setError(null);
    setResult(null);
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
      if (id !== requestId.current) {
        return;
      }
      setResult(response);
    } catch (caught) {
      if (id !== requestId.current) {
        return;
      }
      setError(caught instanceof ApiError ? caught.message : 'Die Vorschläge konnten nicht berechnet werden.');
    } finally {
      if (id === requestId.current) {
        inFlight.current = false;
        setLoading(false);
      }
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
    <Stack spacing={2} data-testid="suggest-form">
      <Typography variant="h2" sx={{ fontSize: '1.25rem' }}>
        Vorschläge
      </Typography>
      <ChoiceButtons
        label="Mahlzeit"
        layout="wrap"
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
      <Accordion disableGutters elevation={0} sx={{ border: 1, borderColor: 'divider', '&:before': { display: 'none' } }}>
        <AccordionSummary expandIcon={<span aria-hidden="true">▾</span>} aria-controls="suggest-preferences" id="suggest-preferences-header">
          <Typography component="span">
            Weitere Einstellungen{activePreferences > 0 ? ` (${activePreferences})` : ''}
          </Typography>
        </AccordionSummary>
        <AccordionDetails id="suggest-preferences">
          <Stack spacing={0.5} component="fieldset" sx={{ border: 0, p: 0, m: 0, minWidth: 0 }}>
            <Typography component="legend" variant="body2" color="text.secondary">
              Seltener gebraucht
            </Typography>
            <PreferenceSwitch label="Einfaches oder schnelles Essen" checked={preferSimple} onChange={setPreferSimple} />
            <PreferenceSwitch label="Für Homeoffice geeignet" checked={preferHomeOffice} onChange={setPreferHomeOffice} />
            <PreferenceSwitch label="Für Meal Prep geeignet" checked={preferMealPrep} onChange={setPreferMealPrep} />
          </Stack>
        </AccordionDetails>
      </Accordion>
      <Button
        type="button"
        variant="contained"
        fullWidth
        disabled={loading || selectedDiners.length === 0}
        data-testid="show-suggestions"
        onClick={() => void showSuggestions(excluded)}
      >
        {loading ? 'Wird berechnet …' : 'Was essen wir?'}
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
            <Button type="button" color="inherit" variant="outlined" fullWidth disabled={loading} onClick={() => void showSuggestions([])}>
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
          {result.suggestions.length === 0 && !result.explanation ? (
            <Alert severity="info" data-testid="suggestion-explanation">
              Keine passenden Gerichte. Passe Mahlzeit oder Mitessende an, oder lege ein Gericht an.
            </Alert>
          ) : null}
          {result.suggestions.map((suggestion) => (
            <SuggestionCard
              key={`${suggestion.dishId}:${suggestion.variantId ?? 'base'}`}
              suggestion={suggestion}
              disabled={loading}
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
                  dishName: suggestion.name,
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
                  dishName: suggestion.name,
                  variantId: suggestion.variantId,
                  dinerIds: selectedDiners,
                })
              }
            />
          ))}
          {result.suggestions.length > 0 ? (
            <Button type="button" variant="outlined" fullWidth data-testid="suggest-again" onClick={() => void showSuggestions(excluded)}>
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
