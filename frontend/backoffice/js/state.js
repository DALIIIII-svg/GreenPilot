import { api } from './api.js';

export const state = {
  me: null,
  sites: [],
  siteId: null,
};

export const hasScope = (scope) => Boolean(state.me?.scopes?.includes(scope));
export const isAdmin = () => Boolean(state.me?.roles?.includes('ADMIN')) && hasScope('admin');
export const currentSite = () => state.sites.find((s) => s.id === state.siteId) || null;

export function selectSite(id) {
  state.siteId = id;
  try {
    localStorage.setItem('gp.site', id);
  } catch {}
  dispatchEvent(new Event('gp:site'));
}

export async function loadSites() {
  state.sites = await api.get('/sites');
  let saved = null;
  try {
    saved = localStorage.getItem('gp.site');
  } catch {}
  if (!state.sites.some((s) => s.id === state.siteId)) {
    state.siteId = state.sites.some((s) => s.id === saved) ? saved : state.sites[0]?.id ?? null;
  }
  dispatchEvent(new Event('gp:sites'));
}
