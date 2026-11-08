import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { createPKCE } from './PKCE';
import { tokenManager, type TokenSet } from './TokenManager';
import { api, setRefreshHandler } from '../api/client';
import type { MeView } from '../api/types';

type AuthValue = { token: string | null; user: MeView | null; loading: boolean; authError: string | null; login: () => Promise<void>; logout: () => void; refresh: () => Promise<string> };
const AuthContext = createContext<AuthValue | null>(null);
const iam = import.meta.env.VITE_IAM_BASE || '/iam';
const registeredRedirectUri = import.meta.env.VITE_OAUTH_REDIRECT_URI || 'http://localhost:5500/callback.html';
let refreshFlight: Promise<string> | null = null;
async function tokenRequest(params: Record<string, string>): Promise<TokenSet> {
  const body = new URLSearchParams({ client_id: import.meta.env.VITE_OAUTH_CLIENT_ID || 'greenpilot-pwa', ...params });
  const response = await fetch(`${iam}/token`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body });
  const json = await response.json();
  if (!response.ok) throw new Error(json.error_description || json.error || `Token request failed (${response.status})`);
  tokenManager.write(json as TokenSet);
  return json as TokenSet;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState(() => tokenManager.read()?.access_token ?? null);
  const [user, setUser] = useState<MeView | null>(null);
  const [loading, setLoading] = useState(true);
  const [authError, setAuthError] = useState<string | null>(null);
  const refresh = useCallback(async () => {
    if (!refreshFlight) {
      refreshFlight = (async () => {
        const saved = tokenManager.read();
        if (!saved?.refresh_token) throw new Error('Session expired');
        const next = await tokenRequest({ grant_type: 'refresh_token', refresh_token: saved.refresh_token });
        setToken(next.access_token); return next.access_token;
      })().finally(() => { refreshFlight = null; });
    }
    return refreshFlight;
  }, []);
  useEffect(() => { setRefreshHandler(refresh); }, [refresh]);
  const login = useCallback(async () => {
    setAuthError(null);
    let redirectOrigin: string;
    try { redirectOrigin = new URL(registeredRedirectUri).origin; }
    catch { setAuthError('La configuration VITE_OAUTH_REDIRECT_URI est invalide.'); return; }
    if (redirectOrigin !== location.origin) {
      setAuthError(`L’IAM autorise le retour sur ${registeredRedirectUri}, mais GreenPilot est ouvert sur ${location.origin}. Arrête le serveur qui occupe le port 5500, puis relance le frontend sur ce port.`);
      return;
    }
    const pkce = await createPKCE();
    sessionStorage.setItem('greenpilot.pkce', JSON.stringify({ verifier: pkce.verifier, state: pkce.state }));
    location.assign(`${iam}/authorize?${new URLSearchParams({ client_id: import.meta.env.VITE_OAUTH_CLIENT_ID || 'greenpilot-pwa', redirect_uri: registeredRedirectUri, response_type: 'code', scope: import.meta.env.VITE_OAUTH_SCOPES || 'greenhouse:read greenhouse:control incidents:manage admin', state: pkce.state, code_challenge: pkce.challenge, code_challenge_method: 'S256' })}`);
  }, []);
  const logout = useCallback(() => { tokenManager.clear(); sessionStorage.removeItem('greenpilot.pkce'); setToken(null); setUser(null); setAuthError(null); location.assign('/'); }, []);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
          const params = new URLSearchParams(location.search);
        if (params.has('code') || params.has('error')) {
          const raw = sessionStorage.getItem('greenpilot.pkce'); sessionStorage.removeItem('greenpilot.pkce');
          const saved = raw ? JSON.parse(raw) as { verifier: string; state: string } : null;
          history.replaceState({}, '', location.pathname);
          if (params.has('error')) throw new Error(params.get('error_description') || params.get('error') || 'OAuth sign-in failed');
          if (!saved || params.get('state') !== saved.state) throw new Error('Sign-in state could not be verified. Please try again.');
          const result = await tokenRequest({ grant_type: 'authorization_code', code: params.get('code') || '', redirect_uri: registeredRedirectUri, code_verifier: saved.verifier });
          if (active) setToken(result.access_token);
        }
        let current = tokenManager.read();
        if (current && Date.now() > Number((current as TokenSet & { saved_at?: number }).saved_at || 0) + (current.expires_in - 45) * 1000) { await refresh(); current = tokenManager.read(); }
        if (current?.access_token && active) {
          const me = await api.get<MeView>('/users/me');
          if (active) { setUser(me.data); setToken(current.access_token); }
        }
      } catch (error) {
        tokenManager.clear(); if (active) { setToken(null); setUser(null); setAuthError(error instanceof Error ? error.message : 'La connexion a échoué.'); console.error(error); }
      } finally { if (active) setLoading(false); }
    })();
    return () => { active = false; };
  }, [refresh]);

  useEffect(() => {
    if (!token) return;
    const saved = tokenManager.read(); if (!saved) return;
    const timer = window.setTimeout(() => { void refresh().catch(logout); }, Math.max(1000, (saved.expires_in - 60) * 1000));
    return () => clearTimeout(timer);
  }, [token, refresh, logout]);

  const value = useMemo(() => ({ token, user, loading, authError, login, logout, refresh }), [token, user, loading, authError, login, logout, refresh]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
export function useAuth() { const value = useContext(AuthContext); if (!value) throw new Error('useAuth must be used within AuthProvider'); return value; }

