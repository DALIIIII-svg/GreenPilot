package tn.supcom.greenpilot.api.boundaries;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/** REST: https://api.greenpilot.local/api/...   WebSocket: wss://api.greenpilot.local/ws */
@ApplicationPath("/api")
public class GreenPilotApplication extends Application {
}
