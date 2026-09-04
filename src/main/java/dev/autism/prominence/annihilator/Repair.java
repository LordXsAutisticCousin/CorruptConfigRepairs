package dev.autism.prominence.annihilator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

final class Repair {
    private static final Logger LOGGER = LoggerFactory.getLogger(Annihilator.MOD_ID);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    static final int MAX_BACKUPS = 20;

    private Repair() {
    }

    static void run(Path gameDir) {
        Path config = gameDir.resolve("config");
        Path templates = config.resolve(Annihilator.MOD_ID).resolve("defaults");
        Path backups = gameDir.resolve("config_doctor_backups").resolve(STAMP.format(LocalDateTime.now()));
        List<String> log = new ArrayList<>();
        int scannedCount = 0;

        try {
            List<Path> files = ConfigWalk.list(config);
            scannedCount = files.size();
            for (Path file : files) {
                if (!Integrity.isBroken(file)) {
                    continue;
                }
                Path rel = config.relativize(file);
                String name = rel.toString().replace('\\', '/');
                LOGGER.warn("Corrupt config detected: {}", name);
                if (backup(file, backups.resolve(rel), name, log)) {
                    restore(templates.resolve(rel), file, name, log);
                }
            }
        } catch (IOException e) {
            String err = "walk-fail " + e.getClass().getName() + " " + e.getMessage();
            log.add(err);
            LOGGER.error("Failed to walk config directory: {}", e.getMessage(), e);
        }

        if (log.isEmpty()) {
            LOGGER.info("Config scan complete. All {} configs healthy.", scannedCount);
        } else {
            LOGGER.info("Config scan complete. {} actions taken across {} configs.", log.size(), scannedCount);
            pruneOldBackups(gameDir.resolve("config_doctor_backups"));
        }

        write(gameDir.resolve("logs").resolve(Annihilator.MOD_ID + ".log"), log, scannedCount);
    }

    private static boolean backup(Path file, Path target, String name, List<String> log) {
        try {
            Files.createDirectories(target.getParent());
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            log.add("backup-fail " + name + " " + e.getClass().getName() + " " + e.getMessage());
            LOGGER.error("Failed to backup {}: {}", name, e.getMessage(), e);
            return false;
        }
    }

    private static void restore(Path template, Path file, String name, List<String> log) {
        if (!Files.isRegularFile(template)) {
            log.add("quarantine " + name);
            LOGGER.info("Quarantined {} (no clean template found in defaults)", name);
            return;
        }
        if (Integrity.isBroken(template)) {
            log.add("corrupt-template " + name);
            LOGGER.error("Default template for {} is also corrupt! Quarantining without restore.", name);
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.copy(template, file, StandardCopyOption.REPLACE_EXISTING);
            log.add("restore " + name);
            LOGGER.info("Restored {} from clean template", name);
        } catch (Exception e) {
            log.add("restore-fail " + name + " " + e.getClass().getName() + " " + e.getMessage());
            LOGGER.error("Failed to restore clean template for {}: {}", name, e.getMessage(), e);
        }
    }

    static void pruneOldBackups(Path backupsDir) {
        if (!Files.isDirectory(backupsDir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(backupsDir)) {
            List<Path> dirs = stream
                .filter(Files::isDirectory)
                .sorted((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()))
                .toList();
            if (dirs.size() > MAX_BACKUPS) {
                int toDelete = dirs.size() - MAX_BACKUPS;
                for (int i = 0; i < toDelete; i++) {
                    deleteRecursively(dirs.get(i));
                }
                LOGGER.info("Pruned {} old backup snapshot(s), keeping newest {}", toDelete, MAX_BACKUPS);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to prune old backups: {}", e.getMessage());
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void write(Path file, List<String> log, int scannedCount) {
        if (log.isEmpty()) {
            log.add("scan-ok scanned=" + scannedCount);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, log, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("{} log write failed: {}", Annihilator.MOD_ID, e.getMessage(), e);
        }
    }
}
