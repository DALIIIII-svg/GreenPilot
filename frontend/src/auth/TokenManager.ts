export type TokenSet = { access_token: string; refresh_token: string; expires_in: number; token_type?: string };
const KEY = 'greenpilot.tokens';
export const tokenManager = {
  read(): TokenSet | null { try { return JSON.parse(sessionStorage.getItem(KEY) || 'null') as TokenSet | null; } catch { return null; } },
  write(value: TokenSet) { sessionStorage.setItem(KEY, JSON.stringify({ ...value, saved_at: Date.now() })); },
  clear() { sessionStorage.removeItem(KEY); },
};
