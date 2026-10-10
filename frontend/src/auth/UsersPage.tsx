import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useState } from 'react';
import { ChoiceButtons, ErrorState, LoadingState } from '../dishes/ui';
import { ApiError } from '../http';
import { createUser, listUsers, resetPassword, updateUser } from './api';
import { useAuth } from './AuthProvider';
import type { CurrentUser, Role } from './types';

const ROLE_OPTIONS: { value: Role; label: string }[] = [
  { value: 'MEMBER', label: 'Familienmitglied' },
  { value: 'ADMIN', label: 'Administration' },
];

export function UsersPage() {
  const { user } = useAuth();
  const [people, setPeople] = useState<CurrentUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<CurrentUser | null>(null);
  const [resetTarget, setResetTarget] = useState<CurrentUser | null>(null);

  async function reload() {
    setLoading(true);
    setError(null);
    try {
      setPeople(await listUsers());
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Die Familie konnte nicht geladen werden.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (user?.role !== 'ADMIN') {
      setLoading(false);
      return;
    }
    let active = true;
    listUsers()
      .then((result) => {
        if (active) {
          setPeople(result);
          setLoading(false);
        }
      })
      .catch((caught: unknown) => {
        if (active) {
          setError(caught instanceof ApiError ? caught.message : 'Die Familie konnte nicht geladen werden.');
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
  }, [user?.role]);

  if (user?.role !== 'ADMIN') {
    return <Alert severity="info">Diese Seite ist nur für die Administration.</Alert>;
  }

  async function toggleActive(person: CurrentUser) {
    setError(null);
    setNotice(null);
    try {
      const updated = await updateUser(person.id, {
        displayName: person.displayName,
        role: person.role,
        active: !person.active,
      });
      setPeople((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      setNotice(updated.active ? `${updated.displayName} ist wieder aktiv.` : `${updated.displayName} ist deaktiviert.`);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Die Änderung ist fehlgeschlagen.');
    }
  }

  return (
    <Stack spacing={2}>
      <header>
        <Typography variant="h1" component="h1">
          Familie
        </Typography>
        <Typography color="text.secondary" sx={{ mt: 1 }}>
          Konten für diese Installation. Eine öffentliche Registrierung gibt es nicht.
        </Typography>
      </header>
      {notice ? <Alert severity="success">{notice}</Alert> : null}
      {error ? <Alert severity="error">{error}</Alert> : null}
      {loading ? <LoadingState label="Familie wird geladen …" /> : null}
      {!loading && !error && people.length === 0 ? <ErrorState message="Noch keine Konten." onRetry={() => void reload()} /> : null}
      {people.map((person) => (
        <Paper key={person.id} sx={{ p: 2 }}>
          <Stack spacing={1.5}>
            <Typography sx={{ overflowWrap: 'anywhere', fontWeight: 700 }}>{person.displayName}</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
              {person.username} · {person.role === 'ADMIN' ? 'Administration' : 'Familienmitglied'} ·{' '}
              {person.active ? 'Aktiv' : 'Deaktiviert'}
              {person.passwordChangeRequired ? ' · Passwortänderung offen' : ''}
            </Typography>
            <Button type="button" variant="outlined" fullWidth onClick={() => setEditTarget(person)}>
              Bearbeiten
            </Button>
            <Button type="button" variant="outlined" fullWidth onClick={() => void toggleActive(person)}>
              {person.active ? 'Deaktivieren' : 'Aktivieren'}
            </Button>
            <Button type="button" variant="outlined" fullWidth onClick={() => setResetTarget(person)}>
              Passwort zurücksetzen
            </Button>
          </Stack>
        </Paper>
      ))}
      <Button type="button" variant="contained" fullWidth data-testid="new-member" onClick={() => setCreateOpen(true)}>
        Familienmitglied anlegen
      </Button>
      <CreateDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={(created) => {
          setPeople((current) => [...current, created].sort((a, b) => a.displayName.localeCompare(b.displayName, 'de')));
          setNotice(`${created.displayName} wurde angelegt. Das Passwort muss bei der nächsten Anmeldung geändert werden.`);
          setCreateOpen(false);
        }}
      />
      <EditDialog
        person={editTarget}
        onClose={() => setEditTarget(null)}
        onSaved={(updated) => {
          setPeople((current) => current.map((item) => (item.id === updated.id ? updated : item)));
          setNotice('Die Angaben wurden gespeichert.');
          setEditTarget(null);
        }}
      />
      <ResetDialog
        person={resetTarget}
        onClose={() => setResetTarget(null)}
        onSaved={(updated) => {
          setPeople((current) => current.map((item) => (item.id === updated.id ? updated : item)));
          setNotice(`Für ${updated.displayName} liegt ein temporäres Passwort. Es muss bei der nächsten Anmeldung geändert werden.`);
          setResetTarget(null);
        }}
      />
    </Stack>
  );
}

function CreateDialog({
  open,
  onClose,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  onCreated: (user: CurrentUser) => void;
}) {
  const [username, setUsername] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<Role>('MEMBER');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function submit() {
    setSaving(true);
    setError(null);
    try {
      const created = await createUser({ username, displayName, password, role });
      setUsername('');
      setDisplayName('');
      setPassword('');
      setRole('MEMBER');
      onCreated(created);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Das Konto konnte nicht angelegt werden.');
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog open={open} onClose={onClose} fullWidth aria-labelledby="create-user-title">
      <DialogTitle id="create-user-title">Familienmitglied anlegen</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {error ? <Alert severity="error">{error}</Alert> : null}
          <TextField
            label="Benutzername"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            required
            fullWidth
            slotProps={{ htmlInput: { 'data-testid': 'member-username' } }}
          />
          <TextField
            label="Anzeigename"
            value={displayName}
            onChange={(event) => setDisplayName(event.target.value)}
            required
            fullWidth
            slotProps={{ htmlInput: { 'data-testid': 'member-display-name' } }}
          />
          <TextField
            label="Temporäres Passwort"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
            fullWidth
            helperText="Mindestens 12 Zeichen. Wird bei der nächsten Anmeldung geändert."
            slotProps={{ htmlInput: { 'data-testid': 'member-password' } }}
          />
          <ChoiceButtons label="Rolle" options={ROLE_OPTIONS} selected={role} onChange={setRole} />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ flexDirection: 'column', alignItems: 'stretch', gap: 1, px: 3, pb: 2 }}>
        <Button type="button" variant="contained" fullWidth disabled={saving} data-testid="member-save" onClick={() => void submit()}>
          Anlegen
        </Button>
        <Button type="button" variant="outlined" fullWidth onClick={onClose}>
          Abbrechen
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function EditDialog({
  person,
  onClose,
  onSaved,
}: {
  person: CurrentUser | null;
  onClose: () => void;
  onSaved: (user: CurrentUser) => void;
}) {
  const [displayName, setDisplayName] = useState(person?.displayName ?? '');
  const [role, setRole] = useState<Role>(person?.role ?? 'MEMBER');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setDisplayName(person?.displayName ?? '');
    setRole(person?.role ?? 'MEMBER');
    setError(null);
  }, [person]);

  async function submit() {
    if (!person) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      onSaved(await updateUser(person.id, { displayName, role, active: person.active }));
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Die Angaben konnten nicht gespeichert werden.');
      setSaving(false);
    }
  }

  return (
    <Dialog open={person !== null} onClose={onClose} fullWidth aria-labelledby="edit-user-title">
      <DialogTitle id="edit-user-title">Person bearbeiten</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {error ? <Alert severity="error">{error}</Alert> : null}
          <TextField label="Anzeigename" value={displayName} onChange={(event) => setDisplayName(event.target.value)} required fullWidth />
          <ChoiceButtons label="Rolle" options={ROLE_OPTIONS} selected={role} onChange={setRole} />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ flexDirection: 'column', alignItems: 'stretch', gap: 1, px: 3, pb: 2 }}>
        <Button type="button" variant="contained" fullWidth disabled={saving} onClick={() => void submit()}>
          Speichern
        </Button>
        <Button type="button" variant="outlined" fullWidth onClick={onClose}>
          Abbrechen
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function ResetDialog({
  person,
  onClose,
  onSaved,
}: {
  person: CurrentUser | null;
  onClose: () => void;
  onSaved: (user: CurrentUser) => void;
}) {
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setPassword('');
    setError(null);
  }, [person]);

  async function submit() {
    if (!person) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      onSaved(await resetPassword(person.id, password));
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'Das Passwort konnte nicht gesetzt werden.');
      setSaving(false);
    }
  }

  return (
    <Dialog open={person !== null} onClose={onClose} fullWidth aria-labelledby="reset-password-title">
      <DialogTitle id="reset-password-title">Passwort zurücksetzen</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Typography>
            Setze ein temporäres Passwort für {person?.displayName}. Es gibt keine E-Mail. Die Person muss es bei der nächsten Anmeldung ändern.
          </Typography>
          {error ? <Alert severity="error">{error}</Alert> : null}
          <TextField
            label="Temporäres Passwort"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
            fullWidth
            slotProps={{ htmlInput: { 'data-testid': 'reset-password' } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ flexDirection: 'column', alignItems: 'stretch', gap: 1, px: 3, pb: 2 }}>
        <Button type="button" variant="contained" fullWidth disabled={saving} data-testid="reset-password-save" onClick={() => void submit()}>
          Passwort setzen
        </Button>
        <Button type="button" variant="outlined" fullWidth onClick={onClose}>
          Abbrechen
        </Button>
      </DialogActions>
    </Dialog>
  );
}
