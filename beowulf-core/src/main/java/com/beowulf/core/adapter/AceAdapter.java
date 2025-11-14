package com.beowulf.core.adapter;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class AceAdapter {

    /**
     * Extracts ACE archive using external "unace" tool.
     * Requires user to have unace installed on their OS.
     * 
     * @param archive   path to existing ACE archive
     * @param targetDir destination directory
     */
    public void decompress(Path archive, Path targetDir) throws IOException {
        Objects.requireNonNull(archive, "archive");
        Objects.requireNonNull(targetDir, "targetDir");

        if (!Files.exists(archive)) {
            throw new FileNotFoundException("ACE file not found: " + archive);
        }

        Files.createDirectories(targetDir);

        String unace = findUnaceBinary()
                .orElseThrow(() -> new IOException("""
                        'unace' binary is not installed or not in PATH.

                        Please install it manually:
                          • Arch Linux:   sudo pacman -S unace
                          • Debian/Ubuntu: sudo apt install unace-nonfree
                          • macOS:        brew install unace
                          • Windows:      Install WinACE (includes unace.exe)
                        """));

        List<String> cmd = new ArrayList<>();
        cmd.add(unace);
        cmd.add("x");
        cmd.add(archive.toAbsolutePath().toString());
        cmd.add(targetDir.toAbsolutePath().toString() + File.separator);

        runProcess(cmd);
    }

    private Optional<String> findUnaceBinary() {
        if (isWindows()) {
            if (isOnPath("unace.exe"))
                return Optional.of("unace.exe");
            if (isOnPath("unace"))
                return Optional.of("unace");
        } else {
            if (isOnPath("unace"))
                return Optional.of("unace");
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
                throw new IOException("ACE failed (exit " + exit + "):\n" + output);
            }
        } catch (InterruptedException error) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("ACE interrupted", error);
        }
    }
}
