package tn.supcom.greenpilot.api.boundaries;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/** REST: http://localhost:8080/greenpilot/api/...   WebSocket: ws://localhost:8080/greenpilot/ws */
@ApplicationPath("/api")
public class GreenPilotApplication extends Application {
}
