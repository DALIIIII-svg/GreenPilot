import { api, qs } from '../api.js';
import { state, isAdmin, loadSites } from '../state.js';
import { h, dialog, emptyState, errorBox, field, fmtTime, loading, pageHead, panel, table, toast, fill } from '../ui.js';

const KINDS = ['ESP32_NODE', 'CAMERA', 'GATEWAY'];

const numberOrNull = (input) => (input.value === '' ? null : Number(input.value));

function zoneRow(zone = { id: '', name: '', plants: [] }) {
  const row = h('div', { class: 'zone-row' },
    h('input', { placeholder: 'Id (A)', value: zone.id, 'data-k': 'id', required: true, pattern: '[A-Za-z0-9_-]+' }),
    h('input', { placeholder: 'Name', value: zone.name || '', 'data-k': 'name' }),
    h('input', { placeholder: 'Plants: pepper-1, pepper-2', value: (zone.plants || []).map((p) => p.id).join(', '), 'data-k': 'plants' }),
    h('button', { type: 'button', class: 'icon-btn', 'aria-label': 'Remove zone', onclick: () => row.remove() }, '×'));
  row.plants = zone.plants || [];
  return row;
}

function readZones(container) {
  return [...container.querySelectorAll('.zone-row')].map((row) => {
    const get = (k) => row.querySelector(`[data-k="${k}"]`).value.trim();
    const known = new Map(row.plants.map((p) => [p.id, p]));
    return {
      id: get('id'),
      name: get('name') || null,
      plants: get('plants').split(',').map((p) => p.trim()).filter(Boolean).map((id) => known.get(id) || { id }),
    };
  });
}

async function editSite(existing, done) {
  let etag = null;
  let site = existing;
  if (existing) {
    const tagged = await api.getTagged(`/sites/${existing.id}`);
    site = tagged.data;
    etag = tagged.etag;
  }
  const [lng, lat] = site?.location?.coordinates || ['', ''];
  const input = (name, attrs = {}) => h('input', { name, ...attrs });
  const f = {
    id: input('id', { value: site?.id || '', required: true, pattern: '[a-z0-9-]+', disabled: Boolean(site) }),
    name: input('name', { value: site?.name || '', required: true, maxlength: 100 }),
    lat: input('lat', { type: 'number', step: 'any', min: -90, max: 90, value: lat, required: true }),
    lng: input('lng', { type: 'number', step: 'any', min: -180, max: 180, value: lng, required: true }),
    address: input('address', { value: site?.address || '', maxlength: 200 }),
    gatewayId: input('gatewayId', { value: site?.gatewayId || '', pattern: '[A-Za-z0-9_-]+' }),
    maxDuration: input('maxDuration', { type: 'number', min: 1, max: 3600, value: site?.irrigation?.maxDurationSec ?? 30 }),
    cooldown: input('cooldown', { type: 'number', min: 0, value: site?.irrigation?.cooldownSec ?? 300 }),
    low: input('low', { type: 'number', min: 0, max: 100, step: 'any', value: site?.thresholds?.soilMoistureLowPct ?? '' }),
    high: input('high', { type: 'number', min: 0, max: 100, step: 'any', value: site?.thresholds?.soilMoistureHighPct ?? '' }),
  };
  const zones = h('div', { class: 'zones' }, (site?.zones?.length ? site.zones : [undefined]).map((z) => zoneRow(z)));
  const form = h('form', { class: 'form-grid', id: 'site-form' },
    field('Site id', f.id, 'Lowercase letters, digits and dashes'), field('Name', f.name),
    field('Latitude', f.lat), field('Longitude', f.lng),
    field('Address', f.address), field('Gateway id', f.gatewayId, 'MQTT username of the Raspberry Pi, e.g. pi-01'),
    h('fieldset', { class: 'span-2' }, h('legend', {}, 'Zones and plants'), zones,
      h('button', { type: 'button', class: 'btn btn-small', onclick: () => zones.append(zoneRow()) }, 'Add zone')),
    field('Max watering (s)', f.maxDuration), field('Cooldown (s)', f.cooldown),
    field('Soil moisture low (%)', f.low), field('Soil moisture high (%)', f.high));

  const save = h('button', { class: 'btn btn-primary', type: 'submit', form: 'site-form' }, 'Save');
  const { close } = dialog(site ? `Edit ${site.name}` : 'New greenhouse', form, [save]);
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const body = {
      name: f.name.value.trim(),
      lat: Number(f.lat.value),
      lng: Number(f.lng.value),
      address: f.address.value.trim(),
      gatewayId: f.gatewayId.value.trim() || undefined,
      zones: readZones(zones),
      irrigation: { maxDurationSec: numberOrNull(f.maxDuration), cooldownSec: numberOrNull(f.cooldown) },
      thresholds: { soilMoistureLowPct: numberOrNull(f.low), soilMoistureHighPct: numberOrNull(f.high) },
    };
    save.disabled = true;
    try {
      if (site) await api.put(`/sites/${site.id}`, body, etag);
      else await api.post('/sites', { id: f.id.value.trim(), ...body });
      toast(site ? 'Greenhouse updated. New limits were sent to the gateway.' : 'Greenhouse created.');
      close();
      await loadSites();
      done();
    } catch (error) {
      toast(error.status === 412 ? 'This greenhouse was changed meanwhile. Close and open it again.' : error.message, 'error');
      save.disabled = false;
    }
  });
}

async function editDevice(site, existing, done) {
  let etag = null;
  let device = existing;
  if (existing) {
    const tagged = await api.getTagged(`/devices/${encodeURIComponent(existing.id)}`);
    device = tagged.data;
    etag = tagged.etag;
  }
  const id = h('input', { value: device?.id || '', required: true, pattern: '[A-Za-z0-9_-]+', disabled: Boolean(device) });
  const kind = h('select', { value: device?.kind || 'ESP32_NODE' }, KINDS.map((k) => h('option', { value: k }, k)));
  const name = h('input', { value: device?.name || '' });
  const zones = h('div', { class: 'checks' }, (site.zones || []).map((z) =>
    h('label', {}, h('input', { type: 'checkbox', value: z.id, checked: (device?.zoneIds || []).includes(z.id) }), ` ${z.name || z.id}`)));
  let td = '';
  if (device?.thingDescription) {
    try {
      td = JSON.stringify(JSON.parse(device.thingDescription), null, 2);
    } catch {
      td = device.thingDescription;
    }
  }
  const thing = h('textarea', { rows: 10, class: 'code', value: td, placeholder: '{ "@context": "https://www.w3.org/2022/wot/td/v1.1", "title": "…" }' });
  const form = h('form', { class: 'stack', id: 'device-form' },
    field('Device id', id), field('Kind', kind), field('Name', name), field('Zones', zones),
    field('W3C Thing Description (JSON)', thing, 'Needs at least "@context" and "title".'));
  const save = h('button', { class: 'btn btn-primary', type: 'submit', form: 'device-form' }, 'Save');
  const { close } = dialog(device ? `Edit ${device.id}` : `New device in ${site.name}`, form, [save]);

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    let thingDescription;
    if (thing.value.trim()) {
      try {
        thingDescription = JSON.parse(thing.value);
      } catch {
        toast('The Thing Description is not valid JSON.', 'error');
        return;
      }
    }
    save.disabled = true;
    try {
      await api.put(`/devices/${encodeURIComponent(id.value.trim())}`, {
        siteId: site.id,
        kind: kind.value,
        name: name.value.trim() || null,
        zoneIds: [...zones.querySelectorAll('input:checked')].map((c) => c.value),
        thingDescription,
      }, etag);
      toast('Device saved.');
      close();
      done();
    } catch (error) {
      toast(error.message, 'error');
      save.disabled = false;
    }
  });
}

async function showThing(device) {
  try {
    const td = await api.get(`/devices/${encodeURIComponent(device.id)}/td`);
    dialog(`${device.id} · Thing Description`, h('pre', { class: 'code' }, JSON.stringify(td, null, 2)));
  } catch (error) {
    toast(error.status === 404 ? 'This device has no Thing Description yet.' : error.message, 'error');
  }
}

export async function render(view) {
  if (!isAdmin()) {
    fill(view, emptyState('Administrators only'));
    return;
  }
  let selected = state.siteId;
  const sitesPanel = panel(null);
  const devicesPanel = panel(null);
  fill(view, 
    pageHead('Sites & devices', 'Greenhouses, their zones, limits and connected devices',
      h('button', { class: 'btn btn-primary', onclick: () => editSite(null, draw).catch((e) => toast(e.message, 'error')) }, 'New greenhouse')),
    sitesPanel, devicesPanel);

  async function drawDevices() {
    const site = state.sites.find((s) => s.id === selected);
    if (!site) {
      fill(devicesPanel, h('h2', {}, 'Devices'), emptyState('Select a greenhouse above'));
      return;
    }
    fill(devicesPanel, h('h2', {}, `Devices · ${site.name}`), loading());
    try {
      const devices = await api.get(`/devices${qs({ siteId: site.id })}`);
      fill(devicesPanel, 
        h('div', { class: 'panel-head' }, h('h2', {}, `Devices · ${site.name}`),
          h('button', { class: 'btn btn-small', onclick: () => editDevice(site, null, drawDevices).catch((e) => toast(e.message, 'error')) }, 'Add device')),
        devices.length
          ? table([
              { label: 'Id', cell: (d) => h('strong', {}, d.id) },
              { label: 'Kind', cell: (d) => d.kind },
              { label: 'Name', cell: (d) => d.name || '—' },
              { label: 'Zones', cell: (d) => (d.zoneIds || []).join(', ') || '—' },
              { label: 'Updated', cell: (d) => fmtTime(d.updatedAt) },
              { label: '', cell: (d) => h('span', { class: 'row-actions' },
                  h('button', { class: 'btn btn-small', onclick: (e) => { e.stopPropagation(); showThing(d); } }, 'TD'),
                  h('button', { class: 'btn btn-small', onclick: (e) => { e.stopPropagation(); editDevice(site, d, drawDevices).catch((x) => toast(x.message, 'error')); } }, 'Edit')) },
            ], devices)
          : emptyState('No device registered', 'Add the ESP32 node and the camera of this greenhouse.'));
    } catch (error) {
      fill(devicesPanel, errorBox(error));
    }
  }

  function draw() {
    fill(sitesPanel, h('h2', {}, 'Greenhouses'), state.sites.length
      ? table([
          { label: 'Name', cell: (s) => h('strong', {}, s.name) },
          { label: 'Id', cell: (s) => s.id },
          { label: 'Gateway', cell: (s) => s.gatewayId || '—' },
          { label: 'Zones', cell: (s) => (s.zones || []).map((z) => `${z.id} (${(z.plants || []).length})`).join(', ') || '—' },
          { label: 'Limits', cell: (s) => `${s.irrigation?.maxDurationSec ?? '—'} s / ${s.irrigation?.cooldownSec ?? '—'} s` },
          { label: '', cell: (s) => h('button', { class: 'btn btn-small', onclick: (e) => { e.stopPropagation(); editSite(s, draw).catch((x) => toast(x.message, 'error')); } }, 'Edit') },
        ], state.sites, (s) => {
          selected = s.id;
          drawDevices();
        })
      : emptyState('No greenhouse yet', 'Create the first one.'));
    drawDevices();
  }

  draw();
}
