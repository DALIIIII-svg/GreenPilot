package tn.supcom.greenpilot.api.controllers.events;

import jakarta.inject.Qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** CDI qualifier from the course skeleton: where an inbound message came from. */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
public @interface MessageEvent {
    Type value();

    enum Type { WEBSOCKET, MQTT }
}
