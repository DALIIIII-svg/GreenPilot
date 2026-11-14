const localFrontend = ['user.greenpilot.local', 'admin.greenpilot.local'].includes(location.hostname);
const devServer = ['5500', '5501'].includes(location.port);
const backend = localFrontend
  ? 'http://api.greenpilot.local:8080'
  : devServer ? 'http://localhost:8080' : location.origin;
const iam = localFrontend ? 'http://iam.greenpilot.local:8080/iam' : `${backend}/iam`;

export const config = {
  api: `${backend}/greenpilot/api`,
  iam,
  ws: `${backend.replace(/^http/, 'ws')}/greenpilot/ws`,
  clientId: 'greenpilot-pwa',
  redirectUri: new URL('callback.html', location.href).href.split('?')[0].split('#')[0],
  scope: 'greenhouse:read greenhouse:control incidents:manage admin',
};
