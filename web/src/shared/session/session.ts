import { useSyncExternalStore } from "react";

export type Session = { userId: string; token: string };

const STORAGE_KEY = "frontrow.session";
const listeners = new Set<() => void>();
let current = read();

function read(): Session | null {
  const raw = sessionStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Session;
    if (!parsed.token || !parsed.userId) return null;
    return parsed;
  } catch {
    return null;
  }
}

function publish(next: Session | null) {
  current = next;
  if (next) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  else sessionStorage.removeItem(STORAGE_KEY);
  listeners.forEach((listener) => listener());
}

export const sessionStore = {
  get: () => current,
  signIn(userId: string, token: string) {
    publish({ userId, token });
  },
  signOut() {
    publish(null);
  },
  subscribe(listener: () => void) {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};

export function useSession() {
  return useSyncExternalStore(sessionStore.subscribe, sessionStore.get, () => null);
}
