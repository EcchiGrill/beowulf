package com.beowulf.core.factory;

import com.beowulf.core.strategy.*;

import java.nio.file.Path;
import java.util.Locale;

public class ArchiverFactory {

    /**
     * Returns an Archiver strategy based on the file extension.
     */
    public Archiver getArchiver(Path archive) {
        String name = archive.getFileName().toString().toLowerCase(Locale.ROOT);

        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            return new TarGzArchiver();
        }

        if (name.endsWith(".zip")) {
            return new ZipArchiver();
        }

        if (name.endsWith(".rar")) {
            return new RarArchiver();
        }

        if (name.endsWith(".ace")) {
            return new AceArchiver();
        }

        throw new IllegalArgumentException("Unsupported archive format: " + name);
    }

}
