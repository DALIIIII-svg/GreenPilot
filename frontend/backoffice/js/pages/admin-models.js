import { api, qs } from '../api.js';
import { state, isAdmin, loadSites } from '../state.js';
import { h, badge, dialog, emptyState, errorBox, field, fmtTime, loading, pageHead, panel, table, toast, fill } from '../ui.js';

function parseMetrics(text) {
  const metrics = {};
  for (const line of text.split('\n').map((l) => l.trim()).filter(Boolean)) {
    const [key, value] = line.split('=').map((p) => p.trim());
    if (!key || value === undefined || Number.isNaN(Number(value))) throw new Error(`Invalid metric line: "${line}" (use name = number)`);
    metrics[key] = Number(value);
  }
  return metrics;
}

function registerModel(done) {
  const kind = h('select', {}, h('option', { value: 'VISION' }, 'Vision (leaf disease)'), h('option', { value: 'FORECAST' }, 'Forecast (soil moisture)'));
  const version = h('input', { required: true, placeholder: '1.0.0' });
  const uri = h('input', { required: true, placeholder: 'https://… or file:///models/…' });
  const sha = h('input', { required: true, pattern: '[0-9a-fA-F]{64}', placeholder: '64 hexadecimal characters' });
  const metrics = h('textarea', { rows: 3, class: 'code', placeholder: 'mAP50 = 0.82\nrecall = 0.77' });
  const dataset = h('input', { placeholder: 'pepper-leaf-dataset@v1' });
  const notes = h('textarea', { rows: 2 });
  const form = h('form', { class: 'stack', id: 'model-form' },
    field('Kind', kind), field('Version', version), field('Artifact location', uri),
    field('SHA-256 of the file', sha, 'On Windows: Get-FileHash model.zip'),
    field('Metrics', metrics, 'One per line: name = value'), field('Dataset', dataset), field('Notes', notes));
  const save = h('button', { class: 'btn btn-primary', type: 'submit', form: 'model-form' }, 'Register');
  const { close } = dialog('Register a model release', form, [save]);
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
      await api.post('/models', {
        kind: kind.value,
        version: version.value.trim(),
        artifactUri: uri.value.trim(),
        sha256: sha.value.trim().toLowerCase(),
        metrics: parseMetrics(metrics.value),
        datasetRef: dataset.value.trim() || null,
        notes: notes.value.trim() || null,
      });
      toast('Model registered.');
      close();
      done();
    } catch (error) {
      toast(error.message, 'error');
    }
  });
}

async function deploy(release, siteId, done) {
  const site = state.sites.find((s) => s.id === siteId);
  if (!confirm(`Deploy ${release.kind} ${release.modelVersion} to ${site?.name || siteId}?`)) return;
  try {
    await api.post(`/models/${release.id}/deploy`, { siteId });
    toast('Deployment sent. The version becomes active when the gateway confirms it.');
    done();
  } catch (error) {
    toast(error.message, 'error');
  }
}

function chooseSite(release, done) {
  const select = h('select', {}, state.sites.map((s) => h('option', { value: s.id }, s.name)));
  const go = h('button', { class: 'btn btn-primary' }, 'Deploy');
  const { close } = dialog(`Deploy ${release.kind} ${release.modelVersion}`, field('Greenhouse', select), [go]);
  go.addEventListener('click', () => {
    close();
    deploy(release, select.value, done);
  });
}

export async function render(view) {
  if (!isAdmin()) {
    fill(view, emptyState('Administrators only'));
    return;
  }
  const releasesPanel = panel(null, loading());
  const activePanel = panel(null, loading());
  const auditPanel = panel(null, loading());
  const tabs = h('div', { class: 'tabs' });
  const sections = { models: h('div', {}, activePanel, releasesPanel), audit: h('div', { hidden: true }, auditPanel) };
  for (const [key, label] of [['models', 'Models'], ['audit', 'Audit log']]) {
    tabs.append(h('button', {
      class: `tab${key === 'models' ? ' active' : ''}`,
      onclick: (e) => {
        tabs.querySelectorAll('.tab').forEach((b) => b.classList.toggle('active', b === e.currentTarget));
        Object.entries(sections).forEach(([k, el]) => { el.hidden = k !== key; });
        if (key === 'audit') loadAudit();
      },
    }, label));
  }
  fill(view, 
    pageHead('Models & audit', 'Versions of the AI models on each greenhouse, and the log of every action',
      h('button', { class: 'btn btn-primary', onclick: () => registerModel(loadModels) }, 'Register model')),
    tabs, sections.models, sections.audit);

  let releases = [];

  async function loadModels() {
    try {
      [releases] = await Promise.all([api.get('/models'), loadSites()]);
      const find = (kind, version) => releases.find((r) => r.kind === kind && r.modelVersion === version);
      const reload = () => loadModels();

      fill(activePanel, h('h2', {}, 'Running on each greenhouse'), state.sites.length
        ? table([
            { label: 'Greenhouse', cell: (s) => h('strong', {}, s.name) },
            ...['VISION', 'FORECAST'].map((kind) => ({
              label: kind === 'VISION' ? 'Vision model' : 'Forecast model',
              cell: (s) => {
                const active = s.activeModels?.[kind];
                const previous = s.previousModels?.[kind];
                const back = previous && find(kind, previous);
                return h('div', {}, active ? h('strong', {}, active) : h('span', { class: 'muted' }, 'none'),
                  back ? h('button', { class: 'btn btn-small', onclick: () => deploy(back, s.id, reload) }, `Roll back to ${previous}`) : null);
              },
            })),
          ], state.sites)
        : emptyState('No greenhouse yet'));

      fill(releasesPanel, h('h2', {}, 'Releases'), releases.length
        ? table([
            { label: 'Model', cell: (r) => h('div', {}, badge(r.kind), ' ', h('strong', {}, r.modelVersion)) },
            { label: 'Metrics', cell: (r) => Object.entries(r.metrics || {}).map(([k, v]) => `${k} ${v}`).join(' · ') || '—' },
            { label: 'Dataset', cell: (r) => r.datasetRef || '—' },
            { label: 'Registered', cell: (r) => `${fmtTime(r.createdAt)} · ${r.createdBy || ''}` },
            { label: 'Deployments', cell: (r) => (r.deployments || []).length },
            { label: '', cell: (r) => h('button', { class: 'btn btn-small', onclick: () => chooseSite(r, reload) }, 'Deploy') },
          ], releases)
        : emptyState('No model registered yet', 'Register a release after training it.'));
    } catch (error) {
      fill(releasesPanel, errorBox(error));
    }
  }

  async function loadAudit() {
    const filter = h('input', { type: 'search', placeholder: 'Filter by user, action or detail' });
    const siteFilter = h('select', {}, h('option', { value: '' }, 'All greenhouses'), state.sites.map((s) => h('option', { value: s.id }, s.name)));
    const result = h('div', {}, loading());
    fill(auditPanel, h('h2', {}, 'Audit log'), h('div', { class: 'filters' }, field('Search', filter), field('Greenhouse', siteFilter)), result);
    let entries = [];
    const draw = () => {
      const q = filter.value.trim().toLowerCase();
      const rows = entries.filter((e) => !q || [e.username, e.action, e.details, e.siteId].join(' ').toLowerCase().includes(q));
      fill(result, rows.length
        ? table([
            { label: 'Time', cell: (e) => fmtTime(e.ts) },
            { label: 'User', cell: (e) => e.username || 'system' },
            { label: 'Action', cell: (e) => h('code', {}, e.action) },
            { label: 'Greenhouse', cell: (e) => e.siteId || '—' },
            { label: 'Detail', cell: (e) => h('span', { class: 'detail' }, e.details || '') },
          ], rows)
        : emptyState('No entry'));
    };
    const fetchEntries = async () => {
      fill(result, loading());
      try {
        entries = await api.get(`/audit${qs({ siteId: siteFilter.value, limit: 500 })}`);
        draw();
      } catch (error) {
        fill(result, errorBox(error));
      }
    };
    filter.addEventListener('input', draw);
    siteFilter.addEventListener('change', fetchEntries);
    await fetchEntries();
  }

  await loadModels();
}
