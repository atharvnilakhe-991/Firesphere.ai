import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import type { Session, SessionUser } from '@firesphere/api-client/types';
import { secureStorage } from '../storage/secureStorage';
import { registerUnauthorizedHandler } from './sessionEvents';
import { createDemoSession, restoreDemoSession } from '../dev/mockServices';
import { unavailableAuthAdapter } from './authAdapter';

interface AuthState { user: SessionUser | null; accessToken: string | null; loading: boolean; error: string | null; login(email: string, password: string): Promise<void>; demoLogin(role: 'CITIZEN' | 'OFFICER'): Promise<void>; logout(): Promise<void>; }
const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: React.PropsWithChildren) {
  const [user, setUser] = useState<SessionUser | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => registerUnauthorizedHandler(() => { void secureStorage.clear(); setAccessToken(null); setUser(null); setError('Your session has expired. Sign in again.'); }), []);

  useEffect(() => {
    let active = true;
    void (async () => {
      try {
        const token = await secureStorage.getAccessToken();
        if (!token) return;
        const demoUser = __DEV__ ? restoreDemoSession(token) : null;
        if (demoUser) { if (active) { setAccessToken(token); setUser(demoUser); } return; }
        if (active) { setError('A saved session cannot be restored until the approved organization authentication adapter is configured.'); }
      } catch (reason) {
        // Preserve a token when secure storage itself is temporarily unavailable.
        if (active) setError(reason instanceof Error ? reason.message : 'Session could not be restored.');
      } finally { if (active) setLoading(false); }
    })();
    return () => { active = false; };
  }, []);

  async function login(email: string, password: string) {
    setError(null);
    const session = await unavailableAuthAdapter.login({ email, password });
    await secureStorage.saveTokens(session.accessToken, session.refreshToken);
    setAccessToken(session.accessToken); setUser(session.user);
  }
  async function demoLogin(role: 'CITIZEN' | 'OFFICER') {
    if (!__DEV__) throw new Error('Development sign-in is unavailable in release builds.');
    const session = createDemoSession(role, role === 'OFFICER' ? 'officer@demo.local' : 'citizen@demo.local');
    await secureStorage.saveTokens(session.accessToken);
    setAccessToken(session.accessToken); setUser(session.user); setError(null);
  }
  async function logout() {
    try { if (accessToken && !accessToken.startsWith('local-demo:')) await unavailableAuthAdapter.logout(); } finally { await secureStorage.clear(); setAccessToken(null); setUser(null); }
  }
  const state = useMemo(() => ({ user, accessToken, loading, error, login, demoLogin, logout }), [user, accessToken, loading, error]);
  return <AuthContext.Provider value={state}>{children}</AuthContext.Provider>;
}
export function useAuth() { const value = useContext(AuthContext); if (!value) throw new Error('useAuth must be used inside AuthProvider.'); return value; }
