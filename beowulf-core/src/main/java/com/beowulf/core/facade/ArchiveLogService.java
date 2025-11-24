package com.beowulf.core.facade;

import com.beowulf.core.db.DataSourceFactory;
import com.beowulf.core.model.ArchiveLog;
import com.beowulf.core.model.ArchivePart;

import javax.sql.DataSource;
import java.sql.*;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ArchiveLogService {

    private final DataSource dataSource;

    public ArchiveLogService() {
        this.dataSource = DataSourceFactory.getDataSource();
    }

    public List<ArchivePart> findArchiveParts(UUID archiveId) {
        String sql = """
                SELECT id, archive_id, part_index, path, size_bytes, created_at
                FROM archive_part
                WHERE archive_id = ?
                ORDER BY part_index
                """;

        List<ArchivePart> parts = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setObject(1, archiveId);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    ArchivePart part = new ArchivePart();
                    part.setPartIndex(resultSet.getInt("part_index"));
                    part.setPath(resultSet.getString("path"));
                    part.setSizeBytes(resultSet.getLong("size_bytes"));
                    parts.add(part);
                }
            }
        } catch (SQLException error) {
            throw new RuntimeException("Failed to load archive parts", error);
        }

        return parts;
    }

    public List<ArchiveLog> findRecentLogs(UUID userId, int limit) {
        String sql = """
                SELECT
                    al.id                      AS log_id,
                    al.user_id                 AS user_id,
                    al.archive_id              AS archive_id,
                    al.operation,
                    al.status,
                    al.target_path,
                    al.duration_ms,
                    al.created_at,

                    a.path                     AS archive_path,
                    a.format                   AS format,
                    a.compression              AS compression,
                    a.size_bytes               AS size_bytes,

                    -- NEW: агрегаты по archive_part
                    COALESCE(parts.parts_count, 0)  AS split_parts_count,
                    COALESCE(parts.total_size, 0)   AS split_total_size,
                    parts.first_part_path           AS split_first_path
                FROM archive_log al
                JOIN archive a ON a.id = al.archive_id
                LEFT JOIN (
                SELECT
                        ap.archive_id,
                        COUNT(*)            AS parts_count,
                        SUM(ap.size_bytes)  AS total_size,
                        MIN(ap.path)        AS first_part_path
                    FROM archive_part ap
                    GROUP BY ap.archive_id
                ) parts ON parts.archive_id = a.id
                WHERE al.user_id = ?
                ORDER BY al.created_at DESC
                LIMIT ?
                """;

        List<ArchiveLog> logs = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(sql)) {

            preparedStatement.setObject(1, userId);
            preparedStatement.setInt(2, limit);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    ArchiveLog log = new ArchiveLog();

                    log.setId(resultSet.getLong("log_id"));
                    log.setUserId((UUID) resultSet.getObject("user_id"));
                    log.setArchiveId((UUID) resultSet.getObject("archive_id"));
                    log.setOperation(resultSet.getString("operation"));
                    log.setStatus(resultSet.getString("status"));
                    log.setTargetPath(resultSet.getString("target_path"));
                    log.setDurationMs(resultSet.getLong("duration_ms"));

                    Timestamp ts = resultSet.getTimestamp("created_at");
                    if (ts != null) {
                        log.setCreatedAt(ts.toInstant().atOffset(ZoneOffset.UTC));
                    }

                    log.setArchivePath(resultSet.getString("archive_path"));
                    log.setFormat(resultSet.getString("format"));
                    log.setCompression(resultSet.getString("compression"));
                    log.setSizeBytes(resultSet.getLong("size_bytes"));

                    long partsCount = resultSet.getLong("split_parts_count");
                    if (resultSet.wasNull()) {
                        log.setSplitPartsCount(null);
                    } else {
                        log.setSplitPartsCount(partsCount);
                    }

                    long totalSize = resultSet.getLong("split_total_size");
                    if (resultSet.wasNull()) {
                        log.setSplitTotalSize(null);
                    } else {
                        log.setSplitTotalSize(totalSize);
                    }

                    log.setSplitFirstPath(resultSet.getString("split_first_path"));

                    logs.add(log);
                }
            }

        } catch (SQLException error) {
            throw new RuntimeException("Failed to load archive logs", error);
        }

        return logs;
    }
}
