const frontendDomain = location.hostname.match(/^(?:user|admin)\.(.+)$/i)?.[1];
const devServer = ['5500', '5501'].includes(location.port);
const servicePort = devServer ? ':8080' : location.port ? `:${location.port}` : '';
const siblingOrigin = (service) => `${location.protocol}//${service}.${frontendDomain}${servicePort}`;
const backend = frontendDomain
  ? siblingOrigin('api')
  : devServer ? 'http://localhost:8080' : location.origin;
const iam = frontendDomain ? `${siblingOrigin('iam')}/iam` : `${backend}/iam`;

export const config = {
  api: `${backend}/greenpilot/api`,
  iam,
  ws: `${backend.replace(/^http/, 'ws')}/greenpilot/ws`,
  clientId: 'greenpilot-pwa',
  redirectUri: new URL('callback.html', location.href).href.split('?')[0].split('#')[0],
  scope: 'greenhouse:read greenhouse:control incidents:manage',
};
