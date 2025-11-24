package com.beowulf.core.facade;

import com.beowulf.core.db.DataSourceFactory;
import com.beowulf.core.model.ArchiveOperation;
import com.beowulf.core.model.ArchivePart;
import com.beowulf.core.user.AppUser;

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;
import java.util.UUID;

public class ArchivePersistenceService {

    private final DataSource dataSource;

    public ArchivePersistenceService() {
        this.dataSource = DataSourceFactory.getDataSource();
    }

    /**
     * Persist an archive operation.
     *
     * @param ctx the ArchiveOperation object to persist
     */

    public void persistOperation(ArchiveOperation ctx) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            connection.setAutoCommit(false);

            AppUser user = ctx.getUser();
            upsertUser(connection, user);

            UUID checksumId = ctx.getChecksumId();
            if (checksumId == null) {
                checksumId = UUID.randomUUID();
            }

            String checksumValue = ctx.getChecksumValue();
            if (checksumValue == null) {
                checksumValue = "";
            }

            insertChecksum(connection, checksumId,
                    ctx.getChecksumType(),
                    checksumValue);

            ctx.setChecksumId(checksumId);
            ctx.setChecksumValue(checksumValue);

            UUID archiveId = ctx.getArchiveId();
            if (archiveId == null) {
                archiveId = UUID.randomUUID();
                insertArchive(connection,
                        archiveId,
                        user.getId(),
                        checksumId,
                        ctx.getFormat(),
                        ctx.getCompression(),
                        ctx.getArchivePath(),
                        ctx.getSizeBytes());
            } else {
                updateArchive(connection,
                        archiveId,
                        user.getId(),
                        checksumId,
                        ctx.getFormat(),
                        ctx.getCompression(),
                        ctx.getArchivePath(),
                        ctx.getSizeBytes());
            }
            ctx.setArchiveId(archiveId);

            insertArchiveLog(connection,
                    user.getId(),
                    archiveId,
                    ctx.getOperation(),
                    ctx.getStatus(),
                    ctx.getTargetPath(),
                    ctx.getDurationMs());

            if (ctx.getParts() != null && !ctx.getParts().isEmpty()) {
                saveArchiveParts(archiveId, ctx.getParts());
            }

            connection.commit();

            connection.commit();
        } catch (SQLException error) {
            safeRollback(connection);
            throw new RuntimeException("Failed to persist archive operation", error);
        } finally {
            safeClose(connection);
        }
    }

    /**
     * Upsert a user.
     *
     * @param connection the database connection
     * @param user       the AppUser object to upsert
     */

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

    /**
     * Insert a checksum.
     *
     * @param connection the database connection
     * @param id         the ID of the checksum
     * @param type       the type of the checksum
     * @param value      the value of the checksum
     */

    private void insertChecksum(Connection connection,
            UUID id,
            String type,
            String value) throws SQLException {

        if (type == null || type.isBlank()) {
            type = "UNKNOWN";
        }
        if (value == null || value.isBlank()) {
            value = "N/A";
        }

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

    /**
     * Insert an archive.
     *
     * @param connection  the database connection
     * @param archiveId   the ID of the archive
     * @param userId      the ID of the user
     * @param checksumId  the ID of the checksum
     * @param format      the format of the archive
     * @param compression the compression of the archive
     * @param path        the path of the archive
     * @param sizeBytes   the size of the archive
     */

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

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setObject(1, archiveId);
            preparedStatement.setObject(2, userId);
            preparedStatement.setObject(3, checksumId);
            preparedStatement.setString(4, format);
            preparedStatement.setString(5, compression);
            preparedStatement.setString(6, path);
            preparedStatement.setLong(7, sizeBytes);
            preparedStatement.executeUpdate();
        }
    }

    /**
     * Update an archive.
     *
     * @param connection  the database connection
     * @param archiveId   the ID of the archive
     * @param userId      the ID of the user
     * @param checksumId  the ID of the checksum
     * @param format      the format of the archive
     * @param compression the compression of the archive
     * @param path        the path of the archive
     * @param sizeBytes   the size of the archive
     */

    private void updateArchive(Connection connection,
            UUID archiveId,
            UUID userId,
            UUID checksumId,
            String format,
            String compression,
            String path,
            long sizeBytes) throws SQLException {
        String sql = """
                UPDATE archive
                SET checksum_id = ?,
                    format      = ?,
                    compression = ?,
                    path        = ?,
                    size_bytes  = ?
                WHERE id = ? AND user_id = ?
                """;

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setObject(1, checksumId);
            preparedStatement.setString(2, format);
            preparedStatement.setString(3, compression);
            preparedStatement.setString(4, path);
            preparedStatement.setLong(5, sizeBytes);
            preparedStatement.setObject(6, archiveId);
            preparedStatement.setObject(7, userId);
            preparedStatement.executeUpdate();
        }
    }

    /**
     * Insert an archive log.
     *
     * @param connection the database connection
     * @param userId     the ID of the user
     * @param archiveId  the ID of the archive
     * @param operation  the operation to log
     * @param status     the status of the operation
     * @param targetPath the target path of the operation
     * @param durationMs the duration of the operation
     */

    private void insertArchiveLog(Connection connection,
            UUID userId,
            UUID archiveId,
            String operation,
            String status,
            String targetPath,
            long durationMs) throws SQLException {
        String sql = """
                INSERT INTO archive_log
                    (user_id, archive_id, operation, status, target_path, duration_ms)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setObject(1, userId);
            preparedStatement.setObject(2, archiveId);
            preparedStatement.setString(3, operation);
            preparedStatement.setString(4, status);
            preparedStatement.setString(5, targetPath);
            preparedStatement.setLong(6, durationMs);
            preparedStatement.executeUpdate();
        }
    }

    public void saveArchiveParts(UUID archiveId, List<ArchivePart> parts) {
        if (parts == null || parts.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO archive_part (archive_id, part_index, path, size_bytes)
                VALUES (?, ?, ?, ?)
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {

            for (ArchivePart part : parts) {
                ps.setObject(1, archiveId);
                ps.setInt(2, part.getPartIndex());
                ps.setString(3, part.getPath());
                ps.setLong(4, part.getSizeBytes());
                ps.addBatch();
            }

            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save archive parts", e);
        }
    }

    /**
     * Rollback a connection.
     *
     * @param connection the database connection
     */

    private void safeRollback(Connection connection) {
        if (connection != null) {
            try {
                connection.rollback();
            } catch (SQLException error) {
            }
        }
    }

    /**
     * Close a connection.
     *
     * @param connection the database connection
     */

    private void safeClose(Connection connection) {
        if (connection != null) {
            try {
                connection.setAutoCommit(true);
                connection.close();
            } catch (SQLException error) {
            }
        }
    }
}
