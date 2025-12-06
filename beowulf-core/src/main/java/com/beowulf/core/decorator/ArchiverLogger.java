package com.beowulf.core.decorator;

import com.beowulf.core.interfaces.Archiver;
import com.beowulf.core.model.ArchiveOperation;
import com.beowulf.core.service.ArchivePersistenceService;
import com.beowulf.core.user.AppUser;
import com.beowulf.core.user.AppUserService;
import com.beowulf.core.utils.ArchiveMetadataUtil;
import com.beowulf.core.visitor.ArchiveVisitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class ArchiverLogger implements Archiver {

    private final Archiver delegate;
    private final AppUserService userService;
    private final ArchiveVisitor visitor;

    public ArchiverLogger(Archiver delegate) {
        this.delegate = delegate;
        this.userService = new AppUserService();
        this.visitor = new ArchiveVisitor(new ArchivePersistenceService());
    }

    @Override
    public void compress(Path sourceDir, Path targetArchive) throws IOException {
        AppUser user = userService.resolveCurrentUser();
        long started = System.nanoTime();

        var meta = ArchiveMetadataUtil.detect(targetArchive);

        ArchiveOperation ctx = new ArchiveOperation();
        ctx.setUser(user);
        ctx.setOperation("COMPRESS");
        ctx.setArchivePath(targetArchive.toString());
        ctx.setFormat(meta.format());
        ctx.setCompression(meta.compression());
        ctx.setChecksumType("SHA256");

        try {
            delegate.compress(sourceDir, targetArchive);

            ctx.setStatus("SUCCESS");
            ctx.setSizeBytes(Files.size(targetArchive));
            ctx.setChecksumValue(ArchiveMetadataUtil.sha256(targetArchive));
        } catch (IOException e) {
            ctx.setStatus("FAILED");
            throw e;
        } finally {
            ctx.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            visitor.visit(ctx);
        }
    }

    @Override
    public void decompress(Path archive, Path targetDir) throws IOException {
        AppUser user = userService.resolveCurrentUser();
        long started = System.nanoTime();

        var meta = ArchiveMetadataUtil.detect(archive);

        ArchiveOperation ctx = new ArchiveOperation();
        ctx.setUser(user);
        ctx.setOperation("DECOMPRESS");
        ctx.setArchivePath(archive.toString());
        ctx.setTargetPath(targetDir.toString());
        ctx.setFormat(meta.format());
        ctx.setCompression(meta.compression());
        ctx.setChecksumType("SHA256");

        try {
            delegate.decompress(archive, targetDir);

            ctx.setStatus("SUCCESS");
            ctx.setSizeBytes(Files.size(archive));
            ctx.setChecksumValue(ArchiveMetadataUtil.sha256(archive));
        } catch (IOException e) {
            ctx.setStatus("FAILED");
            throw e;
        } finally {
            ctx.setDurationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            visitor.visit(ctx);
        }
    }

    @Override
    public String getName() {
        return delegate.getName();
    }
}
