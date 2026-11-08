import { config } from './config.js';
import { accessToken } from './auth.js';

const listeners = new Set();
let socket = null;
let attempt = 0;
let stopped = true;
let onStatus = () => {};

export function onLive(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function onLiveStatus(callback) {
  onStatus = callback;
}

export function startLive() {
  stopped = false;
  connect();
}

export function stopLive() {
  stopped = true;
  socket?.close();
}

async function connect() {
  if (stopped) return;
  const token = await accessToken().catch(() => null);
  if (!token) return;
  socket = new WebSocket(config.ws);
  socket.onopen = () => socket.send(JSON.stringify({ type: 'auth', token }));
  socket.onmessage = (event) => {
    let message;
    try {
      message = JSON.parse(event.data);
    } catch {
      return;
    }
    if (message.type === 'auth_ok') {
      attempt = 0;
      onStatus(true);
      return;
    }
    listeners.forEach((listener) => listener(message));
  };
  socket.onclose = () => {
    onStatus(false);
    if (!stopped) setTimeout(connect, Math.min(30_000, 1000 * 2 ** attempt++));
  };
}
