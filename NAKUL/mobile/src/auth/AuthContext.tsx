import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import type { Session, SessionUser } from '@firesphere/api-client/types';
import { api } from '../api/client';
import { secureStorage } from '../storage/secureStorage';
import { registerUnauthorizedHandler } from './sessionEvents';
import { createDemoSession, restoreDemoSession } from '../dev/mockServices';
import { createConfiguredAuthAdapter } from './authAdapter';

interface AuthState { user: SessionUser | null; accessToken: string | null; loading: boolean; error: string | null; login(email: string, password: string): Promise<void>; demoLogin(role: 'CITIZEN' | 'OFFICER'): Promise<void>; logout(): Promise<void>; }
const AuthContext = createContext<AuthState | null>(null);
const configuredAuthAdapter = createConfiguredAuthAdapter(api);

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
        const restoredUser = await configuredAuthAdapter.restore();
        if (active && restoredUser) { setAccessToken(token); setUser(restoredUser); }
        else if (active) { await secureStorage.clear(); setError('Your saved session is no longer valid. Sign in again.'); }
      } catch (reason) {
        // Preserve a token when secure storage itself is temporarily unavailable.
        if (active) setError(reason instanceof Error ? reason.message : 'Session could not be restored.');
      } finally { if (active) setLoading(false); }
    })();
    return () => { active = false; };
  }, []);

  async function login(email: string, password: string) {
    setError(null);
    try {
      const session = await configuredAuthAdapter.login({ email, password });
      await secureStorage.saveTokens(session.accessToken, session.refreshToken);
      setAccessToken(session.accessToken); setUser(session.user);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Sign-in could not be completed.');
      throw reason;
    }
  }
  async function demoLogin(role: 'CITIZEN' | 'OFFICER') {
    if (!__DEV__) throw new Error('Development sign-in is unavailable in release builds.');
    const session = createDemoSession(role, role === 'OFFICER' ? 'officer@demo.local' : 'citizen@demo.local');
    await secureStorage.saveTokens(session.accessToken);
    setAccessToken(session.accessToken); setUser(session.user); setError(null);
  }
  async function logout() {
    try { if (accessToken && !accessToken.startsWith('local-demo:')) await configuredAuthAdapter.logout(); }
    catch { /* Local sign-out must still complete when the service is unavailable. */ }
    finally { await secureStorage.clear(); setAccessToken(null); setUser(null); }
  }
  const state = useMemo(() => ({ user, accessToken, loading, error, login, demoLogin, logout }), [user, accessToken, loading, error]);
  return <AuthContext.Provider value={state}>{children}</AuthContext.Provider>;
}
export function useAuth() { const value = useContext(AuthContext); if (!value) throw new Error('useAuth must be used inside AuthProvider.'); return value; }
