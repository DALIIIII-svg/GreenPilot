import { api, qs } from '../api.js';
import { currentSite } from '../state.js';
import { h, badge, dialog, download, emptyState, errorBox, field, fmtTime, lineChart, loading, pageHead, panel, round, table, toast, fill } from '../ui.js';

const TYPES = {
  soil_moisture: { label: 'Soil moisture', unit: '%' },
  air_temperature: { label: 'Air temperature', unit: '°C' },
  air_humidity: { label: 'Air humidity', unit: '%' },
};
const PERIODS = { '24h': 86400000, '7d': 7 * 86400000, '30d': 30 * 86400000 };

// For each forecast, compare the prediction and the "nothing changes" baseline
// with the real reading closest to the forecast's target time.
function forecastScore(forecasts, readings) {
  let model = 0;
  let baseline = 0;
  let count = 0;
  for (const f of forecasts) {
    if (f.baseline == null) continue;
    const target = Date.parse(f.ts) + (f.horizonMin || 30) * 60000;
    let best = null;
    for (const r of readings) {
      if (r.zoneId !== f.zoneId) continue;
      const gap = Math.abs(Date.parse(r.ts) - target);
      if (gap <= 5 * 60000 && (!best || gap < best.gap)) best = { gap, value: r.value };
    }
    if (!best) continue;
    model += Math.abs(f.predicted - best.value);
    baseline += Math.abs(f.baseline - best.value);
    count++;
  }
  return count ? { model: model / count, baseline: baseline / count, count } : null;
}

async function showPhoto(row) {
  try {
    const url = await api.blobUrl(`/images/${encodeURIComponent(row.imageKey)}`);
    const { el } = dialog(`${row.plantId} · ${fmtTime(row.ts)}`, h('img', { src: url, alt: `Photo of ${row.plantId}`, class: 'photo' }));
    el.addEventListener('close', () => URL.revokeObjectURL(url));
  } catch (error) {
    toast(error.message, 'error');
  }
}

export async function render(view) {
  const site = currentSite();
  if (!site) {
    fill(view, emptyState('No greenhouse yet'));
    return;
  }
  const type = h('select', {}, Object.entries(TYPES).map(([value, t]) => h('option', { value }, t.label)));
  const period = h('select', {}, h('option', { value: '24h' }, 'Last 24 hours'), h('option', { value: '7d' }, 'Last 7 days'), h('option', { value: '30d' }, 'Last 30 days'));
  const exportButton = h('button', { class: 'btn' }, 'Export CSV');
  const chart = panel(null, loading());
  const forecast = panel('Forecast quality', loading());
  const inspections = panel('Camera inspections', loading());
  fill(view, 
    pageHead('History', site.name, exportButton),
    h('div', { class: 'filters' }, field('Measurement', type), field('Period', period)),
    chart, forecast, inspections);

  let rows = [];

  async function load() {
    const t = TYPES[type.value];
    const from = new Date(Date.now() - PERIODS[period.value]).toISOString();
    fill(chart, loading());
    try {
      rows = await api.get(`/measurements${qs({ siteId: site.id, type: type.value, from, limit: 5000 })}`);
      let series;
      if (type.value === 'soil_moisture') {
        const forecasts = await api.get(`/forecasts${qs({ siteId: site.id, from, limit: 5000 })}`);
        series = (site.zones || []).flatMap((zone) => [
          { name: `${zone.name || zone.id} measured`, points: rows.filter((r) => r.zoneId === zone.id).map((r) => ({ t: Date.parse(r.ts), v: r.value })) },
          { name: `${zone.name || zone.id} forecast`, dashed: true, points: forecasts.filter((f) => f.zoneId === zone.id).map((f) => ({ t: Date.parse(f.ts) + (f.horizonMin || 30) * 60000, v: f.predicted })) },
        ]).filter((s) => s.points.length);
        const score = forecastScore(forecasts, rows);
        fill(forecast, h('h2', {}, 'Forecast quality'), score
          ? h('p', {}, `Average error of the model: `, h('strong', {}, `${round(score.model, 2)} points`),
              ` vs ${round(score.baseline, 2)} for "no change" (${score.count} forecasts checked). `,
              score.model < score.baseline ? 'The model is better than the baseline.' : 'The model does not beat the baseline yet.')
          : h('p', { class: 'muted' }, 'Not enough forecasts with a matching measurement in this period.'));
        forecast.hidden = false;
      } else {
        series = [{ name: t.label, points: rows.map((r) => ({ t: Date.parse(r.ts), v: r.value })) }];
        forecast.hidden = true;
      }
      fill(chart, h('h2', {}, `${t.label} (${t.unit})`), lineChart(series, { unit: t.unit === '%' ? '%' : '' }));
    } catch (error) {
      fill(chart, errorBox(error));
    }
  }

  async function loadInspections() {
    try {
      const items = await api.get(`/inspections${qs({ siteId: site.id, limit: 100 })}`);
      fill(inspections, h('h2', {}, 'Camera inspections'), items.length
        ? table([
            { label: 'Time', cell: (i) => fmtTime(i.ts) },
            { label: 'Plant', cell: (i) => i.plantId },
            { label: 'Result', cell: (i) => badge(i.status) },
            { label: 'Detections', cell: (i) => (i.detections || []).length },
            { label: 'Model', cell: (i) => i.modelVersion || '—' },
            { label: 'Photo', cell: (i) => (i.imageKey ? h('button', { class: 'btn btn-small', onclick: () => showPhoto(i) }, 'View') : '—') },
          ], items)
        : emptyState('No inspection yet'));
    } catch (error) {
      fill(inspections, errorBox(error));
    }
  }

  exportButton.addEventListener('click', () => {
    if (!rows.length) {
      toast('Nothing to export for this period.');
      return;
    }
    const lines = ['time,zone,type,value,unit,quality', ...rows.map((r) => [r.ts, r.zoneId || '', r.type, r.value, r.unit || '', r.quality || ''].join(','))];
    download(`greenpilot-${site.id}-${type.value}.csv`, lines.join('\n'), 'text/csv');
  });
  type.addEventListener('change', load);
  period.addEventListener('change', load);
  await Promise.all([load(), loadInspections()]);
}
