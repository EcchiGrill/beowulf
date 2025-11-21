package com.beowulf.core.visitor;

import com.beowulf.core.db.DataSourceFactory;
import com.beowulf.core.user.AppUser;

import javax.sql.DataSource;
import java.sql.*;
import java.util.UUID;

public class ArchivePersistenceService {

    private final DataSource dataSource;

    public ArchivePersistenceService() {
        this.dataSource = DataSourceFactory.getDataSource();
    }

    public void persistOperation(ArchiveOperationContext ctx) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);

            AppUser user = ctx.getUser();
            upsertUser(connection, user);

            UUID checksumId = ctx.getChecksumId() != null
                    ? ctx.getChecksumId()
                    : UUID.randomUUID();

            insertChecksum(connection, checksumId,
                    ctx.getChecksumType(), ctx.getChecksumValue());
            ctx.setChecksumId(checksumId);

            UUID archiveId = ctx.getArchiveId() != null
                    ? ctx.getArchiveId()
                    : UUID.randomUUID();

            insertArchive(connection, archiveId, user.getId(), checksumId,
                    ctx.getFormat(), ctx.getCompression(),
                    ctx.getArchivePath(), ctx.getSizeBytes());
            ctx.setArchiveId(archiveId);

            insertArchiveLog(connection,
                    user.getId(), archiveId,
                    ctx.getOperation(),
                    ctx.getStatus(),
                    ctx.getDurationMs());

            connection.commit();
        } catch (SQLException error) {
            safeRollback(connection);
            throw new RuntimeException("Failed to persist archive operation", error);
        } finally {
            safeClose(connection);
        }
    }

    private void upsertUser(Connection connection, AppUser user) throws SQLException {
        String sql = """
                INSERT INTO app_user (id, username)
                VALUES (?, ?)
                ON CONFLICT (id) DO UPDATE
                    SET username = EXCLUDED.username
                """;

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setObject(1, user.getId());
            preparedStatement.setString(2, user.getUsername());
            preparedStatement.executeUpdate();
        }
    }

    private void insertChecksum(Connection connection,
            UUID id,
            String type,
            String value) throws SQLException {
        String sql = """
                INSERT INTO checksum (id, type, value)
                VALUES (?, ?, ?)
                """;

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setObject(1, id);
            preparedStatement.setString(2, type);
            preparedStatement.setString(3, value);
            preparedStatement.executeUpdate();
        }
    }

    private void insertArchive(Connection connection,
            UUID archiveId,
            UUID userId,
            UUID checksumId,
            String format,
            String compression,
            String path,
            long sizeBytes) throws SQLException {
        String sql = """
                INSERT INTO archive
                    (id, user_id, checksum_id, format, compression, path, size_bytes)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, archiveId);
            ps.setObject(2, userId);
            ps.setObject(3, checksumId);
            ps.setString(4, format);
            ps.setString(5, compression);
            ps.setString(6, path);
            ps.setLong(7, sizeBytes);
            ps.executeUpdate();
        }
    }

    private void insertArchiveLog(Connection connection,
            UUID userId,
            UUID archiveId,
            String operation,
            String status,
            long durationMs) throws SQLException {
        String sql = """
                INSERT INTO archive_log
                (user_id, archive_id, operation, status, duration_ms)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, userId);
            ps.setObject(2, archiveId);
            ps.setString(3, operation);
            ps.setString(4, status);
            ps.setLong(5, durationMs);
            ps.executeUpdate();
        }
    }

    private void safeRollback(Connection connection) {
        if (connection != null) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
            }
        }
    }

    private void safeClose(Connection connection) {
        if (connection != null) {
            try {
                connection.setAutoCommit(true);
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
