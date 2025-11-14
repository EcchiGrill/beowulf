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

        try (OutputStream filOutputStream = Files.newOutputStream(targetArchive);
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(filOutputStream);
                GZIPOutputStream gzipOutputStream = new GZIPOutputStream(bufferedOutputStream);
                TarArchiveOutputStream tarArchiveOutputStream = new TarArchiveOutputStream(gzipOutputStream)) {

            tarArchiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

            Files.walk(sourceDir)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        String entryName = sourceDir.relativize(path).toString();

                        try (InputStream in = Files.newInputStream(path)) {
                            TarArchiveEntry entry = new TarArchiveEntry(path.toFile(), entryName);
                            tarArchiveOutputStream.putArchiveEntry(entry);
                            IOUtils.copy(in, tarArchiveOutputStream);
                            tarArchiveOutputStream.closeArchiveEntry();
                        } catch (IOException error) {
                            throw new UncheckedIOException("TAR.GZ compression failed", error);
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
