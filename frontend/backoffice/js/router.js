const routes = [];
let current = null;
let cleanup = null;
let view = null;
let fallback = '/';
let onChange = () => {};

export function route(pattern, load) {
  routes.push({ parts: pattern.split('/'), load });
}

function match(path) {
  const parts = path.split('/');
  for (const r of routes) {
    if (r.parts.length !== parts.length) continue;
    const params = {};
    const ok = r.parts.every((part, i) => {
      if (part.startsWith(':')) {
        params[part.slice(1)] = decodeURIComponent(parts[i]);
        return true;
      }
      return part === parts[i];
    });
    if (ok) return { r, params };
  }
  return null;
}

async function show() {
  const path = location.hash.slice(1) || fallback;
  const found = match(path);
  if (!found) {
    location.replace(`#${fallback}`);
    return;
  }
  const token = Symbol(path);
  current = token;
  if (cleanup) cleanup();
  cleanup = null;
  document.querySelectorAll('dialog[open]').forEach((d) => d.close());
  view.replaceChildren();
  onChange(path);
  const page = await found.r.load();
  if (current !== token) return;
  const result = await page.render(view, found.params);
  if (current === token) cleanup = typeof result === 'function' ? result : null;
  else if (typeof result === 'function') result();
}

export function startRouter(target, home, changed) {
  view = target;
  fallback = home;
  onChange = changed || onChange;
  addEventListener('hashchange', show);
  show();
}

export function rerender() {
  show();
}
