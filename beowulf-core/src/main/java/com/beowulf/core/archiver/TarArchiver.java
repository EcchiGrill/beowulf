package com.beowulf.core.archiver;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.file.*;

public class TarArchiver implements Archiver {

    @Override
    public void compress(Path sourceDir, Path targetArchive) throws IOException {
        try (OutputStream fileOutputStream = Files.newOutputStream(targetArchive);
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
                TarArchiveOutputStream tarArchiveOutputStream = new TarArchiveOutputStream(bufferedOutputStream)) {

            Files.walk(sourceDir)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        String entryName = sourceDir.relativize(path).toString();

                        try (InputStream inputStream = Files.newInputStream(path)) {
                            TarArchiveEntry entry = new TarArchiveEntry(path.toFile(), entryName);
                            tarArchiveOutputStream.putArchiveEntry(entry);
                            IOUtils.copy(inputStream, tarArchiveOutputStream);
                            tarArchiveOutputStream.closeArchiveEntry();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });

            tarArchiveOutputStream.finish();
        }
    }

    @Override
    public void decompress(Path archive, Path targetDir) throws IOException {
        try (InputStream fileInputStream = Files.newInputStream(archive);
                BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
                TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(bufferedInputStream)) {

            ArchiveEntry entry;
            while ((entry = tarArchiveInputStream.getNextEntry()) != null) {
                Path outputPath = targetDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    try (OutputStream outputStream = Files.newOutputStream(outputPath)) {
                        IOUtils.copy(tarArchiveInputStream, outputStream);
                    }
                }
            }
        }
    }

    @Override
    public String getName() {
        return "TAR";
    }
}
