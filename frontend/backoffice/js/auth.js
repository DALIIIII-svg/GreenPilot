import { config } from './config.js';

const TOKENS = 'gp.tokens';
const PKCE = 'gp.pkce';
let refreshing = null;

const base64url = (bytes) =>
  btoa(String.fromCharCode(...new Uint8Array(bytes))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

function readTokens() {
  try {
    return JSON.parse(localStorage.getItem(TOKENS));
  } catch {
    return null;
  }
}

function saveTokens(answer) {
  const tokens = {
    access: answer.access_token,
    refresh: answer.refresh_token,
    expiresAt: Date.now() + answer.expires_in * 1000,
  };
  localStorage.setItem(TOKENS, JSON.stringify(tokens));
  return tokens;
}

export function clearSession() {
  localStorage.removeItem(TOKENS);
}

export function hasSession() {
  return Boolean(readTokens()?.refresh);
}

async function tokenRequest(params) {
  const res = await fetch(`${config.iam}/token`, {
    method: 'POST',
    body: new URLSearchParams({ client_id: config.clientId, ...params }),
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    const error = new Error(body.error_description || body.error || `Sign-in failed (${res.status})`);
    error.status = res.status;
    throw error;
  }
  return saveTokens(body);
}

export async function login() {
  const verifier = base64url(crypto.getRandomValues(new Uint8Array(32)));
  const challenge = base64url(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier)));
  const state = crypto.randomUUID();
  sessionStorage.setItem(PKCE, JSON.stringify({ verifier, state }));
  const params = new URLSearchParams({
    client_id: config.clientId,
    redirect_uri: config.redirectUri,
    response_type: 'code',
    scope: config.scope,
    state,
    code_challenge: challenge,
    code_challenge_method: 'S256',
  });
  location.assign(`${config.iam}/authorize?${params}`);
}

export async function completeLogin(search) {
  const answer = new URLSearchParams(search);
  const saved = JSON.parse(sessionStorage.getItem(PKCE) || 'null');
  sessionStorage.removeItem(PKCE);
  if (answer.get('error')) throw new Error(answer.get('error_description') || answer.get('error'));
  if (!saved || answer.get('state') !== saved.state) throw new Error('Sign-in could not be verified. Please try again.');
  await tokenRequest({
    grant_type: 'authorization_code',
    code: answer.get('code'),
    redirect_uri: config.redirectUri,
    code_verifier: saved.verifier,
  });
}

// Refresh tokens are single-use, so concurrent callers must share one request.
export function refresh() {
  if (!refreshing) {
    refreshing = (async () => {
      const tokens = readTokens();
      if (!tokens?.refresh) throw new Error('Session expired');
      try {
        return (await tokenRequest({ grant_type: 'refresh_token', refresh_token: tokens.refresh })).access;
      } catch (error) {
        if (error.status) clearSession();
        throw error;
      }
    })().finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

export async function accessToken() {
  const tokens = readTokens();
  if (!tokens) return null;
  if (Date.now() > tokens.expiresAt - 30_000) return refresh();
  return tokens.access;
}

export function logout() {
  clearSession();
  location.assign('./');
}
