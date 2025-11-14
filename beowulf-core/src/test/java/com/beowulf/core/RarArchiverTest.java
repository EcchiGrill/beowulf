package com.beowulf.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.beowulf.core.strategy.RarArchiver;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.nio.file.*;

class RarArchiverTest {

    @TempDir
    Path tempDir;

    @Test
    void decompressionTest() throws Exception {
        try (InputStream in = RarArchiverTest.class.getResourceAsStream("/com/resources/test.rar")) {
            Path archive = tempDir.resolve("test.rar");
            Files.copy(in, archive, REPLACE_EXISTING);

            Path outputDir = tempDir.resolve("beowulf-out-dir");
            Files.createDirectories(outputDir);

            Path extractedFile = outputDir.resolve("compress-me.txt");

            var archiver = new RarArchiver();
            archiver.decompress(archive, outputDir);

            assertTrue(Files.exists(extractedFile), "File should exist after decompression");

            String expected = "Compress me in, Beowulf!";
            String actual = Files.readString(extractedFile, StandardCharsets.UTF_8);
            assertEquals(expected, actual, "Decompressed file content is correct");
        }
    }
}
