package com.beowulf.core.decorator;

import com.beowulf.core.interfaces.Archiver;
import com.beowulf.core.service.ArchivePersistenceService;
import com.beowulf.core.user.AppUser;
import com.beowulf.core.user.AppUserService;
import com.beowulf.core.utils.ArchiveMetadataUtil;
import com.beowulf.core.visitor.ArchiveOperation;
import com.beowulf.core.visitor.ArchiveVisitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class ArchiverLogger implements Archiver {

    private final Archiver delegate;
    private final AppUserService identityProvider;
    private final ArchiveVisitor visitor;
    private final String operationOverride;

    public ArchiverLogger(Archiver delegate,
            AppUserService identityProvider,
            ArchiveVisitor visitor) {
        this(delegate, identityProvider, visitor, null);
    }

    public ArchiverLogger(Archiver delegate,
            AppUserService identityProvider,
            ArchivePersistenceService persistenceService) {
        this(delegate, identityProvider, new ArchiveVisitor(persistenceService), null);
    }

    public ArchiverLogger(Archiver delegate,
            AppUserService identityProvider,
            ArchiveVisitor visitor,
            String operationOverride) {
        this.delegate = delegate;
        this.identityProvider = identityProvider;
        this.visitor = visitor;
        this.operationOverride = operationOverride;
    }

    @Override
    public void compress(Path sourceDir, Path targetArchive) throws IOException {
        AppUser user = identityProvider.resolveCurrentUser();
        long started = System.nanoTime();

        ArchiveMetadataUtil.ArchiveMeta meta = ArchiveMetadataUtil.detect(targetArchive);

        ArchiveOperation ctx = new ArchiveOperation();
        ctx.setUser(user);
        ctx.setOperation(operationOverride != null ? operationOverride : "COMPRESS");
        ctx.setArchivePath(targetArchive.toAbsolutePath().toString());
        ctx.setTargetPath(null);
        ctx.setFormat(meta.format());
        ctx.setCompression(meta.compression());
        ctx.setChecksumType("SHA256");

        try {
            delegate.compress(sourceDir, targetArchive);

            ctx.setStatus("SUCCESS");
            ctx.setSizeBytes(Files.size(targetArchive));
            ctx.setChecksumValue(ArchiveMetadataUtil.sha256(targetArchive));
        } catch (IOException error) {
            ctx.setStatus("FAILED");
            ctx.setChecksumValue(null);
            ctx.setSizeBytes(0L);
            throw error;
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            ctx.setDurationMs(durationMs);
            visitor.visit(ctx);
        }
    }

    @Override
    public void decompress(Path archive, Path targetDir) throws IOException {
        AppUser user = identityProvider.resolveCurrentUser();
        long started = System.nanoTime();

        ArchiveMetadataUtil.ArchiveMeta meta = ArchiveMetadataUtil.detect(archive);

        ArchiveOperation ctx = new ArchiveOperation();
        ctx.setUser(user);
        ctx.setOperation(operationOverride != null ? operationOverride : "DECOMPRESS");
        ctx.setArchivePath(archive.toAbsolutePath().toString());
        ctx.setTargetPath(targetDir.toAbsolutePath().toString());
        ctx.setFormat(meta.format());
        ctx.setCompression(meta.compression());
        ctx.setChecksumType("SHA256");

        try {
            delegate.decompress(archive, targetDir);

            ctx.setStatus("SUCCESS");
            ctx.setSizeBytes(Files.size(archive));
            ctx.setChecksumValue(ArchiveMetadataUtil.sha256(archive));
        } catch (IOException error) {
            ctx.setStatus("FAILED");
            ctx.setChecksumValue(null);
            ctx.setSizeBytes(0L);
            throw error;
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            ctx.setDurationMs(durationMs);
            visitor.visit(ctx);
        }
    }

    @Override
    public String getName() {
        return delegate.getName();
    }
}
