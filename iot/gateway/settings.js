const fs = require('fs');
const path = require('path');

const repo = path.resolve(__dirname, '..', '..');
const defaults = {
    GW_ID: 'pi-01',
    GW_MQTT_HOST: 'localhost',
    GW_MQTT_PORT: '8883',
    GW_CA_FILE: path.join(repo, 'tools', 'certs', 'ca.crt'),
    GW_AUTO_IRRIGATION: 'true'
};
for (const [name, value] of Object.entries(defaults)) {
    if (!process.env[name]) process.env[name] = value;
}
if (!process.env.GW_MQTT_USER) process.env.GW_MQTT_USER = process.env.GW_ID;
process.env.GW_CLIENT_ID = `${process.env.GW_ID}-gateway`;
process.env.GW_COMMAND_TOPIC = `greenpilot/${process.env.GW_ID}/down/cmd`;

// Node-RED does not read credentials from environment variables, so the broker login is written here at each start.
const credentials = { broker: { user: process.env.GW_MQTT_USER, password: process.env.GW_MQTT_PASSWORD } };
fs.writeFileSync(path.join(__dirname, 'flows_cred.json'), JSON.stringify(credentials, null, 4));

module.exports = {
    uiPort: Number(process.env.GW_HTTP_PORT || 1880),
    flowFile: 'flows.json',
    flowFilePretty: true,
    credentialSecret: false,
    contextStorage: {
        default: { module: 'localfilesystem' }
    },
    functionGlobalContext: {
        thingDescription: require('./things/esp32-node.json')
    },
    logging: {
        console: { level: 'info', metrics: false, audit: false }
    },
    editorTheme: {
        projects: { enabled: false },
        tours: false
    }
};
