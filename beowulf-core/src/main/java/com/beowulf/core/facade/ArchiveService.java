package com.beowulf.core.facade;

import com.beowulf.core.decorator.ArchiverLogger;
import com.beowulf.core.factory.ArchiverFactory;
import com.beowulf.core.interfaces.Archiver;

import java.io.IOException;
import java.nio.file.Path;

public class ArchiveService {
    private final ArchiverFactory factory = new ArchiverFactory();

    /**
     * Compress a source directory or file into a single archive file.
     *
     * @param sourceDir     path to file or directory to compress
     * @param targetArchive path to resulting archive file
     */

    public void compress(Path sourceDir, Path targetArchive) throws IOException {
        Archiver archiver = factory.getArchiver(targetArchive);

        Archiver loggedArchiver = new ArchiverLogger(archiver);

        loggedArchiver.compress(sourceDir, targetArchive);
    }

    /**
     * Decompress an archive file into a target directory.
     *
     * @param archive   path to existing archive
     * @param targetDir destination directory
     */

    public void decompress(Path archive, Path targetDir) throws IOException {
        Archiver archiver = factory.getArchiver(archive);

        Archiver loggedArchiver = new ArchiverLogger(archiver);

        loggedArchiver.decompress(archive, targetDir);
    }
}
