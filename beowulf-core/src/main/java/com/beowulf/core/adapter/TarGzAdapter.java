package com.beowulf.core.adapter;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.file.*;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class TarGzAdapter {

    /**
     * Compresses sourceDir into a .tar.gz archive.
     *
     * @param sourceDir     path to file or directory to compress.
     * @param targetArchive path to resulting .tar.gz file.
     */
    public void compress(Path sourceDir, Path targetArchive) throws IOException {
        Objects.requireNonNull(sourceDir, "sourceDir");
        Objects.requireNonNull(targetArchive, "targetArchive");

        if (!Files.exists(sourceDir))
            throw new FileNotFoundException("Source directory not found: " + sourceDir);

        Path parent = sourceDir.getParent();
        Path base;

        String dirName = sourceDir.getFileName() != null ? sourceDir.getFileName().toString() : "";
        if (dirName.startsWith("beowulf-edit-")) {
            base = sourceDir;
        } else {
            base = parent != null ? parent : sourceDir;
        }

        try (OutputStream filOutputStream = Files.newOutputStream(targetArchive);
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(filOutputStream);
                GZIPOutputStream gzipOutputStream = new GZIPOutputStream(bufferedOutputStream);
                TarArchiveOutputStream tarArchiveOutputStream = new TarArchiveOutputStream(gzipOutputStream)) {

            tarArchiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

            Files.walk(sourceDir)
                    .forEach(path -> {
                        try {
                            Path relativePath = base.relativize(path);
                            String entryName = relativePath.toString().replace(File.separatorChar, '/');

                            if (entryName.isEmpty()) {
                                return;
                            }

                            if (Files.isDirectory(path)) {
                                if (!entryName.endsWith("/")) {
                                    entryName = entryName + "/";
                                }

                                TarArchiveEntry dirEntry = new TarArchiveEntry(entryName);
                                dirEntry.setModTime(Files.getLastModifiedTime(path).toMillis());
                                tarArchiveOutputStream.putArchiveEntry(dirEntry);
                                tarArchiveOutputStream.closeArchiveEntry();
                            } else {
                                TarArchiveEntry fileEntry = new TarArchiveEntry(path.toFile(), entryName);
                                tarArchiveOutputStream.putArchiveEntry(fileEntry);
                                try (InputStream in = Files.newInputStream(path)) {
                                    IOUtils.copy(in, tarArchiveOutputStream);
                                }
                                tarArchiveOutputStream.closeArchiveEntry();
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException("TAR.GZ compression failed", e);
                        }
                    });

            tarArchiveOutputStream.finish();
        }
    }

    /**
     * Extracts a tar.gz archive.
     *
     * @param archive   path to existing .tar.gz archive.
     * @param targetDir destination directory.
     */
    public void decompress(Path archive, Path targetDir) throws IOException {
        Objects.requireNonNull(archive);
        Objects.requireNonNull(targetDir);

        if (!Files.exists(archive))
            throw new FileNotFoundException("Archive file not found: " + archive);

        Files.createDirectories(targetDir);

        try (InputStream filInputStream = Files.newInputStream(archive);
                BufferedInputStream bufferedInputStream = new BufferedInputStream(filInputStream);
                GZIPInputStream gzipInputStream = new GZIPInputStream(bufferedInputStream);
                TarArchiveInputStream tarInputStream = new TarArchiveInputStream(gzipInputStream)) {

            ArchiveEntry entry;
            while ((entry = tarInputStream.getNextEntry()) != null) {
                Path outputPath = targetDir.resolve(entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    try (OutputStream outputStream = Files.newOutputStream(outputPath)) {
                        IOUtils.copy(tarInputStream, outputStream);
                    }
                }
            }
        }
    }
}
