package com.beowulf.core.db;

import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

public final class DatabaseMigrations {

    private DatabaseMigrations() {
    }

    public static void migrate() {
        DataSource dataSource = Database.getDataSource();

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();

        flyway.migrate();
    }
}
