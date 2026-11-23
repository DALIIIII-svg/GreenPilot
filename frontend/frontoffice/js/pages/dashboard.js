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

// A forecast older than this is shown greyed out: the gateway stopped forecasting (stale or missing readings).
const FORECAST_FRESH_SEC = 10 * 60;

function forecastLine(forecast, low) {
  const horizon = forecast.horizonMin || 30;
  const ageSec = Math.max(0, Math.round((Date.now() - Date.parse(forecast.ts)) / 1000));
  const change = forecast.baseline == null ? null : forecast.predicted - forecast.baseline;
  const trend = change == null || Math.abs(change) < 0.5 ? 'steady' : change < 0 ? 'drying' : 'wetter';
  const arrow = { steady: '→', drying: '↘', wetter: '↗' }[trend];
  const dry = low && forecast.predicted < low;
  const stale = ageSec > FORECAST_FRESH_SEC;
  const classes = ['card-forecast', dry && !stale ? 'card-forecast-warn' : '', stale ? 'card-forecast-stale' : ''].filter(Boolean).join(' ');
  return h('div', { class: classes, title: forecast.modelVersion ? `Model ${forecast.modelVersion}` : null },
    h('p', {}, `In ${horizon} min `, h('strong', {}, `${round(forecast.predicted)} %`),
      ` ${arrow}`, change == null ? null : ` ${change > 0 ? '+' : ''}${round(change)}`),
    h('p', { class: 'card-meta' },
      dry && !stale ? `Below the ${low} % threshold · ` : '',
      stale ? `Last forecast ${fmtAge(ageSec)}` : `Forecast ${fmtAge(ageSec)}`));
}

function renderSensors(box, state) {
  const site = state.site;
  const low = site.thresholds?.soilMoistureLowPct;
  const cards = (site.zones || []).map((zone) => {
    const r = reading(state, 'soil_moisture', zone.id);
    const forecast = state.forecasts.find((f) => f.zoneId === zone.id);
    const extra = forecast
      ? forecastLine(forecast, low)
      : h('p', { class: 'card-forecast card-forecast-stale' }, 'No forecast yet');
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

const CHART_PERIODS = { '3h': 3 * 3600000, '24h': 86400000 };
let chartPeriod = '3h';

// Measured moisture per zone, with each forecast drawn at the time it is about to (dashed, reaching into the future).
async function renderChart(box, site) {
  const since = new Date(Date.now() - CHART_PERIODS[chartPeriod]).toISOString();
  const picker = h('div', { class: 'segmented', role: 'group', 'aria-label': 'Period' },
    Object.keys(CHART_PERIODS).map((key) => h('button', {
      class: `btn btn-small${key === chartPeriod ? ' active' : ''}`, type: 'button', 'aria-pressed': String(key === chartPeriod),
      onclick: () => { chartPeriod = key; renderChart(box, site); },
    }, key)));
  const head = h('div', { class: 'panel-head' }, h('h2', {}, 'Soil moisture'), picker);
  try {
    const [rows, forecasts] = await Promise.all([
      api.get(`/measurements${qs({ siteId: site.id, type: 'soil_moisture', from: since, limit: 5000 })}`),
      api.get(`/forecasts${qs({ siteId: site.id, from: since, limit: 5000 })}`).catch(() => []),
    ]);
    const series = (site.zones || []).flatMap((zone) => [
      { name: `${zone.name || zone.id}`, points: rows.filter((r) => r.zoneId === zone.id).map((r) => ({ t: Date.parse(r.ts), v: r.value })) },
      { name: `${zone.name || zone.id} forecast`, dashed: true,
        points: forecasts.filter((f) => f.zoneId === zone.id).map((f) => ({ t: Date.parse(f.ts) + (f.horizonMin || 30) * 60000, v: f.predicted })) },
    ]).filter((s) => s.points.length);
    fill(box, head, lineChart(series, { unit: '%' }),
      forecasts.length ? h('p', { class: 'muted small' }, 'Dashed: what the gateway predicted 30 min earlier for that time.') : null);
  } catch (error) {
    fill(box, head, errorBox(error));
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
      if (message.type === 'telemetry' || message.type === 'forecast') renderChart(chart, site);
    }, 400);
  });
  return () => {
    off();
    clearTimeout(timer);
  };
}
