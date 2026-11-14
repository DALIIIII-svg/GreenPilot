import { api, qs } from '../api.js';
import { currentSite, hasScope } from '../state.js';
import { onLive } from '../live.js';
import { h, badge, emptyState, errorBox, fmtAge, fmtTime, lineChart, loading, pageHead, panel, round, toast, fill } from '../ui.js';

const LIVE_TYPES = ['telemetry', 'forecast', 'command', 'incident.opened', 'incident.updated'];

function reading(state, type, zoneId) {
  return state.readings.find((r) => r.type === type && (zoneId ? r.zoneId === zoneId : !r.zoneId));
}

function sensorCard(title, r, { value, warn, extra } = {}) {
  const stale = !r || r.stale;
  return h('article', { class: `card${stale ? ' card-stale' : ''}${warn ? ' card-warn' : ''}` },
    h('p', { class: 'card-label' }, title),
    h('p', { class: 'card-value' }, r ? value ?? `${round(r.value)} ${r.unit || ''}` : '—'),
    h('p', { class: 'card-meta' }, r ? `${fmtAge(r.ageSec)}${r.stale ? ' · stale' : ''}` : 'No data yet'),
    extra);
}

function renderSensors(box, state) {
  const site = state.site;
  const low = site.thresholds?.soilMoistureLowPct;
  const cards = (site.zones || []).map((zone) => {
    const r = reading(state, 'soil_moisture', zone.id);
    const forecast = state.forecasts.find((f) => f.zoneId === zone.id);
    const extra = forecast
      ? h('p', { class: 'card-forecast' }, `In ${forecast.horizonMin || 30} min: ${round(forecast.predicted)} %`)
      : null;
    return sensorCard(`Soil moisture · ${zone.name || zone.id}`, r, { warn: r && low && r.value < low, extra });
  });
  const reservoir = reading(state, 'reservoir');
  cards.push(
    sensorCard('Air temperature', reading(state, 'air_temperature')),
    sensorCard('Air humidity', reading(state, 'air_humidity')),
    sensorCard('Water reservoir', reservoir, { value: reservoir?.value ? 'OK' : 'Low', warn: reservoir && !reservoir.value }),
    h('a', { class: `card card-link${state.openIncidents ? ' card-warn' : ''}`, href: '#/incidents' },
      h('p', { class: 'card-label' }, 'Open incidents'),
      h('p', { class: 'card-value' }, String(state.openIncidents)),
      h('p', { class: 'card-meta' }, state.openIncidents ? 'Review them' : 'All plants look fine')));
  fill(box, ...cards);
}

async function renderChart(box, site) {
  const since = new Date(Date.now() - 86400000).toISOString();
  try {
    const rows = await api.get(`/measurements${qs({ siteId: site.id, type: 'soil_moisture', from: since, limit: 2000 })}`);
    const series = (site.zones || []).map((zone) => ({
      name: zone.name || zone.id,
      points: rows.filter((r) => r.zoneId === zone.id).map((r) => ({ t: Date.parse(r.ts), v: r.value })),
    }));
    fill(box, h('h2', {}, 'Soil moisture, last 24 h'), lineChart(series, { unit: '%' }));
  } catch (error) {
    fill(box, h('h2', {}, 'Soil moisture, last 24 h'), errorBox(error));
  }
}

function renderIrrigation(box, state, reload) {
  const site = state.site;
  const max = site.irrigation?.maxDurationSec ?? 30;
  const cooldown = site.irrigation?.cooldownSec ?? 300;
  const canWater = hasScope('greenhouse:control');
  const output = h('output', {}, `${Math.min(10, max)} s`);
  const slider = h('input', {
    type: 'range', min: 1, max, value: Math.min(10, max),
    oninput: () => { output.textContent = `${slider.value} s`; },
  });
  const button = h('button', { class: 'btn btn-primary', disabled: !canWater }, 'Water now');
  button.addEventListener('click', async () => {
    const seconds = Number(slider.value);
    if (!confirm(`Water ${site.name} for ${seconds} seconds?`)) return;
    button.disabled = true;
    try {
      await api.post('/irrigation', { siteId: site.id, durationSec: seconds });
      toast('Command sent to the greenhouse.');
      await reload();
    } catch (error) {
      toast(error.message, 'error');
    } finally {
      button.disabled = !canWater;
    }
  });

  const last = state.lastIrrigation;
  fill(box, 
    h('h2', {}, 'Irrigation'),
    h('div', { class: 'slider' }, h('label', { for: 'duration' }, 'Duration'), slider, output),
    h('p', { class: 'muted small' }, `Limit ${max} s per watering, ${Math.round(cooldown / 60)} min between waterings.`),
    button,
    canWater ? null : h('p', { class: 'muted small' }, 'Your account cannot send irrigation commands.'),
    h('div', { class: 'last-command' },
      h('p', { class: 'card-label' }, 'Last watering'),
      last
        ? h('p', {}, `${last.durationSec} s · ${fmtTime(last.issuedAt)} · `, badge(last.status))
        : h('p', { class: 'muted' }, 'None yet')));
}

export async function render(view) {
  const site = currentSite();
  if (!site) {
    fill(view, emptyState('No greenhouse yet', 'Ask an administrator to give you access to a site.'));
    return;
  }
  const sensors = h('section', { class: 'cards' }, loading());
  const chart = panel(null, loading());
  const irrigation = panel(null, loading());
  fill(view, pageHead(site.name, site.address || site.id), sensors, h('div', { class: 'columns' }, chart, irrigation));

  const reload = async () => {
    try {
      const state = await api.get(`/sites/${site.id}/state`);
      renderSensors(sensors, state);
      renderIrrigation(irrigation, state, reload);
    } catch (error) {
      fill(sensors, errorBox(error));
    }
  };
  await Promise.all([reload(), renderChart(chart, site)]);

  let timer;
  const off = onLive((message) => {
    if (message.siteId !== site.id || !LIVE_TYPES.includes(message.type)) return;
    clearTimeout(timer);
    timer = setTimeout(() => {
      reload();
      if (message.type === 'telemetry') renderChart(chart, site);
    }, 400);
  });
  return () => {
    off();
    clearTimeout(timer);
  };
}
