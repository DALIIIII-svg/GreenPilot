package tn.supcom.greenpilot.api.controllers.managers;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Session;
import tn.supcom.greenpilot.api.controllers.events.PushNotification;
import tn.supcom.greenpilot.core.security.AuthenticatedUser;
import tn.supcom.greenpilot.core.security.Scopes;
import tn.supcom.greenpilot.core.security.TokenVerifier;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Live push to the PWAs (replaces 10 s polling). Protocol on ws(s)://host/greenpilot/ws :
 *   client -> {"type":"auth","token":"<access token>"}   (browsers cannot set headers on a WebSocket,
 *                                                         and a token in the URL would end up in logs)
 *   server -> {"type":"auth_ok"}, then {"siteId","type","data"} for every event of an accessible site.
 * Unauthenticated sessions are closed after 10 s.
 */
@ApplicationScoped
public class WebSocketManager {

    private static final Logger LOG = Logger.getLogger(WebSocketManager.class.getName());
    private static final String USER = "gp.user";
    private static final String SITES = "gp.sites";
    private static final String ALL = "*";

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Jsonb jsonb = JsonbBuilder.create();

    @Inject TokenVerifier verifier;
    @Inject AccessManager access;

    @Resource(lookup = "java:comp/DefaultManagedScheduledExecutorService")
    ManagedScheduledExecutorService scheduler;

    public void opened(Session session) {
        sessions.put(session.getId(), session);
        scheduler.schedule(() -> {
            if (session.isOpen() && !session.getUserProperties().containsKey(USER)) close(session, "Authentication timeout");
        }, 10, TimeUnit.SECONDS);
    }

    public void received(Session session, String text) {
        JsonObject msg;
        try (JsonReader r = Json.createReader(new StringReader(text))) {
            msg = r.readObject();
        } catch (RuntimeException e) {
            close(session, "Invalid message");
            return;
        }
        if (!"auth".equals(msg.getString("type", ""))) return;
        try {
            AuthenticatedUser user = verifier.verify(msg.getString("token", ""));
            if (!user.hasScope(Scopes.READ)) throw new SecurityException("Token lacks scope " + Scopes.READ);
            Set<String> sites = access.accessibleSites(user);
            session.getUserProperties().put(USER, user);
            session.getUserProperties().put(SITES, sites == null ? Set.of(ALL) : Set.copyOf(sites));
            send(session, "{\"type\":\"auth_ok\"}");
        } catch (SecurityException e) {
            close(session, "Authentication failed");
        }
    }

    public void closed(Session session) {
        sessions.remove(session.getId());
    }

    void onPush(@ObservesAsync PushNotification notification) {
        String json = jsonb.toJson(notification);
        for (Session s : sessions.values()) {
            if (s.getUserProperties().get(SITES) instanceof Set<?> sites
                    && (sites.contains(ALL) || sites.contains(notification.siteId()))) {
                send(s, json);
            }
        }
    }

    private void send(Session session, String json) {
        if (!session.isOpen()) return;
        synchronized (session) { // one write at a time per session
            try {
                session.getBasicRemote().sendText(json);
            } catch (IOException e) {
                LOG.fine("WebSocket send failed: " + e.getMessage());
            }
        }
    }

    private void close(Session session, String reason) {
        sessions.remove(session.getId());
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, reason));
        } catch (IOException ignored) {
        }
    }

    @PreDestroy
    void shutdown() {
        try {
            jsonb.close();
        } catch (Exception ignored) {
        }
    }
}
