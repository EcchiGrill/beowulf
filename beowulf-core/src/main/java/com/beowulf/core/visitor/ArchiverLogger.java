package com.beowulf.core.visitor;

import com.beowulf.core.interfaces.Archiver;
import com.beowulf.core.user.AppUser;
import com.beowulf.core.user.AppUserService;
import com.beowulf.core.utils.ArchiveMetadataUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class ArchiverLogger implements Archiver {

    private final Archiver delegate;
    private final AppUserService identityProvider;
    private final ArchiveVisitor visitor;

    public ArchiverLogger(Archiver delegate,
            AppUserService identityProvider,
            ArchiveVisitor visitor) {
        this.delegate = delegate;
        this.identityProvider = identityProvider;
        this.visitor = visitor;
    }

    @Override
    public void compress(Path sourceDir, Path targetArchive) throws IOException {
        AppUser user = identityProvider.resolveCurrentUser();
        long started = System.nanoTime();

        ArchiveMetadataUtil.ArchiveMeta meta = ArchiveMetadataUtil.detect(targetArchive);

        ArchiveOperation ctx = new ArchiveOperation();
        ctx.setUser(user);
        ctx.setOperation("COMPRESS");
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
        ctx.setOperation("DECOMPRESS");
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
