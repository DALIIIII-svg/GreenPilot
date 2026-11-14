const WebSocket = require('ws');
const readline = require('readline');

const url = process.env.GW_FIELD_URL || 'ws://localhost:1880/ws/esp32';
const telemetryMs = Number(process.env.TELEMETRY_SEC || 15) * 1000;
const deviceId = 'esp32-node';

const state = {
    soil: { A: 45, B: 40 },
    temperature: 24,
    humidity: 60,
    water: true,
    pump: null,
    maxDurationSec: 30,
    recentIds: []
};

let ws;
let linked = false;

function send(obj) {
    if (linked) ws.send(JSON.stringify(obj));
}

function round(v) {
    return Math.round(v * 10) / 10;
}

function sendTelemetry() {
    send({
        type: 'telemetry',
        deviceId,
        ts: Date.now(),
        pump: state.pump ? 'ON' : 'OFF',
        readings: [
            { type: 'soil_moisture', zoneId: 'A', value: round(state.soil.A), unit: '%', quality: 'OK' },
            { type: 'soil_moisture', zoneId: 'B', value: round(state.soil.B), unit: '%', quality: 'OK' },
            { type: 'air_temperature', value: round(state.temperature), unit: '°C', quality: 'OK' },
            { type: 'air_humidity', value: round(state.humidity), unit: '%', quality: 'OK' },
            { type: 'reservoir', value: state.water ? 1 : 0, quality: 'OK' }
        ]
    });
}

function ack(commandId, status, reason) {
    send({ type: 'ack', commandId, status, reason });
    log(`command ${commandId} -> ${status}${reason ? ' (' + reason + ')' : ''}`);
}

function stopPump(status, reason) {
    const { commandId } = state.pump;
    clearTimeout(state.pump.timer);
    state.pump = null;
    ack(commandId, status, reason);
}

function handleCommand(m) {
    const id = m.commandId;
    if (!id) return;
    if (state.recentIds.includes(id)) return;
    state.recentIds = [...state.recentIds.slice(-7), id];
    if (m.action !== 'IRRIGATE') return ack(id, 'REJECTED', 'unknown action');
    if (m.expiresAt && Date.now() > m.expiresAt) return ack(id, 'REJECTED', 'command expired');
    if (!(m.durationSec >= 1 && m.durationSec <= state.maxDurationSec)) return ack(id, 'REJECTED', 'duration out of range');
    if (!state.water) return ack(id, 'REJECTED', 'reservoir empty');
    if (state.pump) return ack(id, 'REJECTED', 'pump already running');

    state.pump = {
        commandId: id,
        timer: setTimeout(() => stopPump('COMPLETED'), m.durationSec * 1000)
    };
    ack(id, 'ACCEPTED');
}

function onMessage(data) {
    let m;
    try {
        m = JSON.parse(data);
    } catch {
        return log('invalid JSON from gateway');
    }
    if (m.type === 'command') handleCommand(m);
    if (m.type === 'config' && m.maxDurationSec >= 1) {
        state.maxDurationSec = m.maxDurationSec;
        log(`max irrigation duration set to ${m.maxDurationSec} s`);
    }
}

function connect() {
    ws = new WebSocket(url);
    ws.on('open', () => {
        linked = true;
        log(`connected to ${url}`);
        send({ type: 'hello', deviceId, firmware: 'simulator' });
        sendTelemetry();
    });
    ws.on('message', onMessage);
    ws.on('close', () => {
        if (linked) log('gateway connection lost, retrying every 5 s');
        linked = false;
        setTimeout(connect, 5000);
    });
    ws.on('error', () => {});
}

function physics() {
    for (const zone of ['A', 'B']) {
        state.soil[zone] += state.pump ? 1.5 : -0.05;
        state.soil[zone] = Math.min(95, Math.max(5, state.soil[zone]));
    }
    state.temperature += (Math.random() - 0.5) * 0.2;
    state.humidity += (Math.random() - 0.5) * 0.4;
}

function log(text) {
    if (process.stdout.isTTY) {
        readline.clearLine(process.stdout, 0);
        readline.cursorTo(process.stdout, 0);
    }
    console.log(text);
    showStatus();
}

function showStatus() {
    if (!process.stdout.isTTY) return;
    readline.clearLine(process.stdout, 0);
    readline.cursorTo(process.stdout, 0);
    process.stdout.write(
        `A ${state.soil.A.toFixed(1)}%  B ${state.soil.B.toFixed(1)}%  ${state.temperature.toFixed(1)}°C  ` +
        `${state.humidity.toFixed(0)}%RH  water ${state.water ? 'OK ' : 'LOW'}  pump ${state.pump ? 'ON ' : 'OFF'}  ` +
        `| a/z: zone A +/-  s/x: zone B +/-  r: reservoir  t: send now  q: quit`
    );
}

function onKey(key) {
    switch (key) {
        case 'a': state.soil.A = Math.min(95, state.soil.A + 5); break;
        case 'z': state.soil.A = Math.max(5, state.soil.A - 5); break;
        case 's': state.soil.B = Math.min(95, state.soil.B + 5); break;
        case 'x': state.soil.B = Math.max(5, state.soil.B - 5); break;
        case 't': sendTelemetry(); break;
        case 'r':
            state.water = !state.water;
            send({ type: 'event', name: 'lowWater', value: !state.water, ts: Date.now() });
            if (!state.water && state.pump) stopPump('FAILED', 'reservoir empty during irrigation');
            sendTelemetry();
            break;
        case 'q':
        case '\u0003':
            process.exit(0);
    }
    showStatus();
}

if (process.stdin.isTTY) {
    process.stdin.setRawMode(true);
    process.stdin.setEncoding('utf8');
    process.stdin.on('data', onKey);
}

connect();
setInterval(() => { physics(); showStatus(); }, 1000);
setInterval(sendTelemetry, telemetryMs);
