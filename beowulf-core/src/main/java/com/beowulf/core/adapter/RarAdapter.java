package com.beowulf.core.adapter;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class RarAdapter {

    /**
     * Compresses sourceDir into a .rar archive using external "rar" tool.
     * Requires user to have rar installed on their OS.
     * 
     * @param sourceDir     path to file or directory to compress.
     * @param targetArchive path to resulting .rar file.
     */
    public void compress(Path sourceDir, Path targetArchive) throws IOException {
        Objects.requireNonNull(sourceDir, "sourceDir");
        Objects.requireNonNull(targetArchive, "targetArchive");

        if (!Files.exists(sourceDir)) {
            throw new FileNotFoundException("Source directory not found: " + sourceDir);
        }

        String rar = findRarBinary()
                .orElseThrow(() -> new IOException("""
                        'rar' binary is not installed or not in PATH.

                        Please install it manually:
                          • Arch Linux:     yay -S rar
                          • Debian/Ubuntu:  Download 'rarlinux' package from https://rarlab.com and install manually
                          • macOS:          brew install --cask rar
                          • Windows:        Install WinRAR (includes rar.exe)
                        """));

        List<String> cmd = new ArrayList<>();
        cmd.add(rar);
        cmd.add("a");
        cmd.add("-ep1");
        cmd.add(targetArchive.toAbsolutePath().toString());
        cmd.add(sourceDir.toAbsolutePath().toString());

        runProcess(cmd);
    }

    /**
     * Extracts a .rar archive using "rar x".
     * Requires user to have rar installed on their OS.
     * 
     * @param archive   path to existing .rar archive.
     * @param targetDir destination directory.
     */
    public void decompress(Path archive, Path targetDir) throws IOException {
        Objects.requireNonNull(archive, "archive");
        Objects.requireNonNull(targetDir, "targetDir");

        if (!Files.exists(archive)) {
            throw new FileNotFoundException("Archive file not found: " + archive);
        }

        Files.createDirectories(targetDir);

        String rar = findRarBinary()
                .orElseThrow(() -> new IOException("""
                        'rar' binary is not installed or not in PATH.

                        Please install it manually:
                          • Arch Linux:     yay -S rar
                          • Debian/Ubuntu:  Download 'rarlinux' package from https://rarlab.com and install manually
                          • macOS:          brew install --cask rar
                          • Windows:        Install WinRAR (includes rar.exe)
                        """));

        List<String> cmd = new ArrayList<>();
        cmd.add(rar);
        cmd.add("x");
        cmd.add("-o+");
        cmd.add(archive.toAbsolutePath().toString());
        cmd.add(targetDir.toAbsolutePath().toString() + File.separator);

        runProcess(cmd);
    }

    private Optional<String> findRarBinary() {
        if (isWindows()) {
            if (isOnPath("rar.exe"))
                return Optional.of("rar.exe");
            if (isOnPath("rar"))
                return Optional.of("rar");
        } else {
            if (isOnPath("rar"))
                return Optional.of("rar");
        }
        return Optional.empty();
    }

    private boolean isOnPath(String binary) {
        try {
            Process process = new ProcessBuilder(binary).start();
            process.destroy();
            return true;
        } catch (IOException error) {
            return false;
        }
    }

    private boolean isWindows() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    private void runProcess(List<String> command) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        try {
            int exit = process.waitFor();
            if (exit != 0) {
                throw new IOException("RAR failed (exit " + exit + "):\n" + output);
            }
        } catch (InterruptedException error) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("RAR interrupted", error);
        }
    }

}
