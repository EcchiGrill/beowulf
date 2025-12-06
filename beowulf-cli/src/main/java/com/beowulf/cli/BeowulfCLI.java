package com.beowulf.cli;

import com.beowulf.core.db.DatabaseMigrations;
import com.beowulf.core.facade.ArchiveService;
import com.beowulf.core.model.ArchiveLog;
import com.beowulf.core.service.ArchiveLogService;
import com.beowulf.core.user.AppUser;
import com.beowulf.core.user.AppUserService;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class BeowulfCLI {

    private static final ArchiveService archiveService = new ArchiveService();

    private static AppUserService identityProvider;
    private static ArchiveLogService logQueryService;

    public static void main(String[] args) {

        DatabaseMigrations.migrate();

        identityProvider = new AppUserService();
        logQueryService = new ArchiveLogService();

        if (args.length < 1) {
            printUsage();
            return;
        }

        String command = args[0];

        try {
            switch (command) {

                case "compress" -> handleCompress(args);
                case "decompress" -> handleDecompress(args);
                case "logs" -> handleLogs(args);
                case "help" -> printUsage();

                default -> {
                    System.out.println("Unknown command: " + command);
                    printUsage();
                }
            }
        } catch (Exception error) {
            System.err.println("ERROR: " + error.getMessage());
            error.printStackTrace();
            System.exit(1);
        }
    }

    private static void handleCompress(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("Invalid usage for compress.");
            System.err.println("Usage: beowulf compress <sourceDir> <targetArchive>");
            return;
        }

        Path sourceDir = Paths.get(args[1]);
        Path targetArchive = Paths.get(args[2]);

        System.out.println("→ Compressing...");
        archiveService.compress(sourceDir, targetArchive);
        System.out.println("✓ Done: " + targetArchive);
    }

    private static void handleDecompress(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println("Invalid usage for decompress.");
            System.err.println("Usage: beowulf decompress <archive> <targetDir>");
            return;
        }

        Path archive = Paths.get(args[1]);
        Path targetDir = Paths.get(args[2]);

        System.out.println("→ Decompressing...");
        archiveService.decompress(archive, targetDir);
        System.out.println("✓ Extracted to: " + targetDir);
    }

    private static void handleLogs(String[] args) {
        int limit = 20;
        if (args.length >= 2) {
            try {
                limit = Integer.parseInt(args[1]);
            } catch (NumberFormatException error) {
                System.err.println("Invalid limit: " + args[1] + ". Using default: 20");
            }
        }

        AppUser user = identityProvider.resolveCurrentUser();
        List<ArchiveLog> entries = logQueryService.findRecentLogs(user.getId(), limit);

        if (entries.isEmpty()) {
            System.out.println("No logs for current user: " + user.getUsername()
                    + " (" + user.getId() + ")");
            return;
        }

        System.out.println("Beowulf logs for user: " + user.getUsername()
                + " (" + user.getId() + ")");
        System.out.println("Last " + entries.size() + " operations:\n");

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (ArchiveLog entry : entries) {
            String createdAt = entry.getCreatedAt() != null
                    ? entry.getCreatedAt().format(formatter)
                    : "?";

            String pathToShow;
            if ("DECOMPRESS".equalsIgnoreCase(entry.getOperation())) {
                String targetPath = entry.getTargetPath();
                if (targetPath != null && !targetPath.isBlank()) {
                    pathToShow = targetPath;
                } else {
                    pathToShow = entry.getArchivePath();
                }
            } else {
                pathToShow = entry.getArchivePath();
            }

            System.out.printf(
                    "[%d] %s | %-9s | %-7s | %6d ms%n",
                    entry.getId(),
                    createdAt,
                    entry.getOperation(),
                    entry.getStatus(),
                    entry.getDurationMs());

            System.out.printf(
                    "    Path   : %s%n",
                    pathToShow);
            System.out.printf(
                    "    Format : %s / %s, %d bytes%n",
                    entry.getFormat(),
                    entry.getCompression(),
                    entry.getSizeBytes());
            System.out.printf(
                    "    Checksum: %s %s%n",
                    entry.getSplitPartsCount(),
                    entry.getSplitTotalSize());
            System.out.println();
        }
    }

    private static void printUsage() {
        System.out.println("""
                Beowulf CLI
                ---------------------
                Usage:
                  beowulf compress <sourceDir> <targetArchive>
                  beowulf decompress <archiveFile> <targetDir>
                  beowulf logs [limit]

                Examples:
                  beowulf compress ./source beowulf.zip
                  beowulf compress ./folder data.tar.gz
                  beowulf decompress archive.rar ./output
                  beowulf logs
                  beowulf logs 10

                Supported formats:
                  ZIP, TAR.GZ, TGZ, RAR, ACE (extract-only)

                """);
    }
}
