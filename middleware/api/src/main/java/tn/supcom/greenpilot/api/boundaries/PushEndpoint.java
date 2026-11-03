package tn.supcom.greenpilot.api.boundaries;

import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import tn.supcom.greenpilot.api.controllers.managers.WebSocketManager;

/** WebSocket boundary: ws://localhost:8080/greenpilot/ws - all the logic is in WebSocketManager. */
@ServerEndpoint("/ws")
public class PushEndpoint {

    @Inject
    WebSocketManager manager;

    @OnOpen
    public void open(Session session) { manager.opened(session); }

    @OnMessage
    public void message(Session session, String text) { manager.received(session, text); }

    @OnClose
    public void close(Session session) { manager.closed(session); }

    @OnError
    public void error(Session session, Throwable error) { manager.closed(session); }
}
