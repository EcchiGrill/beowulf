package com.beowulf.core.strategy;

import com.beowulf.core.adapter.TarGzAdapter;
import com.beowulf.core.interfaces.Archiver;

import java.io.IOException;
import java.nio.file.Path;

public class TarGzArchiver implements Archiver {

    private final TarGzAdapter adapter = new TarGzAdapter();

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
        return "TAR";
    }
}
