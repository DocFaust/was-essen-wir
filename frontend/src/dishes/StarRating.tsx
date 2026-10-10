import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Rating from '@mui/material/Rating';
import Typography from '@mui/material/Typography';
import { STAR_MEANINGS, starLabel } from './labels';

function statusText(busy: boolean, value: number | null): string {
  if (busy) {
    return 'Bewertung wird gespeichert …';
  }
  if (value == null) {
    return 'Nicht bewertet';
  }
  return STAR_MEANINGS[value] ?? 'Nicht bewertet';
}

export function StarRating({
  label,
  value,
  busy,
  error,
  onChange,
  onClear,
  testId,
}: {
  label: string;
  value: number | null;
  busy: boolean;
  error: string | null;
  onChange: (stars: number) => void;
  onClear: () => void;
  testId: string;
}) {
  const labelId = `${testId}-label`;
  return (
    <Box
      component="fieldset"
      data-testid={testId}
      aria-busy={busy}
      sx={{ border: 0, p: 0, m: 0, minWidth: 0, maxWidth: '100%' }}
    >
      <Typography id={labelId} component="legend" variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Rating
        name={testId}
        value={value}
        max={5}
        size="large"
        disabled={busy}
        emptyLabelText="Nicht bewertet"
        getLabelText={starLabel}
        onChange={(_event, next) => {
          if (next != null) {
            onChange(next);
          }
        }}
        slotProps={{ root: { 'aria-labelledby': labelId } }}
        sx={{
          fontSize: '2.5rem',
          maxWidth: '100%',
          '& .MuiRating-label': { padding: '4px' },
        }}
      />
      <Typography variant="body2" data-testid={`${testId}-status`} aria-live="polite">
        {statusText(busy, value)}
      </Typography>
      {value != null ? (
        <Button
          type="button"
          variant="text"
          fullWidth
          disabled={busy}
          onClick={onClear}
          aria-label={`${label} entfernen`}
          data-testid={`${testId}-clear`}
        >
          Bewertung entfernen
        </Button>
      ) : null}
      {error ? <Alert severity="error">{error}</Alert> : null}
    </Box>
  );
}
