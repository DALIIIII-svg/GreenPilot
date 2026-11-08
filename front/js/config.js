const devServer = location.port === '5500';
const backend = devServer ? 'http://localhost:8080' : location.origin;

export const config = {
  api: `${backend}/greenpilot/api`,
  iam: `${backend}/iam`,
  ws: `${backend.replace(/^http/, 'ws')}/greenpilot/ws`,
  clientId: 'greenpilot-pwa',
  redirectUri: new URL('callback.html', location.href).href.split('?')[0].split('#')[0],
  scope: 'greenhouse:read greenhouse:control incidents:manage admin',
};
