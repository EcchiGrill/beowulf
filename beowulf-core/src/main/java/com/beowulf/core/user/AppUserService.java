package com.beowulf.core.user;

import com.beowulf.core.db.DataSourceFactory;

import javax.sql.DataSource;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.Properties;
import java.util.UUID;

public class AppUserService {

    private static final String CONFIG_DIR = System.getProperty("user.home") + File.separator + ".beowulf";
    private static final String CONFIG_FILE = CONFIG_DIR + File.separator + "config.properties";
    private static final String KEY_USER_ID = "user.id";

    private final DataSource dataSource;

    public AppUserService() {
        this.dataSource = DataSourceFactory.getDataSource();
    }

    public AppUser resolveCurrentUser() {
        UUID userId = loadOrCreateUserId();
        AppUser user = findUserById(userId);
        if (user != null) {
            return user;
        }
        AppUser newUser = new AppUser();
        newUser.setId(userId);
        newUser.setUsername(System.getProperty("user.name", "unknown"));
        return insertUser(newUser);
    }

    private UUID loadOrCreateUserId() {
        try {
            Properties props = new Properties();
            Path path = Paths.get(CONFIG_FILE);

            if (Files.exists(path)) {
                try (InputStream inputStream = Files.newInputStream(path)) {
                    props.load(inputStream);
                }
                String idStr = props.getProperty(KEY_USER_ID);
                if (idStr != null && !idStr.isBlank()) {
                    return UUID.fromString(idStr.trim());
                }
            }

            Files.createDirectories(Paths.get(CONFIG_DIR));
            UUID newId = UUID.randomUUID();
            props.setProperty(KEY_USER_ID, newId.toString());
            try (OutputStream outputStream = Files.newOutputStream(path)) {
                props.store(outputStream, "Beowulf local user config");
            }
            return newId;
        } catch (IOException error) {
            throw new RuntimeException("Failed to load/create local user id", error);
        }
    }

    private AppUser findUserById(UUID id) {
        String sql = """
                SELECT id, username, created_at
                FROM app_user
                WHERE id = ?
                """;

        try (Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setObject(1, id);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    AppUser user = new AppUser();
                    user.setId((UUID) resultSet.getObject("id"));
                    user.setUsername(resultSet.getString("username"));
                    user.setCreatedAt(resultSet.getObject("created_at", OffsetDateTime.class));
                    return user;
                }
            }
            return null;
        } catch (SQLException error) {
            throw new RuntimeException("Failed to load app_user", error);
        }
    }

    private AppUser insertUser(AppUser user) {
        String sql = """
                INSERT INTO app_user (id, username)
                VALUES (?, ?)
                RETURNING created_at
                """;

        try (Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setObject(1, user.getId());
            preparedStatement.setString(2, user.getUsername());

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    user.setCreatedAt(resultSet.getObject("created_at", OffsetDateTime.class));
                }
            }
            return user;
        } catch (SQLException error) {
            throw new RuntimeException("Failed to insert app_user", error);
        }
    }
}
