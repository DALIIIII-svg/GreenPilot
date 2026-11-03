package tn.supcom.greenpilot.core.controllers;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tn.supcom.greenpilot.core.boundaries.ApiError;

import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.inc;

/**
 * Direct MongoDB access for what Jakarta NoSQL / Jakarta Data cannot express:
 * index creation, geospatial ($geoNear) and aggregation queries, GridFS files, atomic version checks.
 * Everything else goes through repositories / Template. Same connection settings as JNoSQL.
 */
@ApplicationScoped
public class MongoAccess {

    @Inject
    @ConfigProperty(name = "jnosql.mongodb.url", defaultValue = "mongodb://localhost:27017")
    String url;

    @Inject
    @ConfigProperty(name = "jnosql.document.database")
    String databaseName;

    private MongoClient client;
    private MongoDatabase database;
    private GridFSBucket images;

    @PostConstruct
    void init() {
        client = MongoClients.create(MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(url))
                .applyToClusterSettings(b -> b.serverSelectionTimeout(5, TimeUnit.SECONDS))
                .build());
        database = client.getDatabase(databaseName);
        images = GridFSBuckets.create(database, "images");
    }

    @PreDestroy
    void close() {
        if (client != null) client.close();
    }

    public MongoDatabase database() { return database; }

    public MongoCollection<Document> collection(String name) { return database.getCollection(name); }

    public GridFSBucket images() { return images; }

    public boolean ping() {
        try {
            database.runCommand(new Document("ping", 1));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Optimistic locking for MongoDB (JPA does this with @Version; Jakarta NoSQL does not):
     * atomically moves the stored version from expected to expected+1. If another request changed the
     * document first, nothing matches and we answer 412. The caller then saves the entity with version+1.
     */
    public void claimVersion(String collection, String id, long expectedVersion) {
        long matched = collection(collection)
                .updateOne(and(eq("_id", id), eq("version", expectedVersion)), inc("version", 1L))
                .getMatchedCount();
        if (matched == 0) {
            throw ApiError.preconditionFailed("Modified by someone else in the meantime - reload and retry");
        }
    }
}
