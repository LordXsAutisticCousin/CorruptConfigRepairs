package dev.autism.prominence.annihilator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static dev.autism.prominence.annihilator.TestFiles.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairTest {

    @Test
    void brokenFileWithTemplateIsBackedUpAndRestored(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("config");
        Path live = write(config.resolve("puffish_skills"), "foo.json", "{ truncated");
        write(config.resolve(Annihilator.MOD_ID).resolve("defaults").resolve("puffish_skills"),
            "foo.json", "{\"pack\": true}");

        Repair.run(dir);

        assertEquals("{\"pack\": true}", Files.readString(live));
        assertEquals(List.of("restore puffish_skills/foo.json"), log(dir));
        assertEquals("{ truncated", Files.readString(onlyBackup(dir).resolve("puffish_skills/foo.json")));
    }

    @Test
    void brokenFileWithoutTemplateIsQuarantined(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("config");
        Path live = write(config, "orphan.toml", "[general\n");
        Path healthy = write(config, "healthy.toml", "[general]\nkey = 1\n");

        Repair.run(dir);

        assertFalse(Files.exists(live));
        assertTrue(Files.exists(healthy));
        assertEquals(List.of("quarantine orphan.toml"), log(dir));
        assertEquals("[general\n", Files.readString(onlyBackup(dir).resolve("orphan.toml")));
    }

    @Test
    void brokenTemplateIsNotRestored(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("config");
        Path live = write(config.resolve("puffish_skills"), "foo.json", "{ broken");
        write(config.resolve(Annihilator.MOD_ID).resolve("defaults").resolve("puffish_skills"),
            "foo.json", "{ also broken");

        Repair.run(dir);

        assertFalse(Files.exists(live));
        assertEquals(List.of("corrupt-template puffish_skills/foo.json"), log(dir));
        assertEquals("{ broken", Files.readString(onlyBackup(dir).resolve("puffish_skills/foo.json")));
    }

    @Test
    void cleanRunWritesScanOkLogAndCreatesNoBackups(@TempDir Path dir) throws Exception {
        write(dir.resolve("config"), "healthy.json", "{}");

        Repair.run(dir);

        assertEquals(List.of("scan-ok scanned=1"), log(dir));
        assertFalse(Files.exists(dir.resolve(Repair.BACKUPS_DIR)));
    }

    @Test
    void missingConfigDirectoryIsNotAnError(@TempDir Path dir) throws Exception {
        Repair.run(dir);

        assertEquals(List.of("scan-ok scanned=0"), log(dir));
    }

    @Test
    void runWithActionsPrunesOldSnapshots(@TempDir Path dir) throws Exception {
        Path backupsDir = dir.resolve(Repair.BACKUPS_DIR);
        for (int i = 1; i <= Repair.MAX_BACKUPS; i++) {
            write(backupsDir.resolve(String.format("20200101_%06d", i)), "old.json", "{");
        }
        write(dir.resolve("config"), "broken.json", "{");

        Repair.run(dir);

        try (var stream = Files.list(backupsDir)) {
            List<Path> remaining = stream.sorted().toList();
            assertEquals(Repair.MAX_BACKUPS, remaining.size());
            assertEquals("20200101_000002", remaining.get(0).getFileName().toString());
            assertTrue(Files.exists(remaining.get(Repair.MAX_BACKUPS - 1).resolve("broken.json")));
        }
    }

    @Test
    void oldBackupsArePrunedBeyondLimit(@TempDir Path dir) throws Exception {
        Path backupsDir = dir.resolve(Repair.BACKUPS_DIR);
        for (int i = 1; i <= 25; i++) {
            String stamp = String.format("20260101_%06d", i);
            write(backupsDir.resolve(stamp).resolve("nested").resolve("deeper"), "test.txt", "dummy");
        }

        Repair.pruneOldBackups(backupsDir);

        try (var stream = Files.list(backupsDir)) {
            List<Path> remaining = stream.sorted().toList();
            assertEquals(Repair.MAX_BACKUPS, remaining.size());
            assertEquals("20260101_000006", remaining.get(0).getFileName().toString());
            assertEquals("20260101_000025", remaining.get(Repair.MAX_BACKUPS - 1).getFileName().toString());
        }
    }

    private static List<String> log(Path gameDir) throws Exception {
        return Files.readAllLines(gameDir.resolve("logs").resolve(Annihilator.MOD_ID + ".log"));
    }

    private static Path onlyBackup(Path gameDir) throws Exception {
        try (var stamps = Files.list(gameDir.resolve(Repair.BACKUPS_DIR))) {
            return stamps.findFirst().orElseThrow();
        }
    }
}
