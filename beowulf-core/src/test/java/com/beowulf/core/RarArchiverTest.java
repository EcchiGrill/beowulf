package com.beowulf.core;

import com.beowulf.core.strategy.RarArchiver;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.*;

class RarArchiverTest {

    @TempDir
    Path tempDir;

    private static boolean isRarInstalled() {
        String os = System.getProperty("os.name").toLowerCase();
        boolean isWindows = os.contains("win");
        String[] candidates;

        if (isWindows) {
            candidates = new String[] { "rar.exe", "rar" };
        } else {
            candidates = new String[] { "rar" };
        }

        for (String bin : candidates) {
            try {
                Process process = new ProcessBuilder(bin).start();
                process.destroy();
                return true;
            } catch (IOException error) {
                throw new UncheckedIOException("Process wasn't started correctly!", error);
            }
        }
        return false;
    }

    @Test
    void roundTripCompression() throws Exception {
        Assumptions.assumeTrue(isRarInstalled(),
                "Skipping test — RAR binary not installed");

        Path inputDir = Files.createTempDirectory("beowulf-in-dir");
        Path file = inputDir.resolve("compress-me.txt");
        String content = "Compress me in, Beowulf!";
        Files.writeString(file, content);

        Path archive = tempDir.resolve("beowulf-archive.rar");
        Path outputDir = Files.createTempDirectory("beowulf-out-dir");

        RarArchiver archiver = new RarArchiver();
        archiver.compress(inputDir, archive);
        archiver.decompress(archive, outputDir);

        String extractedFolderName = inputDir.getFileName().toString();
        Path extractedFolderDir = outputDir.resolve(extractedFolderName);
        Path extractedFile = extractedFolderDir.resolve("compress-me.txt");

        assertTrue(Files.exists(archive), "Archive should exist after compression");
        assertTrue(Files.exists(extractedFile), "File should exist after decompression");

        String extractedContent = Files.readString(extractedFile);
        assertEquals(content, extractedContent, "Decompressed file content should match original");
    }
}
