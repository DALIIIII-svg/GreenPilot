#pragma once

#define WIFI_SSID "Wokwi-GUEST"
#define WIFI_PASSWORD ""

// Node-RED gateway. In the Wokwi simulator, host.wokwi.internal is the PC running the simulation.
#define GATEWAY_HOST "host.wokwi.internal"
#define GATEWAY_PORT 1880
#define GATEWAY_PATH "/ws/esp32"

#define DEVICE_ID "esp32-node"
#define FIRMWARE_VERSION "1.0.0"

// F01 asks for 60 s; shorter here so the demo reacts quickly.
#define TELEMETRY_INTERVAL_MS 15000

#define PIN_SOIL_A 34
#define PIN_SOIL_B 35
#define PIN_DHT 15
#define PIN_RESERVOIR 27
#define PIN_PUMP 26

// Raw ADC values of a dry and a wet probe, measured once per probe (capacitive probes read lower when wet).
// The potentiometers of the simulator go from 0 to 4095, so 0 = dry and 4095 = wet.
#define SOIL_A_DRY 0
#define SOIL_A_WET 4095
#define SOIL_B_DRY 0
#define SOIL_B_WET 4095

#define DEFAULT_MAX_DURATION_SEC 30
