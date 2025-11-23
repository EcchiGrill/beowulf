package com.beowulf.core.facade;

import com.beowulf.core.db.DataSourceFactory;
import com.beowulf.core.visitor.ArchiveLog;

import javax.sql.DataSource;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ArchiveLogService {

    private final DataSource dataSource;

    public ArchiveLogService() {
        this.dataSource = DataSourceFactory.getDataSource();
    }

    /**
     * Find the most recent logs for a given user.
     *
     * @param userId the ID of the user
     * @param limit  the maximum number of logs to return
     * @return a list of ArchiveLog objects
     */

    public List<ArchiveLog> findRecentLogs(UUID userId, int limit) {
        String sql = """
                SELECT
                    l.id             AS log_id,
                    l.created_at     AS log_created_at,
                    l.operation,
                    l.status,
                    l.duration_ms,
                    l.target_path    AS target_path,
                    a.path           AS archive_path,
                    a.format,
                    a.compression,
                    a.size_bytes,
                    c.type           AS checksum_type,
                    c.value          AS checksum_value
                FROM archive_log l
                JOIN archive a
                  ON l.archive_id = a.id
                JOIN checksum c
                  ON a.checksum_id = c.id
                WHERE l.user_id = ?
                ORDER BY l.created_at DESC
                LIMIT ?
                """;

        List<ArchiveLog> result = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setObject(1, userId);
            preparedStatement.setInt(2, limit);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    ArchiveLog entry = new ArchiveLog();
                    entry.setId(resultSet.getLong("log_id"));
                    entry.setCreatedAt(resultSet.getObject("log_created_at", OffsetDateTime.class));
                    entry.setOperation(resultSet.getString("operation"));
                    entry.setStatus(resultSet.getString("status"));
                    entry.setDurationMs(resultSet.getLong("duration_ms"));

                    entry.setArchivePath(resultSet.getString("archive_path"));
                    entry.setTargetPath(resultSet.getString("target_path"));

                    entry.setFormat(resultSet.getString("format"));
                    entry.setCompression(resultSet.getString("compression"));
                    entry.setSizeBytes(resultSet.getLong("size_bytes"));
                    entry.setChecksumType(resultSet.getString("checksum_type"));
                    entry.setChecksumValue(resultSet.getString("checksum_value"));

                    result.add(entry);
                }
            }

        } catch (SQLException error) {
            throw new RuntimeException("Failed to load archive logs", error);
        }

        return result;
    }
}
