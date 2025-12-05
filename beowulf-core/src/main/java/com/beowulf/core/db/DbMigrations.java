package com.beowulf.core.db;

import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

public final class DbMigrations {

    private DbMigrations() {
    }

    public static void migrate() {
        DataSource dataSource = DataSourceFactory.getDataSource();

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();

        flyway.migrate();
    }
}
