package com.beowulf.core.adapter;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.file.*;
import java.util.Enumeration;
import java.util.Objects;

public class ZipAdapter {

    /**
     * Compresses sourceDir into a .zip archive.
     * 
     * @param sourceDir     path to file or directory to compress.
     * @param targetArchive path to resulting .zip file.
     */
    public void compress(Path sourceDir, Path targetArchive) throws IOException {
        Objects.requireNonNull(sourceDir, "sourceDir");
        Objects.requireNonNull(targetArchive, "targetArchive");

        if (!Files.exists(sourceDir)) {
            throw new FileNotFoundException("Source directory not found: " + sourceDir);
        }

        Path parent = sourceDir.getParent();
        Path base = parent != null ? parent : sourceDir;

        try (OutputStream outputStream = Files.newOutputStream(targetArchive);
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
                ZipArchiveOutputStream zipArchiveOutputStream = new ZipArchiveOutputStream(bufferedOutputStream)) {

            zipArchiveOutputStream.setLevel(ZipArchiveOutputStream.STORED);

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
                                ZipArchiveEntry dirEntry = new ZipArchiveEntry(entryName);
                                dirEntry.setTime(Files.getLastModifiedTime(path).toMillis());
                                zipArchiveOutputStream.putArchiveEntry(dirEntry);
                                zipArchiveOutputStream.closeArchiveEntry();
                            } else {
                                try (InputStream fileInputStream = Files.newInputStream(path)) {
                                    ZipArchiveEntry entry = new ZipArchiveEntry(entryName);
                                    zipArchiveOutputStream.putArchiveEntry(entry);
                                    IOUtils.copy(fileInputStream, zipArchiveOutputStream);
                                    zipArchiveOutputStream.closeArchiveEntry();
                                }
                            }
                        } catch (IOException error) {
                            throw new UncheckedIOException("ZIP compression failed", error);
                        }
                    });

            zipArchiveOutputStream.finish();
        }
    }

    /**
     * Extracts a .zip archive.
     * 
     * @param archive   path to existing .zip archive.
     * @param targetDir destination directory.
     */
    public void decompress(Path archive, Path targetDir) throws IOException {
        Objects.requireNonNull(archive);
        Objects.requireNonNull(targetDir);

        if (!Files.exists(archive)) {
            throw new FileNotFoundException("Archive file not found: " + archive);
        }

        Files.createDirectories(targetDir);

        try (ZipFile zipFile = ZipFile.builder().setPath(archive).get()) {

            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();

            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                Path outputPath = targetDir.resolve(entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    try (InputStream inputStream = zipFile.getInputStream(entry);
                            OutputStream outputStream = Files.newOutputStream(outputPath)) {
                        IOUtils.copy(inputStream, outputStream);
                    }
                }
            }
        }
    }
}
