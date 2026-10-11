import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useRef, useState } from 'react';
import { ApiError, createDishDraft } from './api';
import type { DishDraft, DraftKind } from './draft';
import { ErrorState, LoadingState } from './ui';

export function CapturePanel({
  kind,
  baseDishId,
  onDraft,
  onManual,
}: {
  kind: DraftKind;
  baseDishId?: string;
  onDraft: (draft: DishDraft) => void;
  onManual: () => void;
}) {
  const [description, setDescription] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [candidates, setCandidates] = useState<DishDraft['candidates']>([]);
  const submitting = useRef(false);

  async function submit(selectedBaseDishId?: string) {
    const text = description.trim();
    if (!text || submitting.current) {
      return;
    }
    submitting.current = true;
    setLoading(true);
    setError(null);
    try {
      const draft = await createDishDraft({
        description: text,
        kind,
        baseDishId: selectedBaseDishId ?? baseDishId ?? null,
      });
      if (draft.outcome === 'NEEDS_BASE_DISH') {
        setCandidates(draft.candidates);
        return;
      }
      setCandidates([]);
      onDraft(draft);
    } catch (caught) {
      setCandidates([]);
      setError(caught instanceof ApiError ? caught.message : 'Der Entwurf konnte nicht erstellt werden.');
    } finally {
      submitting.current = false;
      setLoading(false);
    }
  }

  return (
    <Stack spacing={2} data-testid="dish-capture">
      <TextField
        label={kind === 'VARIANT' ? 'Beschreibe die Variante' : 'Beschreibe dein Gericht'}
        value={description}
        onChange={(event) => setDescription(event.target.value)}
        placeholder="Wir machen oft Kartoffelgratin. Dauert etwa eine Stunde und ist gut zum Vorbereiten."
        fullWidth
        multiline
        minRows={5}
        disabled={loading}
        slotProps={{ htmlInput: { 'data-testid': 'draft-description' } }}
      />
      {loading ? <LoadingState label="Entwurf wird erstellt …" /> : null}
      {error ? (
        <Stack spacing={1}>
          <ErrorState message={error} onRetry={() => void submit()} />
          <Button type="button" variant="outlined" fullWidth onClick={onManual} data-testid="draft-manual">
            Manuell erfassen
          </Button>
        </Stack>
      ) : null}
      {candidates.length > 0 ? (
        <Alert severity="info">
          <Stack spacing={1}>
            <span>Mehrere Gerichte passen. Bitte wähle das Grundgericht.</span>
            {candidates.map((candidate) => (
              <Button
                key={candidate.id}
                type="button"
                variant="outlined"
                fullWidth
                onClick={() => void submit(candidate.id)}
              >
                {candidate.name}
              </Button>
            ))}
          </Stack>
        </Alert>
      ) : null}
      <Button
        type="button"
        variant="contained"
        fullWidth
        disabled={loading || description.trim().length === 0}
        onClick={() => void submit()}
        data-testid="draft-submit"
      >
        Entwurf erstellen
      </Button>
    </Stack>
  );
}
