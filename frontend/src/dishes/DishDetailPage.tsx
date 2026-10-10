import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import { Link as RouterLink, useLocation, useNavigate, useParams } from 'react-router';
import { useViewerId } from '../auth/AuthProvider';
import {
  ApiError,
  clearDishRating,
  clearVariantRating,
  deleteDish,
  deleteVariant,
  getDish,
  setDishRating,
  setVariantRating,
} from './api';
import { EFFORT_LABELS, MEAL_TYPE_LABELS, SUITABILITY_LABELS, noticeFrom } from './labels';
import { StarRating } from './StarRating';
import type { DishDetail, DishVariant } from './types';
import { useRatingActions } from './useRatingActions';
import { ConfirmDialog, ErrorState, LoadingState, Notice } from './ui';

export function DishDetailPage() {
  const { id } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const viewerId = useViewerId();
  const [dish, setDish] = useState<DishDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [variantToDelete, setVariantToDelete] = useState<DishVariant | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [seenViewer, setSeenViewer] = useState(viewerId);
  const ratings = useRatingActions();
  if (seenViewer !== viewerId) {
    setSeenViewer(viewerId);
    setDish(null);
    setLoading(true);
    setError(null);
    setActionError(null);
  }

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
  }, [id, attempt, viewerId]);

  function publishDishRating(stars: number | null) {
    setDish((current) => (current ? { ...current, myRating: stars } : current));
  }

  function publishVariantRating(variantId: string, stars: number | null) {
    setDish((current) =>
      current
        ? {
            ...current,
            variants: current.variants.map((variant) =>
              variant.id === variantId ? { ...variant, myRating: stars } : variant,
            ),
          }
        : current,
    );
  }

  function rateDish(stars: number | null) {
    if (!dish) {
      return;
    }
    void ratings.commit(
      `dish:${dish.id}`,
      stars,
      dish.myRating,
      () => (stars == null ? clearDishRating(dish.id) : setDishRating(dish.id, stars).then(() => undefined)),
      publishDishRating,
    );
  }

  function rateVariant(variant: DishVariant, stars: number | null) {
    if (!dish) {
      return;
    }
    void ratings.commit(
      `variant:${variant.id}`,
      stars,
      variant.myRating,
      () =>
        stars == null
          ? clearVariantRating(dish.id, variant.id)
          : setVariantRating(dish.id, variant.id, stars).then(() => undefined),
      (next) => publishVariantRating(variant.id, next),
    );
  }

  async function confirmDeleteDish() {
    if (!dish) {
      return;
    }
    try {
      await deleteDish(dish.id);
      navigate('/gerichte', { state: { notice: 'Gericht gelöscht.' } });
    } catch (caught) {
      setDeleteOpen(false);
      setActionError(caught instanceof ApiError ? caught.message : 'Das Gericht konnte nicht gelöscht werden.');
    }
  }

  async function confirmDeleteVariant() {
    if (!dish || !variantToDelete) {
      return;
    }
    try {
      const updated = await deleteVariant(dish.id, variantToDelete.id);
      setDish(updated);
      setVariantToDelete(null);
    } catch (caught) {
      setVariantToDelete(null);
      setActionError(caught instanceof ApiError ? caught.message : 'Die Variante konnte nicht gelöscht werden.');
    }
  }

  if (loading) {
    return <LoadingState label="Gericht wird geladen …" />;
  }
  if (error || !dish) {
    return (
      <Stack spacing={2}>
        <ErrorState message={error ?? 'Das Gericht konnte nicht geladen werden.'} onRetry={() => setAttempt((current) => current + 1)} />
        <Button component={RouterLink} to="/gerichte" variant="text" fullWidth>
          Zur Übersicht
        </Button>
      </Stack>
    );
  }

  return (
    <Stack spacing={2}>
      <Button component={RouterLink} to="/gerichte" variant="text" fullWidth>
        Zur Übersicht
      </Button>
      <Typography variant="h1" component="h1" sx={{ overflowWrap: 'anywhere' }}>
        {dish.name}
      </Typography>
      <StarRating
        label={`Deine Bewertung für ${dish.name}`}
        value={dish.myRating}
        busy={ratings.busy[`dish:${dish.id}`] === true}
        error={ratings.errors[`dish:${dish.id}`] ?? null}
        onChange={(stars) => rateDish(stars)}
        onClear={() => rateDish(null)}
        testId="dish-rating"
      />
      <Notice message={noticeFrom(location.state)} />
      {actionError ? <ErrorState message={actionError} /> : null}
      <DetailLine label="Beschreibung" value={dish.description} />
      <DetailLine label="Mahlzeiten" value={dish.mealTypes.map((type) => MEAL_TYPE_LABELS[type]).join(', ')} />
      <DetailLine label="Kategorien" value={dish.categories.join(', ')} />
      <DetailLine label="Aufwand" value={dish.effort ? EFFORT_LABELS[dish.effort] : null} />
      <DetailLine label="Zutaten" value={dish.ingredients.join(', ')} />
      <DetailLine label="Rezept" value={dish.recipeText} />
      <DetailLine label="Rezeptlink" value={dish.recipeUrl} />
      <DetailLine label="Rezeptquelle" value={dish.recipeSource} testId="dish-recipe-source-value" />
      <DetailLine label="Kommentar" value={dish.comments} />
      <DetailLine label="Meal Prep" value={SUITABILITY_LABELS[dish.mealPrep]} />
      <DetailLine label="Homeoffice" value={SUITABILITY_LABELS[dish.homeOffice]} />
      <Typography variant="h2" sx={{ fontSize: '1.25rem' }}>
        Varianten
      </Typography>
      {dish.variants.length === 0 ? <Typography color="text.secondary">Noch keine Varianten.</Typography> : null}
      {dish.variants.map((variant) => (
        <Stack
          key={variant.id}
          spacing={1}
          data-testid="variant-card"
          sx={{ border: 1, borderColor: 'divider', borderRadius: 2, p: 1.5, minWidth: 0 }}
        >
          <Typography sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}>{variant.name}</Typography>
          <StarRating
            label={`Deine Bewertung für ${variant.name}`}
            value={variant.myRating}
            busy={ratings.busy[`variant:${variant.id}`] === true}
            error={ratings.errors[`variant:${variant.id}`] ?? null}
            onChange={(stars) => rateVariant(variant, stars)}
            onClear={() => rateVariant(variant, null)}
            testId={`variant-rating-${variant.id}`}
          />
          <DetailLine
            label="Rezeptquelle"
            value={variant.resolved.recipeSource}
            inherited={variant.resolved.recipeSourceInherited}
            testId="resolved-recipe-source"
          />
          <DetailLine
            label="Zutaten"
            value={variant.resolved.ingredients.join(', ')}
            inherited={variant.resolved.ingredientsInherited}
          />
          <DetailLine
            label="Aufwand"
            value={variant.resolved.effort ? EFFORT_LABELS[variant.resolved.effort] : null}
            inherited={variant.resolved.effortInherited}
          />
          <DetailLine
            label="Meal Prep"
            value={SUITABILITY_LABELS[variant.resolved.mealPrep]}
            inherited={variant.resolved.mealPrepInherited}
          />
          <DetailLine
            label="Homeoffice"
            value={SUITABILITY_LABELS[variant.resolved.homeOffice]}
            inherited={variant.resolved.homeOfficeInherited}
          />
          <Button component={RouterLink} to={`/gerichte/${dish.id}/varianten/${variant.id}/bearbeiten`} variant="outlined" fullWidth>
            Variante bearbeiten
          </Button>
          <Button type="button" variant="outlined" color="error" fullWidth onClick={() => setVariantToDelete(variant)}>
            Variante löschen
          </Button>
        </Stack>
      ))}
      <Button component={RouterLink} to={`/gerichte/${dish.id}/varianten/neu`} variant="contained" fullWidth data-testid="add-variant">
        Variante anlegen
      </Button>
      <Button component={RouterLink} to={`/gerichte/${dish.id}/bearbeiten`} variant="outlined" fullWidth data-testid="edit-dish">
        Bearbeiten
      </Button>
      <Button type="button" variant="outlined" color="error" fullWidth data-testid="delete-dish" onClick={() => setDeleteOpen(true)}>
        Gericht löschen
      </Button>
      <ConfirmDialog
        open={deleteOpen}
        title="Gericht löschen?"
        message={`„${dish.name}“ und alle Varianten werden entfernt.`}
        confirmLabel="Löschen"
        confirmColor="error"
        confirmTestId="confirm-delete"
        onConfirm={() => void confirmDeleteDish()}
        onClose={() => setDeleteOpen(false)}
      />
      <ConfirmDialog
        open={variantToDelete !== null}
        title="Variante löschen?"
        message={variantToDelete ? `„${variantToDelete.name}“ wird entfernt.` : ''}
        confirmLabel="Löschen"
        confirmColor="error"
        onConfirm={() => void confirmDeleteVariant()}
        onClose={() => setVariantToDelete(null)}
      />
    </Stack>
  );
}

function DetailLine({
  label,
  value,
  inherited = false,
  testId,
}: {
  label: string;
  value: string | null;
  inherited?: boolean;
  testId?: string;
}) {
  const text = value && value.trim().length > 0 ? value : 'Keine Angabe';
  return (
    <Stack spacing={0.25}>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography data-testid={testId} sx={{ overflowWrap: 'anywhere' }}>
        {text}
        {inherited && value ? ' · vom Grundgericht' : ''}
      </Typography>
    </Stack>
  );
}
