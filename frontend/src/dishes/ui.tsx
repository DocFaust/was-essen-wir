import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState } from 'react';

export function LoadingState({ label }: { label: string }) {
  return (
    <Stack direction="row" spacing={1.5} role="status" sx={{ alignItems: 'center' }}>
      <CircularProgress size={22} aria-hidden="true" />
      <Typography>{label}</Typography>
    </Stack>
  );
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <Alert severity="error">
      <Stack spacing={1.5}>
        <span>{message}</span>
        {onRetry ? (
          <Button type="button" variant="outlined" color="inherit" fullWidth onClick={onRetry}>
            Erneut versuchen
          </Button>
        ) : null}
      </Stack>
    </Alert>
  );
}

export function Notice({ message }: { message: string | null }) {
  if (!message) {
    return null;
  }
  return (
    <Alert severity="success" role="status">
      {message}
    </Alert>
  );
}

export function ChoiceButtons<T extends string>({
  label,
  options,
  selected,
  onChange,
}: {
  label: string;
  options: { value: T; label: string }[];
  selected: T | T[];
  onChange: (value: T) => void;
}) {
  const values = Array.isArray(selected) ? selected : [selected];
  return (
    <Box component="fieldset" sx={{ border: 0, p: 0, m: 0, minWidth: 0 }}>
      <Typography component="legend" variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        {label}
      </Typography>
      <Stack spacing={1}>
        {options.map((option) => {
          const active = values.includes(option.value);
          return (
            <Button
              key={option.value}
              type="button"
              fullWidth
              variant={active ? 'contained' : 'outlined'}
              aria-pressed={active}
              onClick={() => onChange(option.value)}
              sx={{ justifyContent: 'flex-start', textAlign: 'left', whiteSpace: 'normal' }}
            >
              {option.label}
            </Button>
          );
        })}
      </Stack>
    </Box>
  );
}

export function NameListEditor({
  label,
  values,
  onChange,
  addLabel,
  inputTestId,
}: {
  label: string;
  values: string[];
  onChange: (values: string[]) => void;
  addLabel: string;
  inputTestId?: string;
}) {
  const [draft, setDraft] = useState('');

  function add() {
    const name = draft.trim();
    if (!name) {
      return;
    }
    onChange([...values, name]);
    setDraft('');
  }

  return (
    <Stack spacing={1}>
      <Typography component="h2" variant="subtitle1">
        {label}
      </Typography>
      {values.map((value, index) => (
        <Stack key={`${value}-${index}`} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <Typography sx={{ flex: 1, minWidth: 0, overflowWrap: 'anywhere' }}>{value}</Typography>
          <Button
            type="button"
            variant="outlined"
            aria-label={`${value} entfernen`}
            onClick={() => onChange(values.filter((_, itemIndex) => itemIndex !== index))}
          >
            Entfernen
          </Button>
        </Stack>
      ))}
      <TextField
        label={`${label} ergänzen`}
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            event.preventDefault();
            add();
          }
        }}
        fullWidth
        slotProps={inputTestId ? { htmlInput: { 'data-testid': inputTestId } } : undefined}
      />
      <Button type="button" variant="outlined" fullWidth onClick={add}>
        {addLabel}
      </Button>
    </Stack>
  );
}

export function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel,
  confirmColor = 'primary',
  confirmTestId,
  onConfirm,
  onClose,
}: {
  open: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  confirmColor?: 'primary' | 'error';
  confirmTestId?: string;
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Dialog open={open} onClose={onClose} fullWidth aria-labelledby="confirm-dialog-title">
      <DialogTitle id="confirm-dialog-title">{title}</DialogTitle>
      <DialogContent>
        <DialogContentText>{message}</DialogContentText>
      </DialogContent>
      <DialogActions sx={{ flexDirection: 'column', alignItems: 'stretch', gap: 1, px: 3, pb: 2 }}>
        <Button
          type="button"
          variant="contained"
          color={confirmColor}
          fullWidth
          onClick={onConfirm}
          data-testid={confirmTestId}
        >
          {confirmLabel}
        </Button>
        <Button type="button" variant="outlined" fullWidth onClick={onClose}>
          Abbrechen
        </Button>
      </DialogActions>
    </Dialog>
  );
}
