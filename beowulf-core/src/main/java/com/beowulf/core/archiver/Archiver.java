package com.beowulf.core.archiver;

import java.io.IOException;
import java.nio.file.Path;

public interface Archiver {
    void compress(Path sourceDir, Path targetArchive) throws IOException;
    void decompress(Path archive, Path targetDir) throws IOException;

    String getName();
}
