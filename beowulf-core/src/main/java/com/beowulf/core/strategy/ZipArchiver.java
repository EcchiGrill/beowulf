package com.beowulf.core.strategy;

import com.beowulf.core.adapter.ZipAdapter;

import java.io.IOException;
import java.nio.file.Path;

public class ZipArchiver implements Archiver {

    private final ZipAdapter adapter = new ZipAdapter();

    @Override
    public void compress(Path sourceDir, Path targetArchive) throws IOException {
        adapter.compress(sourceDir, targetArchive);
    }

    @Override
    public void decompress(Path archive, Path targetDir) throws IOException {
        adapter.decompress(archive, targetDir);
    }

    @Override
    public String getName() {
        return "ZIP";
    }
}
