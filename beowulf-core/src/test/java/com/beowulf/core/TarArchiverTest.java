package com.beowulf.core;

import org.junit.jupiter.api.Test;

import com.beowulf.core.strategy.TarArchiver;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.*;

class TarArchiverTest {
    @Test
    void roundTripCompression() throws Exception {
        Path inputDir = Files.createTempDirectory("beowulf-in-dir");
        Path file = inputDir.resolve("compress-me.txt");
        String content = "Compress me in, Beowulf!";
        Files.writeString(file, content);

        Path archive = Files.createTempFile("beowulf-archive", ".tar.gz");
        Path outputDir = Files.createTempDirectory("beowulf-out-dir");

        TarArchiver archiver = new TarArchiver();
        archiver.compress(inputDir, archive);
        archiver.decompress(archive, outputDir);

        Path extractedFile = outputDir.resolve("compress-me.txt");

        assertTrue(Files.exists(archive), "Archive should exist after compression");
        assertTrue(Files.exists(extractedFile), "File should exist after decompression");

        String extractedContent = Files.readString(extractedFile);
        assertEquals(content, extractedContent, "Decompressed file content should match original");
    }
}
