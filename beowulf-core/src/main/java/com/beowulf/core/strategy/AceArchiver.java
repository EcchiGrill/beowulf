package com.beowulf.core.strategy;

import java.io.IOException;
import java.nio.file.Path;

import com.beowulf.core.adapter.AceAdapter;

public class AceArchiver implements Archiver {
    private final AceAdapter adapter = new AceAdapter();

    @Override
    public void compress(Path sourceDir, Path targetArchive) {
        throw new UnsupportedOperationException("ACE compression not supported.");
    }

    @Override
    public void decompress(Path archive, Path targetDir) throws IOException {
        adapter.decompress(archive, targetDir);
    }

    @Override
    public String getName() {
        return "ACE";
    }
}
