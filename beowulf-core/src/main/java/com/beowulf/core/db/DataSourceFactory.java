package com.beowulf.core.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public final class DataSourceFactory {

    private static HikariDataSource dataSource;

    private DataSourceFactory() {
    }

    public static DataSource getDataSource() {
        if (dataSource == null) {
            HikariConfig config = new HikariConfig();

            config.setJdbcUrl("jdbc:postgresql://postgres:5432/beowulf_db");

            config.setUsername("beowulf");
            config.setPassword("beowulf");

            config.setMaximumPoolSize(10);
            config.setMinimumIdle(1);
            config.setPoolName("BeowulfHikariPool");

            dataSource = new HikariDataSource(config);
        }
        return dataSource;
    }

    public static void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
