package com.beowulf.core.archiver;

import java.io.IOException;
import java.nio.file.Path;

public interface Archiver {
    /**
     * Compresses the given source directory or file into a single archive file.
     *
     * @param sourceDir     path to file or directory to compress
     * @param targetArchive path to resulting archive file (.zip, .tar, etc.)
     */
    void compress(Path sourceDir, Path targetArchive) throws IOException;

    /**
     * Extracts the given archive file into the target directory.
     *
     * @param archive   path to existing archive
     * @param targetDir destination directory
     */
    void decompress(Path archive, Path targetDir) throws IOException;

    String getName();
}
