import { api, qs } from '../api.js';
import { state, selectSite } from '../state.js';
import { h, badge, emptyState, errorBox, field, pageHead, panel, table, fill } from '../ui.js';

let leaflet = null;

function loadLeaflet() {
  if (window.L) return Promise.resolve(window.L);
  if (!leaflet) {
    leaflet = new Promise((resolve, reject) => {
      document.head.append(h('link', { rel: 'stylesheet', href: 'vendor/leaflet/leaflet.css' }));
      const script = h('script', { src: 'vendor/leaflet/leaflet.js' });
      script.onload = () => resolve(window.L);
      script.onerror = () => {
        leaflet = null;
        reject(new Error('The map could not be loaded.'));
      };
      document.head.append(script);
    });
  }
  return leaflet;
}

const located = (site) => site.location?.coordinates?.length === 2;
const latLng = (site) => [site.location.coordinates[1], site.location.coordinates[0]];

function open(siteId) {
  selectSite(siteId);
  location.hash = '#/dashboard';
}

export async function render(view) {
  const radius = h('select', {}, [10, 50, 200, 1000].map((km) => h('option', { value: km }, `${km} km`)));
  radius.value = '50';
  const locate = h('button', { class: 'btn btn-primary' }, 'Use my location');
  const mapBox = h('div', { class: 'map' });
  const list = panel('Your greenhouses');
  fill(view, 
    pageHead('Nearby', 'Find the greenhouses around you'),
    h('div', { class: 'filters' }, field('Radius', radius), locate),
    mapBox,
    list);

  let map = null;
  let layer = null;

  function showList(rows, withDistance) {
    fill(list, h('h2', {}, withDistance ? 'Greenhouses near you' : 'Your greenhouses'), rows.length
      ? table([
          { label: 'Name', cell: (s) => h('strong', {}, s.name) },
          withDistance
            ? { label: 'Distance', cell: (s) => `${(s.distanceM / 1000).toFixed(1)} km` }
            : { label: 'Address', cell: (s) => s.address || '—' },
          withDistance
            ? { label: 'Alerts', cell: (s) => (s.activeIncidents ? badge(`${s.activeIncidents} open`) : 'None') }
            : { label: 'Gateway', cell: (s) => s.gatewayId || '—' },
        ], rows, (s) => open(s.id))
      : emptyState('No greenhouse in this radius', 'Try a larger radius.'));
  }

  async function drawMap(me) {
    try {
      const L = await loadLeaflet();
      if (!map) {
        map = L.map(mapBox, { scrollWheelZoom: false });
        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 18, attribution: '© OpenStreetMap' }).addTo(map);
      }
      layer?.remove();
      layer = L.layerGroup().addTo(map);
      const bounds = [];
      for (const site of state.sites.filter(located)) {
        const popup = h('div', {}, h('strong', {}, site.name), h('br'),
          h('a', { href: '#/dashboard', onclick: () => selectSite(site.id) }, 'Open dashboard'));
        L.marker(latLng(site)).addTo(layer).bindPopup(popup);
        bounds.push(latLng(site));
      }
      if (me) {
        L.circleMarker(me, { radius: 8, color: '#2f6db3', fillOpacity: 0.6 }).addTo(layer).bindPopup('You are here');
        bounds.push(me);
      }
      if (bounds.length) map.fitBounds(bounds, { padding: [40, 40], maxZoom: 13 });
      else map.setView([36.8, 10.18], 7);
    } catch (error) {
      fill(mapBox, errorBox(error));
      mapBox.classList.add('map-off');
    }
  }

  locate.addEventListener('click', () => {
    if (!navigator.geolocation) {
      fill(list, errorBox(new Error('Location is not available on this device.')));
      return;
    }
    locate.disabled = true;
    navigator.geolocation.getCurrentPosition(
      async ({ coords }) => {
        try {
          const rows = await api.get(`/sites/nearby${qs({ lat: coords.latitude, lng: coords.longitude, radiusKm: radius.value })}`);
          showList(rows, true);
          drawMap([coords.latitude, coords.longitude]);
        } catch (error) {
          fill(list, errorBox(error));
        } finally {
          locate.disabled = false;
        }
      },
      () => {
        locate.disabled = false;
        fill(list, errorBox(new Error('Location permission was refused.')));
      },
      { enableHighAccuracy: false, timeout: 10000 });
  });

  showList(state.sites, false);
  drawMap(null);
  return () => map?.remove();
}
