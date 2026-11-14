// The apps run on user.<domain> and admin.<domain>; the API and the IAM are their sibling hosts api.<domain> and iam.<domain>.
const domain = location.hostname.match(/^(?:user|admin)\.(.+)$/i)?.[1] ?? 'greenpilot.local';
const port = location.port ? `:${location.port}` : '';
const origin = (service) => `${location.protocol}//${service}.${domain}${port}`;

export const config = {
  api: `${origin('api')}/api`,
  iam: origin('iam'),
  ws: `${origin('api').replace(/^http/, 'ws')}/ws`,
  clientId: 'greenpilot-pwa',
  redirectUri: new URL('callback.html', location.href).href.split('?')[0].split('#')[0],
  scope: 'greenhouse:read greenhouse:control incidents:manage admin',
};
