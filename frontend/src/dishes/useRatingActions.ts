import { useRef, useState } from 'react';
import { ApiError } from '../http';

export function useRatingActions() {
  const inFlight = useRef(new Set<string>());
  const [busy, setBusy] = useState<Record<string, boolean>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});

  async function commit(
    key: string,
    next: number | null,
    previous: number | null,
    persist: () => Promise<void>,
    publish: (stars: number | null) => void,
  ): Promise<void> {
    if (inFlight.current.has(key)) {
      return;
    }
    inFlight.current.add(key);
    publish(next);
    setBusy((current) => ({ ...current, [key]: true }));
    setErrors((current) => {
      if (!(key in current)) {
        return current;
      }
      const rest = { ...current };
      delete rest[key];
      return rest;
    });
    try {
      await persist();
    } catch (caught) {
      publish(previous);
      const message = caught instanceof ApiError ? caught.message : 'Die Bewertung konnte nicht gespeichert werden.';
      setErrors((current) => ({ ...current, [key]: message }));
    } finally {
      inFlight.current.delete(key);
      setBusy((current) => ({ ...current, [key]: false }));
    }
  }

  return { busy, errors, commit };
}
