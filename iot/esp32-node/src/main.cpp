#include <Arduino.h>
#include <WiFi.h>
#include <WebSocketsClient.h>
#include <ArduinoJson.h>
#include <DHTesp.h>
#include "config.h"

WebSocketsClient ws;
DHTesp dht;

bool linked = false;
unsigned long lastTelemetry = 0;

bool pumpOn = false;
unsigned long pumpStartedAt = 0;
unsigned long pumpDurationMs = 0;
String pumpCommandId;
int maxDurationSec = DEFAULT_MAX_DURATION_SEC;

String recentIds[8];
int recentNext = 0;

bool lastReservoirOk = true;

// Epoch time received from the gateway (no NTP: the field node only talks to the gateway).
bool clockSet = false;
uint64_t clockOffsetMs = 0;

bool clockSynced() {
  return clockSet;
}

uint64_t nowMs() {
  return clockSet ? clockOffsetMs + millis() : 0;
}

bool reservoirOk() {
  return digitalRead(PIN_RESERVOIR) == HIGH;
}

float soilPercent(int pin, int dry, int wet) {
  int raw = analogRead(pin);
  float pct = (float)(raw - dry) * 100.0f / (float)(wet - dry);
  return constrain(pct, 0.0f, 100.0f);
}

void send(JsonDocument &doc) {
  if (!linked) return;
  String out;
  serializeJson(doc, out);
  ws.sendTXT(out);
}

void sendAck(const String &commandId, const char *status, const char *reason = nullptr) {
  JsonDocument doc;
  doc["type"] = "ack";
  doc["commandId"] = commandId;
  doc["status"] = status;
  if (reason) doc["reason"] = reason;
  send(doc);
  Serial.printf("command %s -> %s %s\n", commandId.c_str(), status, reason ? reason : "");
}

void addReading(JsonArray readings, const char *type, const char *zoneId, float value, const char *unit) {
  JsonObject r = readings.add<JsonObject>();
  r["type"] = type;
  if (zoneId) r["zoneId"] = zoneId;
  r["value"] = round(value * 10) / 10.0;
  if (unit) r["unit"] = unit;
  r["quality"] = "OK";
}

void sendTelemetry() {
  JsonDocument doc;
  doc["type"] = "telemetry";
  doc["deviceId"] = DEVICE_ID;
  doc["ts"] = nowMs();
  doc["pump"] = pumpOn ? "ON" : "OFF";
  JsonArray readings = doc["readings"].to<JsonArray>();

  addReading(readings, "soil_moisture", "A", soilPercent(PIN_SOIL_A, SOIL_A_DRY, SOIL_A_WET), "%");
  addReading(readings, "soil_moisture", "B", soilPercent(PIN_SOIL_B, SOIL_B_DRY, SOIL_B_WET), "%");

  TempAndHumidity th = dht.getTempAndHumidity();
  if (dht.getStatus() == DHTesp::ERROR_NONE) {
    addReading(readings, "air_temperature", nullptr, th.temperature, "°C");
    addReading(readings, "air_humidity", nullptr, th.humidity, "%");
  } else {
    Serial.printf("DHT22 read failed: %s\n", dht.getStatusString());
  }
  addReading(readings, "reservoir", nullptr, reservoirOk() ? 1 : 0, nullptr);

  send(doc);
}

void sendLowWaterEvent(bool low) {
  JsonDocument doc;
  doc["type"] = "event";
  doc["name"] = "lowWater";
  doc["value"] = low;
  doc["ts"] = nowMs();
  send(doc);
}

bool alreadySeen(const String &id) {
  for (const String &s : recentIds) {
    if (s == id) return true;
  }
  recentIds[recentNext] = id;
  recentNext = (recentNext + 1) % 8;
  return false;
}

void pumpOff() {
  digitalWrite(PIN_PUMP, LOW);
  pumpOn = false;
}

void handleCommand(JsonDocument &doc) {
  String id = doc["commandId"] | "";
  String action = doc["action"] | "";
  int duration = doc["durationSec"] | 0;
  uint64_t expiresAt = doc["expiresAt"] | (uint64_t)0;

  if (id.isEmpty()) return;
  if (alreadySeen(id)) return;
  if (action != "IRRIGATE") return sendAck(id, "REJECTED", "unknown action");
  if (expiresAt > 0 && clockSynced() && nowMs() > expiresAt) return sendAck(id, "REJECTED", "command expired");
  if (duration < 1 || duration > maxDurationSec) return sendAck(id, "REJECTED", "duration out of range");
  if (!reservoirOk()) return sendAck(id, "REJECTED", "reservoir empty");
  if (pumpOn) return sendAck(id, "REJECTED", "pump already running");

  digitalWrite(PIN_PUMP, HIGH);
  pumpOn = true;
  pumpStartedAt = millis();
  pumpDurationMs = (unsigned long)duration * 1000UL;
  pumpCommandId = id;
  sendAck(id, "ACCEPTED");
}

void handleConfig(JsonDocument &doc) {
  uint64_t now = doc["now"] | (uint64_t)0;
  if (now > 0) {
    clockOffsetMs = now - millis();
    clockSet = true;
  }
  int max = doc["maxDurationSec"] | 0;
  if (max >= 1 && max <= 300) {
    maxDurationSec = max;
    Serial.printf("max irrigation duration set to %d s\n", maxDurationSec);
  }
}

void onMessage(uint8_t *payload, size_t length) {
  JsonDocument doc;
  if (deserializeJson(doc, payload, length)) {
    Serial.println("invalid JSON from gateway");
    return;
  }
  String type = doc["type"] | "";
  if (type == "command") handleCommand(doc);
  else if (type == "config") handleConfig(doc);
}

void onWsEvent(WStype_t type, uint8_t *payload, size_t length) {
  switch (type) {
    case WStype_CONNECTED: {
      linked = true;
      Serial.println("connected to gateway");
      JsonDocument hello;
      hello["type"] = "hello";
      hello["deviceId"] = DEVICE_ID;
      hello["firmware"] = FIRMWARE_VERSION;
      send(hello);
      sendTelemetry();
      break;
    }
    case WStype_DISCONNECTED:
      if (linked) Serial.println("gateway connection lost");
      linked = false;
      break;
    case WStype_TEXT:
      onMessage(payload, length);
      break;
    default:
      break;
  }
}

void setup() {
  pinMode(PIN_PUMP, OUTPUT);
  digitalWrite(PIN_PUMP, LOW);
  pinMode(PIN_RESERVOIR, INPUT);
  Serial.begin(115200);
  dht.setup(PIN_DHT, DHTesp::DHT22);

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("WiFi");
  while (WiFi.status() != WL_CONNECTED) {
    delay(250);
    Serial.print(".");
  }
  Serial.println(" connected");

  ws.begin(GATEWAY_HOST, GATEWAY_PORT, GATEWAY_PATH);
  ws.onEvent(onWsEvent);
  ws.setReconnectInterval(5000);
  lastReservoirOk = reservoirOk();
}

void loop() {
  ws.loop();

  if (pumpOn && !reservoirOk()) {
    pumpOff();
    sendAck(pumpCommandId, "FAILED", "reservoir empty during irrigation");
  } else if (pumpOn && millis() - pumpStartedAt >= pumpDurationMs) {
    pumpOff();
    sendAck(pumpCommandId, "COMPLETED");
  }

  bool ok = reservoirOk();
  if (ok != lastReservoirOk) {
    lastReservoirOk = ok;
    sendLowWaterEvent(!ok);
    sendTelemetry();
  }

  if (millis() - lastTelemetry >= TELEMETRY_INTERVAL_MS) {
    lastTelemetry = millis();
    sendTelemetry();
  }
}
