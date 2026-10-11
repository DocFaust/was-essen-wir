import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router';
import { EFFORT_LABELS } from '../dishes/labels';
import type { Suggestion } from './types';

export function SuggestionCard({
  suggestion,
  disabled,
  onExclude,
  onPlan,
  onEaten,
}: {
  suggestion: Suggestion;
  disabled: boolean;
  onExclude: () => void;
  onPlan: () => void;
  onEaten: () => void;
}) {
  const traits = traitsOf(suggestion);
  return (
    <Paper variant="outlined" data-testid="suggestion-card" sx={{ p: 2, minWidth: 0 }}>
      <Stack spacing={1.5}>
        <Typography
          variant="h2"
          data-testid="suggestion-name"
          sx={{ fontSize: '1.25rem', fontWeight: 700, overflowWrap: 'anywhere' }}
        >
          {suggestion.name}
        </Typography>
        <Typography data-testid="suggestion-reason">{suggestion.reason}</Typography>
        {traits.length > 0 ? (
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
            {traits.map((trait) => (
              <Chip key={trait} label={trait} size="small" />
            ))}
          </Stack>
        ) : null}
        <Button component={RouterLink} to={`/gerichte/${suggestion.dishId}`} variant="outlined" fullWidth data-testid="open-suggestion">
          Gericht öffnen
        </Button>
        <Button type="button" variant="contained" fullWidth data-testid="suggestion-plan" onClick={onPlan}>
          Einplanen
        </Button>
        <Button type="button" variant="outlined" fullWidth data-testid="suggestion-eaten" onClick={onEaten}>
          Als gegessen eintragen
        </Button>
        <Button type="button" variant="text" fullWidth disabled={disabled} data-testid="exclude-suggestion" onClick={onExclude}>
          Nicht dieses Gericht
        </Button>
      </Stack>
    </Paper>
  );
}

function traitsOf(suggestion: Suggestion): string[] {
  const traits: string[] = [];
  if (suggestion.effort) {
    traits.push(`Aufwand: ${EFFORT_LABELS[suggestion.effort]}`);
  }
  if (suggestion.mealPrep === 'SUITABLE') {
    traits.push('Meal Prep');
  } else if (suggestion.mealPrep === 'UNSUITABLE') {
    traits.push('Nicht für Meal Prep');
  }
  if (suggestion.homeOffice === 'SUITABLE') {
    traits.push('Homeoffice');
  } else if (suggestion.homeOffice === 'UNSUITABLE') {
    traits.push('Nicht fürs Homeoffice');
  }
  return traits;
}
