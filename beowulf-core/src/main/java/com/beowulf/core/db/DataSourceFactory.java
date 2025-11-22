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

            String url = System.getenv("BEOWULF_DB_URL");
            if (url == null || url.isBlank()) {
                url = "jdbc:postgresql://localhost:5432/beowulf_db";
            }
            config.setJdbcUrl(url);

            String user = System.getenv("BEOWULF_DB_USER");
            if (user == null || user.isBlank()) {
                user = "beowulf";
            }
            config.setUsername(user);

            String pass = System.getenv("BEOWULF_DB_PASS");
            if (pass == null || pass.isBlank()) {
                pass = "beowulf";
            }
            config.setPassword(pass);

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
