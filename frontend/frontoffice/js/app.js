import { hasSession, login, logout } from './auth.js';
import { api } from './api.js';
import { state, isAdmin, loadSites, selectSite } from './state.js';
import { startLive, onLiveStatus } from './live.js';
import { route, startRouter, rerender } from './router.js';
import { h } from './ui.js';

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}

const $ = (id) => document.getElementById(id);

route('/dashboard', () => import('./pages/dashboard.js'));
route('/incidents', () => import('./pages/incidents.js'));
route('/incidents/:id', () => import('./pages/incident.js'));
route('/history', () => import('./pages/history.js'));
route('/nearby', () => import('./pages/nearby.js'));

function showOnline() {
  $('offline').hidden = navigator.onLine;
}
addEventListener('online', showOnline);
addEventListener('offline', showOnline);
showOnline();

function showSignIn(message) {
  $('shell').hidden = true;
  $('signin').hidden = false;
  $('signin-error').textContent = message || '';
  $('signin-error').hidden = !message;
  $('signin-button').onclick = () => login();
}

function fillSitePicker() {
  const select = $('site-select');
  select.replaceChildren(...state.sites.map((s) => h('option', { value: s.id }, s.name)));
  select.value = state.siteId || '';
  select.disabled = state.sites.length < 2;
  $('site-picker').hidden = state.sites.length === 0;
}

function markActive(path) {
  document.querySelectorAll('#nav a').forEach((a) => {
    const target = a.getAttribute('href').slice(1);
    a.classList.toggle('active', path === target || path.startsWith(`${target}/`));
  });
  document.body.classList.remove('nav-open');
}

async function start() {
  if (!hasSession()) {
    showSignIn();
    return;
  }
  try {
    state.me = await api.get('/users/me');
    if (isAdmin()) {
      showSignIn('Administrator accounts must use the GreenPilot Back Office.');
      $('signin-button').textContent = 'Sign out';
      $('signin-button').onclick = logout;
      return;
    }
    await loadSites();
  } catch (error) {
    if (error.status === 0) {
      $('signin-button').textContent = 'Retry';
      showSignIn(error.message);
      $('signin-button').onclick = () => location.reload();
      return;
    }
    showSignIn(error.message);
    return;
  }

  $('signin').hidden = true;
  $('shell').hidden = false;
  $('me-name').textContent = state.me.profile?.displayName || state.me.profile?.username || '';
  $('me-role').textContent = 'Grower';
  fillSitePicker();

  $('site-select').addEventListener('change', (e) => selectSite(e.target.value));
  $('signout').addEventListener('click', logout);
  $('menu').addEventListener('click', () => document.body.classList.toggle('nav-open'));
  $('scrim').addEventListener('click', () => document.body.classList.remove('nav-open'));
  addEventListener('gp:site', rerender);
  addEventListener('gp:sites', fillSitePicker);

  onLiveStatus((on) => {
    $('live').classList.toggle('on', on);
    $('live').textContent = on ? 'Live' : 'Reconnecting';
  });
  startLive();
  startRouter($('view'), '/dashboard', markActive);
}

start();
