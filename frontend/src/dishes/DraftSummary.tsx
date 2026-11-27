import Alert from '@mui/material/Alert';
import Stack from '@mui/material/Stack';
import Button from '@mui/material/Button';
import { Link as RouterLink } from 'react-router';
import type { DishDraft } from './draft';

export function DraftSummary({ draft }: { draft: DishDraft }) {
  return (
    <Stack spacing={1} data-testid="draft-summary">
      {draft.warnings.map((warning) => (
        <Alert key={warning} severity="info">
          {warning}
        </Alert>
      ))}
      <Alert severity="success">Die KI hat einen Gerichtsentwurf erstellt. Bitte prüfe die Angaben vor dem Speichern.</Alert>
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
