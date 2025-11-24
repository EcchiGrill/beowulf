package com.beowulf.core.facade;

import com.beowulf.core.db.DataSourceFactory;
import com.beowulf.core.factory.ArchiverFactory;
import com.beowulf.core.interfaces.Archiver;
import com.beowulf.core.model.ArchiveOperation;
import com.beowulf.core.model.ArchiveVisitor;
import com.beowulf.core.user.AppUser;
import com.beowulf.core.user.AppUserService;
import com.beowulf.core.utils.ArchiveMetadataUtil;
import com.beowulf.core.utils.FileTreeUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ArchiveEditService {

    private final ArchiverFactory archiverFactory;
    private final AppUserService identityProvider;
    private final ArchiveVisitor persistVisitor;
    private final DataSource dataSource;

    public ArchiveEditService(ArchiverFactory archiverFactory,
            AppUserService identityProvider,
            ArchiveVisitor persistVisitor) {
        this.archiverFactory = archiverFactory;
        this.identityProvider = identityProvider;
        this.persistVisitor = persistVisitor;
        this.dataSource = DataSourceFactory.getDataSource();
    }

    /**
     * Delete an entry (file or folder) from the working directory of the archive.
     *
     * @param workingDir   root of the temporary directory for this archive
     * @param relativePath path inside the archive
     */
    public void deleteEntry(Path workingDir, String relativePath) throws IOException {
        Path target = workingDir.resolve(relativePath).normalize();

        if (!target.startsWith(workingDir)) {
            throw new IOException("Invalid path (escape from working directory): " + relativePath);
        }

        if (!Files.exists(target)) {
            return;
        }

        if (Files.isDirectory(target)) {
            FileTreeUtils.deleteRecursively(target);
        } else {
            Files.delete(target);
        }
    }

    /**
     * Recreate (or create) an archive from the contents of sourceDir.
     * Used for editing the archive in GUI.
     *
     * @param sourceDir     temporary folder with the updated content
     * @param targetArchive final archive file
     */
    public void saveArchive(Path sourceDir, Path targetArchive) throws IOException {
        Objects.requireNonNull(sourceDir, "sourceDir");
        Objects.requireNonNull(targetArchive, "targetArchive");

        AppUser user = identityProvider.resolveCurrentUser();
        Archiver archiver = archiverFactory.getArchiver(targetArchive);

        long started = System.nanoTime();
        String status = "SUCCESS";
        long sizeBytes = 0L;
        String checksumValue = null;

        try {
            archiver.compress(sourceDir, targetArchive);
            sizeBytes = Files.size(targetArchive);
            checksumValue = ArchiveMetadataUtil.sha256(targetArchive);
        } catch (IOException e) {
            status = "FAILED";
            throw e;
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            ArchiveMetadataUtil.ArchiveMeta meta = ArchiveMetadataUtil.detect(targetArchive);

            ArchiveOperation ctx = new ArchiveOperation();
            ctx.setUser(user);
            ctx.setOperation("UPDATE");
            ctx.setStatus(status);
            ctx.setArchivePath(targetArchive.toAbsolutePath().toString());
            ctx.setTargetPath(null);
            ctx.setFormat(meta.format());
            ctx.setCompression(meta.compression());
            ctx.setChecksumType("SHA256");
            ctx.setChecksumValue(checksumValue);
            ctx.setSizeBytes(sizeBytes);
            ctx.setDurationMs(durationMs);

            try (Connection connection = dataSource.getConnection()) {
                UUID existingId = findArchiveId(connection, user.getId(), ctx.getArchivePath());
                ctx.setArchiveId(existingId);
            } catch (SQLException error) {
            }

            persistVisitor.visit(ctx);
        }
    }

    /**
     * Find the ID of an archive for a given user and path.
     *
     * @param connection  the database connection
     * @param userId      the ID of the user
     * @param archivePath the path of the archive
     * @return the ID of the archive
     */

    private UUID findArchiveId(Connection connection, UUID userId, String archivePath) throws SQLException {
        String sql = """
                SELECT id
                FROM archive
                WHERE user_id = ? AND path = ?
                ORDER BY created_at DESC
                LIMIT 1
                """;

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setObject(1, userId);
            preparedStatement.setString(2, archivePath);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return (UUID) resultSet.getObject("id");
                }
                return null;
            }
        }
    }
}
