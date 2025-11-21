package com.beowulf.core.facade;

import com.beowulf.core.factory.ArchiverFactory;
import com.beowulf.core.strategy.Archiver;

import java.io.IOException;
import java.nio.file.Path;

public class ArchiveService {
    private final ArchiverFactory factory = new ArchiverFactory();

    public void compress(Path sourceDir, Path targetArchive) throws IOException {
        Archiver archiver = factory.getArchiver(targetArchive);
        archiver.compress(sourceDir, targetArchive);
    }

    public void decompress(Path archive, Path targetDir) throws IOException {
        Archiver archiver = factory.getArchiver(archive);
        archiver.decompress(archive, targetDir);
    }
}
