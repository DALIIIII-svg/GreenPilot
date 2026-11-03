package tn.supcom.greenpilot.api.boundaries;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tn.supcom.greenpilot.api.controllers.managers.MqttManager;
import tn.supcom.greenpilot.core.controllers.MongoAccess;

import java.time.Instant;

/** GET /api/health - public. */
@Path("/health")
@RequestScoped
public class HealthResource {

    @Inject MongoAccess mongo;
    @Inject MqttManager mqtt;

    @GET
    public Response health() {
        boolean db = mongo.ping();
        boolean broker = mqtt.isConnected();
        return Response.ok(Json.createObjectBuilder()
                .add("status", db && broker ? "UP" : "DEGRADED")
                .add("mongo", db).add("mqtt", broker).add("time", Instant.now().toString())
                .build().toString(), MediaType.APPLICATION_JSON_TYPE).build();
    }
}
