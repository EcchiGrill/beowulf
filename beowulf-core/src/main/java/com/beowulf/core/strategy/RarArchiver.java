package com.beowulf.core.strategy;

import com.github.junrar.Junrar;
import com.github.junrar.exception.RarException;
import com.github.junrar.exception.UnsupportedRarV5Exception;

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
        } catch (UnsupportedRarV5Exception unsupportedError) {
            throw new IOException("RAR5 format is not supported for now.", unsupportedError);
        } catch (RarException error) {
            throw new IOException("RAR extraction failed for " + archive + " -> " + targetDir, error);
        }
    }

    @Override
    public String getName() {
        return "RAR";
    }
}
