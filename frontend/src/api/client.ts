import axios, { type InternalAxiosRequestConfig } from 'axios';
import { tokenManager } from '../auth/TokenManager';
let refreshAccessToken: (() => Promise<string>) | undefined;
export const setRefreshHandler = (handler: () => Promise<string>) => { refreshAccessToken = handler; };
export const api = axios.create({ baseURL: import.meta.env.VITE_API_BASE || '/greenpilot/api', headers: { Accept: 'application/json' } });
api.interceptors.request.use((config) => { const token = tokenManager.read()?.access_token; if (token) config.headers.Authorization = `Bearer ${token}`; return config; });
let refreshing: Promise<string> | null = null;
api.interceptors.response.use((response) => response, async (error: unknown) => {
  const e = error as { config?: InternalAxiosRequestConfig & { _retried?: boolean }; response?: { status?: number } };
  if (e.response?.status === 401 && e.config && !e.config._retried && refreshAccessToken) {
    e.config._retried = true;
    if (!refreshing) refreshing = refreshAccessToken().finally(() => { refreshing = null; });
    const token = await refreshing; e.config.headers.Authorization = `Bearer ${token}`; return api.request(e.config);
  }
  return Promise.reject(error);
});
