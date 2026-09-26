package emu.grasscutter.database;

import static emu.grasscutter.config.Configuration.DATABASE;

import com.mongodb.MongoCommandException;
import com.mongodb.client.*;
import dev.morphia.*;
import dev.morphia.annotations.Entity;
import dev.morphia.mapping.*;
import dev.morphia.query.experimental.filters.Filters;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.game.Account;

public final class DatabaseManager {
    private static Datastore gameDatastore;
    private static Datastore dispatchDatastore;

    public static Datastore getGameDatastore() {
        return gameDatastore;
    }

    public static Datastore getAccountDatastore() {
        if (Grasscutter.getRunMode() == ServerRunMode.HYBRID) return gameDatastore;
        else return dispatchDatastore;
    }

    public static MongoDatabase getGameDatabase() {
        return getGameDatastore().getDatabase();
    }

    public static void initialize() {
        MongoClient gameMongoClient = MongoClients.create(DATABASE.game.connectionUri);

        MapperOptions mapperOptions =
                MapperOptions.builder().storeEmpties(true).storeNulls(false).build();

        gameDatastore =
                Morphia.createDatastore(gameMongoClient, DATABASE.game.collection, mapperOptions);

        var entities =
                Grasscutter.reflector.getTypesAnnotatedWith(Entity.class).stream()
                        .filter(
                                cls -> {
                                    Entity e = cls.getAnnotation(Entity.class);
                                    return e != null && !e.value().equals(Mapper.IGNORED_FIELDNAME);
                                })
                        .toArray(Class<?>[]::new);

        gameDatastore.getMapper().map(entities);

        ensureIndexes(gameDatastore);

        if (Grasscutter.getRunMode() != ServerRunMode.HYBRID) {
            MongoClient dispatchMongoClient = MongoClients.create(DATABASE.server.connectionUri);

            dispatchDatastore =
                    Morphia.createDatastore(dispatchMongoClient, DATABASE.server.collection, mapperOptions);
            dispatchDatastore.getMapper().map(new Class<?>[] {DatabaseCounter.class, Account.class});

            ensureIndexes(dispatchDatastore);
        }
    }

    private static void ensureIndexes(Datastore datastore) {
        try {
            datastore.ensureIndexes();
        } catch (MongoCommandException e) {
            Grasscutter.getLogger().info("Mongo index error: ", e);
            if (e.getCode() == 85) {
                MongoIterable<String> collections = datastore.getDatabase().listCollectionNames();
                for (String name : collections) {
                    datastore.getDatabase().getCollection(name).dropIndexes();
                }
                datastore.ensureIndexes();
            }
        }
    }

    public static synchronized int getNextId(Class<?> c) {
        DatabaseCounter counter =
                getGameDatastore()
                        .find(DatabaseCounter.class)
                        .filter(Filters.eq("_id", c.getSimpleName()))
                        .first();
        if (counter == null) {
            counter = new DatabaseCounter(c.getSimpleName());
        }

        try {
            return counter.getNextId();
        } finally {
            DatabaseHelper.saveGameAsync(counter);
        }
    }

    public static synchronized int getNextId(Object o) {
        return getNextId(o.getClass());
    }
}
