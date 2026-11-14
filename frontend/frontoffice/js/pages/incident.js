import { api } from '../api.js';
import { hasScope } from '../state.js';
import { onLive } from '../live.js';
import { h, svg, badge, emptyState, errorBox, field, fmtTime, loading, pageHead, panel, pretty, toast, fill } from '../ui.js';

function evidence(url, event) {
  const figure = h('figure', { class: 'evidence' }, h('img', { src: url, alt: `Photo of ${event.plantId}` }));
  if (event.imageW && event.imageH && event.detections?.length) {
    const size = Math.max(10, event.imageW / 28);
    figure.append(svg('svg', { viewBox: `0 0 ${event.imageW} ${event.imageH}`, preserveAspectRatio: 'none', class: 'boxes' },
      event.detections.filter((d) => d.bbox?.length === 4).map((d) => {
        const [x1, y1, x2, y2] = d.bbox;
        return svg('g', {},
          svg('rect', { x: x1, y: y1, width: x2 - x1, height: y2 - y1, 'stroke-width': size / 5 }),
          svg('text', { x: x1 + 2, y: Math.max(size, y1 - size / 3), 'font-size': size }, `${pretty(d.cls)} ${Math.round(d.score * 100)}%`));
      })));
  }
  return figure;
}

export async function render(view, { id }) {
  const body = h('div', {}, loading());
  fill(view, h('a', { class: 'back', href: '#/incidents' }, '← All incidents'), body);
  let imageUrl = null;
  let busy = false;

  async function act(path, payload, etag, done) {
    if (busy) return;
    busy = true;
    try {
      await api.post(`/incidents/${id}/${path}`, payload, etag);
      toast(done);
    } catch (error) {
      toast(error.status === 412 ? 'Someone else updated this incident. Showing the latest version.' : error.message, 'error');
    } finally {
      busy = false;
      await load();
    }
  }

  function actions(incident, etag) {
    if (incident.status === 'CLOSED') {
      return panel('Closed',
        h('p', {}, `By ${incident.closedBy || '—'} on ${fmtTime(incident.closedAt)}`),
        incident.closeNote ? h('p', { class: 'muted' }, incident.closeNote) : null);
    }
    if (!hasScope('incidents:manage')) return panel('Actions', h('p', { class: 'muted' }, 'Your account can only view incidents.'));

    const parts = [];
    if (incident.status === 'OPEN') {
      parts.push(h('button', { class: 'btn btn-primary', onclick: () => act('ack', {}, etag, 'Incident acknowledged.') }, 'Acknowledge'));
    }
    const verdict = h('select', {}, h('option', { value: 'CONFIRMED' }, 'Disease confirmed'), h('option', { value: 'REJECTED' }, 'False alarm'));
    const note = h('textarea', { rows: 2, maxlength: 1000, placeholder: 'What did you see on the plant?' });
    parts.push(h('form', {
      class: 'stack',
      onsubmit: (e) => {
        e.preventDefault();
        act('review', { verdict: verdict.value, note: note.value.trim() || null }, etag, 'Review saved.');
      },
    }, h('h3', {}, 'Add a review'), field('Verdict', verdict), field('Note', note), h('button', { class: 'btn' }, 'Save review')));

    if (incident.status === 'ACKNOWLEDGED') {
      const reviewed = (incident.reviews || []).length > 0;
      const closeNote = h('input', { type: 'text', maxlength: 1000, placeholder: 'e.g. affected leaves removed' });
      parts.push(h('form', {
        class: 'stack',
        onsubmit: (e) => {
          e.preventDefault();
          act('close', { note: closeNote.value.trim() || null }, etag, 'Incident closed.');
        },
      }, h('h3', {}, 'Close the incident'), field('Note', closeNote),
      h('button', { class: 'btn', disabled: !reviewed }, 'Close incident'),
      reviewed ? null : h('small', { class: 'muted' }, 'Add a review before closing.')));
    }
    return panel('Actions', h('div', { class: 'stack' }, parts));
  }

  async function load() {
    try {
      const { data, etag } = await api.getTagged(`/incidents/${id}`);
      const { incident, events } = data;
      const latest = [...events].reverse().find((e) => e.imageKey);
      const photo = h('div', {}, latest ? loading() : emptyState('No photo for this incident'));

      fill(body, 
        pageHead(pretty(incident.label) || 'Plant health alert',
          `${incident.plantId}${incident.zoneId ? ` · zone ${incident.zoneId}` : ''} · first seen ${fmtTime(incident.openedAt)}`,
          badge(incident.status)),
        h('div', { class: 'columns' },
          panel('Latest evidence', photo,
            h('dl', { class: 'facts' },
              h('dt', {}, 'Detections'), h('dd', {}, incident.occurrences),
              h('dt', {}, 'Confidence'), h('dd', {}, incident.lastScore != null ? `${Math.round(incident.lastScore * 100)} %` : '—'),
              h('dt', {}, 'Last seen'), h('dd', {}, fmtTime(incident.lastSeenAt)),
              h('dt', {}, 'Model'), h('dd', {}, incident.modelVersion || '—'))),
          actions(incident, etag)),
        panel('Reviews', (incident.reviews || []).length
          ? h('ul', { class: 'timeline' }, incident.reviews.map((r) =>
              h('li', {}, badge(r.verdict), ` ${r.by} · ${fmtTime(r.at)}`, r.note ? h('p', {}, r.note) : null)))
          : h('p', { class: 'muted' }, 'No review yet.')),
        panel('Detections from the camera', h('ul', { class: 'timeline' }, [...events].reverse().map((e) =>
          h('li', {}, h('strong', {}, fmtTime(e.ts)), ` · ${Math.round((e.score || 0) * 100)} % · ${e.modelVersion || 'unknown model'}`)))));

      if (latest) {
        try {
          const url = await api.blobUrl(`/images/${encodeURIComponent(latest.imageKey)}`);
          if (imageUrl) URL.revokeObjectURL(imageUrl);
          imageUrl = url;
          fill(photo, evidence(url, latest));
        } catch (error) {
          fill(photo, errorBox(error));
        }
      }
    } catch (error) {
      fill(body, error.status === 404 ? emptyState('Incident not found') : errorBox(error));
    }
  }

  await load();
  const off = onLive((m) => {
    if (m.type === 'incident.updated' && m.data?.id === id && !busy) load();
  });
  return () => {
    off();
    if (imageUrl) URL.revokeObjectURL(imageUrl);
  };
}
