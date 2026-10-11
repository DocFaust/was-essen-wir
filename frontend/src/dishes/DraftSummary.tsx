import Alert from '@mui/material/Alert';
import Stack from '@mui/material/Stack';
import Button from '@mui/material/Button';
import { Link as RouterLink } from 'react-router';
import { DRAFT_FIELD_LABELS, type DishDraft } from './draft';

export function DraftSummary({ draft, edited }: { draft: DishDraft; edited: string[] }) {
  const recognized = draft.recognized.filter((field) => !edited.includes(field)).map(label);
  const unknown = draft.unknown.filter((field) => !edited.includes(field)).map(label);
  const changed = edited.map(label);
  return (
    <Stack spacing={1} data-testid="draft-summary">
      {draft.warnings.map((warning) => (
        <Alert key={warning} severity="info">
          {warning}
        </Alert>
      ))}
      <Alert severity="success">
        <Stack spacing={0.5}>
          {recognized.length > 0 ? <span>Aus der Beschreibung: {recognized.join(', ')}.</span> : null}
          {changed.length > 0 ? <span>Von dir ergänzt: {changed.join(', ')}.</span> : null}
          {unknown.length > 0 ? <span>Noch nicht angegeben: {unknown.join(', ')}.</span> : null}
        </Stack>
      </Alert>
      {draft.duplicates.map((duplicate) => (
        <Alert key={`${duplicate.dishId}-${duplicate.variantId ?? 'dish'}`} severity="warning" data-testid="duplicate-hint">
          <Stack spacing={1}>
            <span>{duplicate.message}</span>
            <Button component={RouterLink} to={`/gerichte/${duplicate.dishId}`} variant="outlined" color="inherit" fullWidth>
              Vorhandenes öffnen
            </Button>
            <Button
              component={RouterLink}
              to={`/gerichte/${duplicate.dishId}/varianten/neu`}
              variant="outlined"
              color="inherit"
              fullWidth
            >
              Variante anlegen
            </Button>
            <span>Du kannst das neue Gericht unten trotzdem speichern.</span>
          </Stack>
        </Alert>
      ))}
    </Stack>
  );
}

function label(field: string): string {
  return DRAFT_FIELD_LABELS[field] ?? field;
}
