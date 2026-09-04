package dev.autism.prominence.annihilator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

final class Repair {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private Repair() {
    }

    static void run(Path gameDir) {
        Path config = gameDir.resolve("config");
        Path templates = config.resolve(Annihilator.MOD_ID).resolve("defaults");
        Path backups = gameDir.resolve("config_doctor_backups").resolve(STAMP.format(LocalDateTime.now()));
        List<String> log = new ArrayList<>();

        try {
            for (Path file : ConfigWalk.list(config)) {
                if (!Integrity.isBroken(file)) {
                    continue;
                }
                Path rel = config.relativize(file);
                String name = rel.toString().replace('\\', '/');
                if (backup(file, backups.resolve(rel), name, log)) {
                    restore(templates.resolve(rel), file, name, log);
                }
            }
        } catch (IOException e) {
            log.add("walk-fail " + e.getClass().getName() + " " + e.getMessage());
        }

        write(gameDir.resolve("logs").resolve(Annihilator.MOD_ID + ".log"), log);
    }

    private static boolean backup(Path file, Path target, String name, List<String> log) {
        try {
            Files.createDirectories(target.getParent());
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            log.add("backup-fail " + name + " " + e.getClass().getName() + " " + e.getMessage());
            return false;
        }
    }

    private static void restore(Path template, Path file, String name, List<String> log) {
        if (!Files.isRegularFile(template)) {
            log.add("quarantine " + name);
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.copy(template, file, StandardCopyOption.REPLACE_EXISTING);
            log.add("restore " + name);
        } catch (Exception e) {
            log.add("restore-fail " + name + " " + e.getClass().getName() + " " + e.getMessage());
        }
    }

    private static void write(Path file, List<String> log) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, log, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println(Annihilator.MOD_ID + " log write failed "
                + e.getClass().getName() + " " + e.getMessage());
        }
    }
}
