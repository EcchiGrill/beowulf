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

        runProcess(cmd, targetDir.toFile());
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
            Process p = new ProcessBuilder(binary).start();
            p.destroy();
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean isWindows() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }

    private void runProcess(List<String> command, File workingDir) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workingDir);
        pb.redirectErrorStream(true);

        Process p = pb.start();

        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line).append(System.lineSeparator());
            }
        }

        try {
            int exit = p.waitFor();
            if (exit != 0) {
                throw new IOException("unace failed with exit code " + exit + "\nOutput:\n" + out);
            }
        } catch (InterruptedException e) {
            p.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("unace interrupted", e);
        }
    }
}
