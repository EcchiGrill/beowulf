package com.beowulf.core.strategy;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.file.*;
import java.util.Enumeration;

public class ZipArchiver implements Archiver {

    @Override
    public void compress(Path sourceDir, Path targetArchive) throws IOException {
        try (OutputStream fileOutputStream = Files.newOutputStream(targetArchive);
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
                ZipArchiveOutputStream zipArchiveOutputStream = new ZipArchiveOutputStream(bufferedOutputStream)) {

            Files.walk(sourceDir)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        String entryName = sourceDir.relativize(path).toString();

                        try (InputStream inputStream = Files.newInputStream(path)) {
                            ZipArchiveEntry entry = new ZipArchiveEntry(entryName);
                            zipArchiveOutputStream.putArchiveEntry(entry);
                            IOUtils.copy(inputStream, zipArchiveOutputStream);
                            zipArchiveOutputStream.closeArchiveEntry();
                        } catch (IOException error) {
                            throw new UncheckedIOException(
                                    "ZIP compression failed for " + sourceDir + " -> " + targetArchive,
                                    error);
                        }
                    });

            zipArchiveOutputStream.finish();
        }
    }

    @Override
    public void decompress(Path archive, Path targetDir) throws IOException {
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

    @Override
    public String getName() {
        return "ZIP";
    }
}
