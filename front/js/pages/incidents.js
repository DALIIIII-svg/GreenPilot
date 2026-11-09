import { api, qs } from '../api.js';
import { currentSite } from '../state.js';
import { onLive } from '../live.js';
import { h, badge, emptyState, errorBox, fmtTime, loading, pageHead, pretty, table, fill } from '../ui.js';

const FILTERS = [
  ['Active', 'OPEN,ACKNOWLEDGED'],
  ['Closed', 'CLOSED'],
  ['All', ''],
];

export async function render(view) {
  const site = currentSite();
  if (!site) {
    fill(view, emptyState('No greenhouse yet'));
    return;
  }
  let status = FILTERS[0][1];
  const list = h('div', {}, loading());
  const tabs = h('div', { class: 'tabs', role: 'tablist' },
    FILTERS.map(([label, value]) =>
      h('button', {
        class: `tab${value === status ? ' active' : ''}`,
        role: 'tab',
        onclick: (e) => {
          status = value;
          tabs.querySelectorAll('.tab').forEach((b) => b.classList.toggle('active', b === e.currentTarget));
          load();
        },
      }, label)));

  fill(view, pageHead('Incidents', `Plant health alerts for ${site.name}`), tabs, list);

  async function load() {
    try {
      const items = await api.get(`/incidents${qs({ siteId: site.id, status, limit: 200 })}`);
      fill(list, items.length
        ? table([
            { label: 'Plant', cell: (i) => h('strong', {}, i.plantId) },
            { label: 'Zone', cell: (i) => i.zoneId || '—' },
            { label: 'Problem', cell: (i) => pretty(i.label) },
            { label: 'Detections', cell: (i) => i.occurrences },
            { label: 'Confidence', cell: (i) => (i.lastScore != null ? `${Math.round(i.lastScore * 100)} %` : '—') },
            { label: 'Last seen', cell: (i) => fmtTime(i.lastSeenAt) },
            { label: 'Status', cell: (i) => badge(i.status) },
          ], items, (i) => { location.hash = `#/incidents/${i.id}`; })
        : emptyState('Nothing here', status === 'CLOSED' ? 'No closed incidents yet.' : 'No active alert. Your plants look healthy.'));
    } catch (error) {
      fill(list, errorBox(error));
    }
  }

  await load();
  return onLive((m) => {
    if (m.siteId === site.id && m.type.startsWith('incident')) load();
  });
}
