import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useState, type FormEvent } from 'react';
import { Link as RouterLink, useLocation } from 'react-router';
import { useViewerId } from '../auth/AuthProvider';
import { ApiError, clearDishRating, listDishes, setDishRating } from './api';
import { EFFORT_LABELS, MEAL_TYPE_LABELS, MEAL_TYPES, SUITABILITY_LABELS, noticeFrom } from './labels';
import { StarRating } from './StarRating';
import type { DishQuery, DishSummary, Effort, MealType, Suitability } from './types';
import { useRatingActions } from './useRatingActions';
import { ErrorState, LoadingState, Notice } from './ui';

const SUITABILITY_FILTERS: Suitability[] = ['SUITABLE', 'UNSUITABLE', 'UNKNOWN'];

export function DishListPage() {
  const location = useLocation();
  const [query, setQuery] = useState('');
  const [mealType, setMealType] = useState('');
  const [effort, setEffort] = useState('');
  const [mealPrep, setMealPrep] = useState('');
  const [homeOffice, setHomeOffice] = useState('');
  const [filtersOpen, setFiltersOpen] = useState(false);
  const viewerId = useViewerId();
  const [dishes, setDishes] = useState<DishSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [requestId, setRequestId] = useState(0);
  const [activeQuery, setActiveQuery] = useState<DishQuery>({});
  const [seenViewer, setSeenViewer] = useState(viewerId);
  const ratings = useRatingActions();
  if (seenViewer !== viewerId) {
    setSeenViewer(viewerId);
    setDishes([]);
    setLoading(true);
    setError(null);
  }

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(null);
    listDishes(activeQuery)
      .then((result) => {
        if (!controller.signal.aborted) {
          setDishes(result);
          setLoading(false);
        }
      })
      .catch((caught: unknown) => {
        if (controller.signal.aborted) {
          return;
        }
        setError(caught instanceof ApiError ? caught.message : 'Die Gerichte konnten nicht geladen werden.');
        setLoading(false);
      });
    return () => controller.abort();
  }, [activeQuery, requestId, viewerId]);

  function publishRating(id: string, stars: number | null) {
    setDishes((current) => current.map((item) => (item.id === id ? { ...item, myRating: stars } : item)));
  }

  function rate(dish: DishSummary, stars: number | null) {
    const key = `dish:${dish.id}`;
    void ratings.commit(
      key,
      stars,
      dish.myRating,
      () => (stars == null ? clearDishRating(dish.id) : setDishRating(dish.id, stars).then(() => undefined)),
      (next) => publishRating(dish.id, next),
    );
  }

  function search(event: FormEvent) {
    event.preventDefault();
    setActiveQuery({
      q: query.trim(),
      mealType,
      effort,
      mealPrep,
      homeOffice,
    });
  }

  return (
    <Stack spacing={2}>
      <Typography variant="h1" component="h1">
        Gerichte
      </Typography>
      <Notice message={noticeFrom(location.state)} />
      <Stack component="form" spacing={1.5} onSubmit={search}>
        <TextField
          label="Suche"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          fullWidth
          slotProps={{ htmlInput: { 'data-testid': 'dish-search' } }}
        />
        <Button type="button" variant="text" fullWidth aria-expanded={filtersOpen} onClick={() => setFiltersOpen((open) => !open)}>
          {filtersOpen ? 'Filter ausblenden' : 'Filter'}
        </Button>
        {filtersOpen ? (
          <Stack spacing={1.5}>
            <TextField select label="Mahlzeit" value={mealType} onChange={(event) => setMealType(event.target.value)} fullWidth>
              <MenuItem value="">Alle</MenuItem>
              {MEAL_TYPES.map((type) => (
                <MenuItem key={type} value={type}>
                  {MEAL_TYPE_LABELS[type]}
                </MenuItem>
              ))}
            </TextField>
            <TextField select label="Aufwand" value={effort} onChange={(event) => setEffort(event.target.value)} fullWidth>
              <MenuItem value="">Alle</MenuItem>
              {(Object.keys(EFFORT_LABELS) as Effort[]).map((value) => (
                <MenuItem key={value} value={value}>
                  {EFFORT_LABELS[value]}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="Meal Prep"
              value={mealPrep}
              onChange={(event) => setMealPrep(event.target.value)}
              fullWidth
            >
              <MenuItem value="">Alle</MenuItem>
              {SUITABILITY_FILTERS.map((value) => (
                <MenuItem key={value} value={value}>
                  {SUITABILITY_LABELS[value]}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="Homeoffice"
              value={homeOffice}
              onChange={(event) => setHomeOffice(event.target.value)}
              fullWidth
            >
              <MenuItem value="">Alle</MenuItem>
              {SUITABILITY_FILTERS.map((value) => (
                <MenuItem key={value} value={value}>
                  {SUITABILITY_LABELS[value]}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
        ) : null}
        <Button type="submit" variant="contained" fullWidth>
          Suchen
        </Button>
      </Stack>
      <Button component={RouterLink} to="/gerichte/neu" variant="contained" fullWidth data-testid="new-dish">
        Neues Gericht
      </Button>
      {loading ? <LoadingState label="Gerichte werden geladen …" /> : null}
      {error ? <ErrorState message={error} onRetry={() => setRequestId((current) => current + 1)} /> : null}
      {!loading && !error && dishes.length === 0 ? (
        <Alert severity="info">Noch keine Gerichte. Ein Name genügt für den Anfang.</Alert>
      ) : null}
      {!loading && !error
        ? dishes.map((dish) => (
            <Stack
              key={dish.id}
              spacing={1}
              data-testid="dish-card"
              sx={{ border: 1, borderColor: 'divider', borderRadius: 2, p: 1.5, minWidth: 0 }}
            >
              <StarRating
                label={`Deine Bewertung für ${dish.name}`}
                value={dish.myRating}
                busy={ratings.busy[`dish:${dish.id}`] === true}
                error={ratings.errors[`dish:${dish.id}`] ?? null}
                onChange={(stars) => rate(dish, stars)}
                onClear={() => rate(dish, null)}
                testId={`list-rating-${dish.id}`}
              />
              <Button
                component={RouterLink}
                to={`/gerichte/${dish.id}`}
                variant="outlined"
                fullWidth
                sx={{ justifyContent: 'flex-start', textAlign: 'left', whiteSpace: 'normal' }}
              >
                <Stack sx={{ alignItems: 'flex-start', minWidth: 0 }}>
                  <Typography component="span" sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}>
                    {dish.name}
                  </Typography>
                  <Typography component="span" variant="body2" color="text.secondary">
                    {subtitle(dish)}
                  </Typography>
                </Stack>
              </Button>
            </Stack>
          ))
        : null}
    </Stack>
  );
}

function subtitle(dish: DishSummary): string {
  const parts = [
    dish.mealTypes.map((type) => MEAL_TYPE_LABELS[type as MealType]).join(', '),
    dish.effort ? EFFORT_LABELS[dish.effort] : '',
    dish.variantNames.join(', '),
  ].filter((part) => part.length > 0);
  return parts.length > 0 ? parts.join(' · ') : 'Ohne weitere Angaben';
}
