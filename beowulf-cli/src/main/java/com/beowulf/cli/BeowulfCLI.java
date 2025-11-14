package com.beowulf.cli;

import com.beowulf.core.factory.ArchiverFactory;
import com.beowulf.core.strategy.Archiver;

import java.nio.file.*;
import java.io.IOException;

public class BeowulfCLI {

    private static final ArchiverFactory factory = new ArchiverFactory();

    public static void main(String[] args) {

        if (args.length < 1) {
            printUsage();
            return;
        }

        String command = args[0];

        try {
            switch (command) {

                case "compress" -> handleCompress(args);
                case "decompress" -> handleDecompress(args);
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

        Archiver archiver = factory.getArchiver(targetArchive);

        System.out.println("→ Compressing using " + archiver.getName());
        archiver.compress(sourceDir, targetArchive);
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

        Archiver archiver = factory.getArchiver(archive);

        System.out.println("→ Decompressing using " + archiver.getName());
        archiver.decompress(archive, targetDir);
        System.out.println("✓ Extracted to: " + targetDir);
    }

    private static void printUsage() {
        System.out.println("""
                Beowulf CLI
                ---------------------
                Usage:
                  beowulf compress <sourceDir> <targetArchive>
                  beowulf decompress <archiveFile> <targetDir>

                Examples:
                  beowulf compress ./source beowulf.zip
                  beowulf compress ./folder data.tar.gz
                  beowulf decompress archive.rar ./output

                Supported formats:
                  ZIP, TAR.GZ, TGZ, RAR, ACE (extract-only)

                """);
    }
}
