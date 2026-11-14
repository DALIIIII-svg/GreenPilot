import { config } from './config.js';
import { accessToken, clearSession, refresh } from './auth.js';

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export function qs(params) {
  const entries = Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== '');
  return entries.length ? `?${new URLSearchParams(entries)}` : '';
}

async function send(base, path, { method = 'GET', body, etag, retry = true } = {}) {
  const token = await accessToken().catch(() => null);
  const headers = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (etag) headers['If-Match'] = etag;

  let res;
  try {
    res = await fetch(base + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, navigator.onLine ? 'The server cannot be reached.' : 'You are offline.');
  }

  if (res.status === 401 && token && retry) {
    try {
      await refresh();
    } catch {
      clearSession();
      location.reload();
      return new Promise(() => {});
    }
    return send(base, path, { method, body, etag, retry: false });
  }
  if (!res.ok) {
    const detail = await res.json().catch(() => null);
    throw new ApiError(res.status, detail?.error || `Request failed (${res.status})`);
  }
  return res;
}

async function json(base, path, options) {
  const res = await send(base, path, options);
  const text = await res.text();
  return { data: text ? JSON.parse(text) : null, etag: res.headers.get('ETag') };
}

function client(base) {
  return {
    get: async (path) => (await json(base, path)).data,
    getTagged: (path) => json(base, path),
    post: async (path, body = {}, etag) => (await json(base, path, { method: 'POST', body, etag })).data,
    put: async (path, body, etag) => (await json(base, path, { method: 'PUT', body, etag })).data,
    blobUrl: async (path) => URL.createObjectURL(await (await send(base, path)).blob()),
  };
}

export const api = client(config.api);
export const iam = client(config.iam);
