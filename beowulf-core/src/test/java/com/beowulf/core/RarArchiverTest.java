package com.beowulf.core;

import com.beowulf.core.archiver.RarArchiver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.*;

class RarArchiverTest {

    @TempDir
    Path tempDir;

    @Test
    void decompressionTest() throws Exception {
        // Load from classpath as a stream (works everywhere)
        try (InputStream in = RarArchiverTest.class.getResourceAsStream("/com/resources/test.rar")) {
            assertNotNull(in, "Test resource not found on classpath: /com/resources/test.rar");
            Path archive = tempDir.resolve("test.rar");
            Files.copy(in, archive, REPLACE_EXISTING);

            Path outputDir = tempDir.resolve("beowulf-out-dir");
            Files.createDirectories(outputDir);

            Path extractedFile = outputDir.resolve("compress-me.txt");

            var archiver = new RarArchiver(); // should delegate to junrar
            archiver.decompress(archive, outputDir);

            assertTrue(Files.exists(extractedFile), "File should exist after decompression");

            String expected = "Compress me in, Beowulf!";
            String actual = Files.readString(extractedFile, StandardCharsets.UTF_8);
            assertEquals(expected, actual, "Decompressed file content is correct");
        }
    }
}
