const SVG = 'http://www.w3.org/2000/svg';

function build(el, attrs, children) {
  let value;
  for (const [key, v] of Object.entries(attrs || {})) {
    if (v === undefined || v === null || v === false) continue;
    if (key.startsWith('on') && typeof v === 'function') el.addEventListener(key.slice(2).toLowerCase(), v);
    else if (key === 'class') el.setAttribute('class', v);
    else if (key === 'value') value = v;
    else if (key === 'dataset') Object.assign(el.dataset, v);
    else el.setAttribute(key, v === true ? '' : v);
  }
  for (const child of children.flat(Infinity)) {
    if (child === undefined || child === null || child === false) continue;
    el.append(child instanceof Node ? child : String(child));
  }
  if (value !== undefined) el.value = value;
  return el;
}

export const h = (tag, attrs, ...children) => build(document.createElement(tag), attrs, children);
export const svg = (tag, attrs, ...children) => build(document.createElementNS(SVG, tag), attrs, children);

export function fill(el, ...children) {
  el.replaceChildren();
  return build(el, null, children);
}

export function fmtTime(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString([], { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
}

export function fmtAge(seconds) {
  if (seconds === undefined || seconds === null) return '—';
  if (seconds < 60) return `${seconds} s ago`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)} min ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} h ago`;
  return `${Math.floor(seconds / 86400)} d ago`;
}

export const round = (value, digits = 1) => (value === undefined || value === null ? '—' : Number(value.toFixed(digits)));
export const pretty = (text) => (text ? String(text).replace(/_/g, ' ').toLowerCase() : '');

export function badge(status) {
  return h('span', { class: `badge badge-${String(status || '').toLowerCase()}` }, pretty(status));
}

export function pageHead(title, subtitle, ...actions) {
  return h('header', { class: 'page-head' },
    h('div', {}, h('h1', {}, title), subtitle ? h('p', { class: 'muted' }, subtitle) : null),
    actions.length ? h('div', { class: 'actions' }, actions) : null);
}

export const panel = (title, ...children) =>
  h('section', { class: 'panel' }, title ? h('h2', {}, title) : null, children);

export const loading = () => h('p', { class: 'muted loading' }, 'Loading…');
export const errorBox = (error) => h('div', { class: 'alert' }, error?.message || String(error));
export const emptyState = (title, text) => h('div', { class: 'empty' }, h('h3', {}, title), text ? h('p', {}, text) : null);

export function toast(message, kind = 'info') {
  const box = document.getElementById('toasts');
  const item = h('div', { class: `toast toast-${kind}`, role: kind === 'error' ? 'alert' : 'status' }, message);
  box.append(item);
  setTimeout(() => item.remove(), kind === 'error' ? 7000 : 4000);
}

export function dialog(title, body, actions = []) {
  const close = () => {
    el.close();
    el.remove();
  };
  const el = h('dialog', { class: 'dialog' },
    h('header', {}, h('h2', {}, title), h('button', { class: 'icon-btn', type: 'button', 'aria-label': 'Close', onclick: close }, '×')),
    h('div', { class: 'dialog-body' }, body),
    actions.length ? h('footer', {}, actions) : null);
  el.addEventListener('close', () => el.remove());
  document.body.append(el);
  el.showModal();
  return { el, close };
}

export function field(label, input, hint) {
  return h('label', { class: 'field' }, h('span', {}, label), input, hint ? h('small', {}, hint) : null);
}

export function table(columns, rows, onRow) {
  return h('div', { class: 'table-wrap' },
    h('table', {},
      h('thead', {}, h('tr', {}, columns.map((c) => h('th', {}, c.label)))),
      h('tbody', {}, rows.map((row) =>
        h('tr', { class: onRow ? 'clickable' : null, onclick: onRow ? () => onRow(row) : null },
          columns.map((c) => h('td', { 'data-label': c.label }, c.cell(row))))))));
}

export function download(name, text, type = 'text/plain') {
  const url = URL.createObjectURL(new Blob([text], { type }));
  h('a', { href: url, download: name }).click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

const PALETTE = ['#1f7a4a', '#2f6db3', '#c47f17', '#8a4fb5'];

export function lineChart(series, { unit = '', height = 240 } = {}) {
  const points = series.flatMap((s) => s.points);
  if (!points.length) return emptyState('No data for this period');
  const width = 720;
  const pad = { left: 44, right: 14, top: 14, bottom: 30 };
  const t0 = Math.min(...points.map((p) => p.t));
  const t1 = Math.max(...points.map((p) => p.t));
  let v0 = Math.min(...points.map((p) => p.v));
  let v1 = Math.max(...points.map((p) => p.v));
  if (v0 === v1) {
    v0 -= 1;
    v1 += 1;
  }
  const span = v1 - v0;
  v0 -= span * 0.08;
  v1 += span * 0.08;
  const x = (t) => pad.left + (t1 === t0 ? 0.5 : (t - t0) / (t1 - t0)) * (width - pad.left - pad.right);
  const y = (v) => pad.top + (1 - (v - v0) / (v1 - v0)) * (height - pad.top - pad.bottom);
  const longRange = t1 - t0 > 2 * 86400000;
  const tick = (t) =>
    new Date(t).toLocaleString([], longRange ? { day: '2-digit', month: 'short' } : { hour: '2-digit', minute: '2-digit' });

  const grid = [];
  for (let i = 0; i <= 4; i++) {
    const v = v0 + ((v1 - v0) * i) / 4;
    grid.push(
      svg('line', { x1: pad.left, x2: width - pad.right, y1: y(v), y2: y(v), class: 'grid' }),
      svg('text', { x: pad.left - 6, y: y(v) + 4, 'text-anchor': 'end', class: 'axis' }, `${round(v)}${unit}`));
  }
  for (let i = 0; i <= 4; i++) {
    const t = t0 + ((t1 - t0) * i) / 4;
    grid.push(svg('text', { x: x(t), y: height - 8, 'text-anchor': i === 0 ? 'start' : i === 4 ? 'end' : 'middle', class: 'axis' }, tick(t)));
  }
  const lines = series.map((s, i) =>
    svg('polyline', {
      points: [...s.points].sort((a, b) => a.t - b.t).map((p) => `${x(p.t).toFixed(1)},${y(p.v).toFixed(1)}`).join(' '),
      fill: 'none',
      stroke: s.color || PALETTE[i % PALETTE.length],
      'stroke-width': 2,
      'stroke-dasharray': s.dashed ? '6 5' : null,
      'stroke-linejoin': 'round',
    }));

  return h('figure', { class: 'chart' },
    svg('svg', { viewBox: `0 0 ${width} ${height}`, role: 'img', 'aria-label': series.map((s) => s.name).join(', ') }, grid, lines),
    h('figcaption', { class: 'legend' },
      series.map((s, i) => {
        const color = s.color || PALETTE[i % PALETTE.length];
        const style = s.dashed ? `border-top:3px dashed ${color};height:0;background:none` : `background:${color}`;
        return h('span', {}, h('i', { style }), s.name);
      })));
}
