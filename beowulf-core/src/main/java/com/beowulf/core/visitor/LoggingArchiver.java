package com.beowulf.core.visitor;

import com.beowulf.core.strategy.Archiver;
import com.beowulf.core.user.AppUser;
import com.beowulf.core.user.LocalUserIdentityProvider;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;

public class LoggingArchiver implements Archiver {

    private final Archiver delegate;
    private final LocalUserIdentityProvider identityProvider;
    private final PersistArchiveVisitor persistVisitor;

    public LoggingArchiver(Archiver delegate,
            LocalUserIdentityProvider identityProvider,
            PersistArchiveVisitor persistVisitor) {
        this.delegate = delegate;
        this.identityProvider = identityProvider;
        this.persistVisitor = persistVisitor;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public void compress(Path sourceDir, Path targetArchive) throws IOException {
        AppUser user = identityProvider.resolveCurrentUser();
        long started = System.currentTimeMillis();

        delegate.compress(sourceDir, targetArchive);

        long duration = System.currentTimeMillis() - started;

        if (!Files.exists(targetArchive)) {
            throw new IOException("Archive was not created: " + targetArchive);
        }

        long sizeBytes = Files.size(targetArchive);
        String checksumValue = sha256Hex(targetArchive);

        ArchiveOperationContext ctx = new ArchiveOperationContext();
        ctx.setUser(user);

        ctx.setArchiveId(UUID.randomUUID());
        ctx.setFormat(detectFormat(targetArchive));
        ctx.setCompression(detectCompression(targetArchive));
        ctx.setArchivePath(targetArchive.toString());
        ctx.setSizeBytes(sizeBytes);

        ctx.setChecksumType("SHA256");
        ctx.setChecksumValue(checksumValue);

        ctx.setOperation("COMPRESS");
        ctx.setOperationPath(sourceDir.toString());
        ctx.setStatus("SUCCESS");
        ctx.setDurationMs(duration);

        ctx.accept(persistVisitor);
    }

    @Override
    public void decompress(Path archive, Path targetDir) throws IOException {
        AppUser user = identityProvider.resolveCurrentUser();
        long started = System.currentTimeMillis();

        if (!Files.exists(archive)) {
            throw new IOException("Archive file not found: " + archive);
        }

        String checksumValue = sha256Hex(archive);
        long sizeBytes = Files.size(archive);

        String status = "SUCCESS";
        try {
            delegate.decompress(archive, targetDir);
        } catch (IOException | RuntimeException error) {
            status = "FAILED";
            throw error;
        } finally {
            long duration = System.currentTimeMillis() - started;

            ArchiveOperationContext ctx = new ArchiveOperationContext();
            ctx.setUser(user);

            ctx.setArchiveId(UUID.randomUUID());
            ctx.setFormat(detectFormat(archive));
            ctx.setCompression(detectCompression(archive));
            ctx.setArchivePath(archive.toString());
            ctx.setSizeBytes(sizeBytes);

            ctx.setChecksumType("SHA256");
            ctx.setChecksumValue(checksumValue);

            ctx.setOperation("DECOMPRESS");
            ctx.setOperationPath(targetDir.toString());
            ctx.setStatus(status);
            ctx.setDurationMs(duration);

            ctx.accept(persistVisitor);
        }
    }

    private String detectFormat(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip"))
            return "ZIP";
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz"))
            return "TAR";
        if (name.endsWith(".tar.bz2") || name.endsWith(".tbz") || name.endsWith(".tbz2"))
            return "TAR";
        if (name.endsWith(".7z"))
            return "7Z";
        if (name.endsWith(".rar"))
            return "RAR";
        if (name.endsWith(".tar"))
            return "TAR";
        if (name.endsWith(".ace"))
            return "ACE";
        return "UNKNOWN";
    }

    private String detectCompression(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip"))
            return "DEFLATE";
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz"))
            return "GZIP";
        if (name.endsWith(".tar.bz2") || name.endsWith(".tbz") || name.endsWith(".tbz2"))
            return "BZIP2";
        if (name.endsWith(".7z"))
            return "LZMA";
        if (name.endsWith(".rar"))
            return "RAR";
        if (name.endsWith(".tar"))
            return "NONE";
        if (name.endsWith(".ace"))
            return "ACE";
        return "UNKNOWN";
    }

    private String sha256Hex(Path path) throws IOException {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];

            try (var in = Files.newInputStream(path)) {
                int read;
                while ((read = in.read(buffer)) != -1) {
                    messageDigest.update(buffer, 0, read);
                }
            }

            byte[] digest = messageDigest.digest();
            StringBuilder stringBuilder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                stringBuilder.append(String.format("%02x", b));
            }
            return stringBuilder.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new RuntimeException("SHA-256 unsupported", error);
        }
    }
}
