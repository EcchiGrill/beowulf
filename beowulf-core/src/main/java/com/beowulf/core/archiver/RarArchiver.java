package com.beowulf.core.archiver;

import com.github.junrar.Junrar;
import com.github.junrar.exception.RarException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RarArchiver implements Archiver {

    @Override
    public void compress(Path sourceDir, Path targetArchive) throws IOException {
        throw new UnsupportedOperationException("RAR creation not supported. Use ZIP/TAR archiver for compression.");
    }

    @Override
    public void decompress(Path archive, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);

        try {
            Junrar.extract(archive.toFile(), targetDir.toFile());
        } catch (RarException e) {
            throw new IOException("RAR extraction failed for " + archive + " -> " + targetDir, e);
        }
    }

    @Override
    public String getName() {
        return "RAR";
    }
}
