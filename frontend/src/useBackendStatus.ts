import { useEffect, useState } from 'react';

export type BackendStatusState =
  | { phase: 'loading' }
  | { phase: 'up'; application: string }
  | { phase: 'down'; message: string };

const UNREACHABLE =
  'Das Backend ist gerade nicht erreichbar. Prüfe, ob es gestartet ist, und versuche es erneut.';

export function useBackendStatus(): {
  state: BackendStatusState;
  retry: () => void;
} {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<BackendStatusState>({ phase: 'loading' });

  useEffect(() => {
    const controller = new AbortController();
    setState({ phase: 'loading' });

    fetch('/api/status', {
      signal: controller.signal,
      headers: { Accept: 'application/json' },
    })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`);
        }
        const body: unknown = await response.json();
        if (!isUpStatus(body)) {
          throw new Error('unexpected status payload');
        }
        setState({ phase: 'up', application: body.application });
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') {
          return;
        }
        setState({ phase: 'down', message: UNREACHABLE });
      });

    return () => controller.abort();
  }, [attempt]);

  return {
    state,
    retry: () => setAttempt((current) => current + 1),
  };
}

function isUpStatus(body: unknown): body is { status: 'UP'; application: string } {
  if (typeof body !== 'object' || body === null) {
    return false;
  }
  const record = body as Record<string, unknown>;
  return record.status === 'UP' && typeof record.application === 'string' && record.application.length > 0;
}
