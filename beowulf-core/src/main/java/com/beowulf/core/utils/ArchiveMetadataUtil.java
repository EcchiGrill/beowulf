package com.beowulf.core.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class ArchiveMetadataUtil {

    public record ArchiveMeta(String format, String compression) {
    }

    private ArchiveMetadataUtil() {
    }

    public static ArchiveMeta detect(Path archivePath) {
        String name = archivePath.getFileName().toString().toLowerCase();

        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            return new ArchiveMeta("TAR", "GZIP");
        }
        if (name.endsWith(".zip")) {
            return new ArchiveMeta("ZIP", "DEFLATE");
        }
        if (name.endsWith(".rar")) {
            return new ArchiveMeta("RAR", "RAR");
        }
        if (name.endsWith(".ace")) {
            return new ArchiveMeta("ACE", "ACE");
        }

        return new ArchiveMeta("UNKNOWN", "UNKNOWN");
    }

    /**
     * Calculate the SHA-256 checksum of a file.
     *
     * @param path the path to the file
     * @return the SHA-256 checksum
     * @throws IOException if the file cannot be read
     */

    public static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream inputStream = new DigestInputStream(Files.newInputStream(path), digest)) {
                byte[] buffer = new byte[8192];
                while (inputStream.read(buffer) != -1) {
                }
            }
            byte[] hash = digest.digest();
            StringBuilder stringBuilder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                stringBuilder.append(String.format("%02x", b));
            }
            return stringBuilder.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 not available", error);
        }
    }
}
