package tn.supcom.greenpilot.api.controllers.managers;

import com.mongodb.client.model.Indexes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Startup;
import jakarta.inject.Inject;
import tn.supcom.greenpilot.api.entities.Command;
import tn.supcom.greenpilot.api.entities.DiseaseEvent;
import tn.supcom.greenpilot.api.entities.Forecast;
import tn.supcom.greenpilot.api.entities.Incident;
import tn.supcom.greenpilot.api.entities.Inspection;
import tn.supcom.greenpilot.api.entities.Measurement;
import tn.supcom.greenpilot.api.entities.Site;
import tn.supcom.greenpilot.core.controllers.MongoAccess;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Creates the MongoDB indexes the queries rely on (JNoSQL does not create indexes). */
@ApplicationScoped
public class Bootstrap {

    private static final Logger LOG = Logger.getLogger(Bootstrap.class.getName());

    @Inject
    MongoAccess mongo;

    void onStart(@Observes Startup event) {
        try {
            mongo.collection(Site.COLLECTION).createIndex(Indexes.geo2dsphere("location"));     // F08 proximity search
            mongo.collection(Site.COLLECTION).createIndex(Indexes.ascending("gatewayId"));
            mongo.collection(Measurement.COLLECTION).createIndex(Indexes.compoundIndex(
                    Indexes.ascending("siteId", "type", "zoneId"), Indexes.descending("ts")));
            mongo.collection(Forecast.COLLECTION).createIndex(Indexes.compoundIndex(
                    Indexes.ascending("siteId", "zoneId"), Indexes.descending("ts")));
            mongo.collection(Inspection.COLLECTION).createIndex(Indexes.compoundIndex(
                    Indexes.ascending("siteId", "plantId"), Indexes.descending("ts")));
            mongo.collection(DiseaseEvent.COLLECTION).createIndex(Indexes.ascending("imageKey"));
            mongo.collection(Incident.COLLECTION).createIndex(Indexes.ascending("siteId", "plantId", "status"));
            mongo.collection(Command.COLLECTION).createIndex(Indexes.compoundIndex(
                    Indexes.ascending("siteId", "type"), Indexes.descending("issuedAt")));
            mongo.collection(Command.COLLECTION).createIndex(Indexes.ascending("status", "expiresAt"));
            mongo.collection("images.files").createIndex(Indexes.ascending("metadata.imageKey"));
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Could not create MongoDB indexes - is MongoDB running?", e);
        }
    }
}
