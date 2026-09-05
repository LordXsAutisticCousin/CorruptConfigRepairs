package dev.autism.prominence.annihilator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnihilatorTest {

    @Test
    void readableFilesAreLeftAlone(@TempDir Path dir) throws Exception {
        assertFalse(Integrity.isBroken(write(dir, "a.json", "{\"url\": \"https://example.com\", \"n\": 1}")));
        assertFalse(Integrity.isBroken(write(dir, "b.json5", "// head\n{\n  key: 'value' // tail } \n}")));
        assertFalse(Integrity.isBroken(write(dir, "c.jsonc", "/* block { */\n{\"a\": [1, 2]}")));
        assertFalse(Integrity.isBroken(write(dir, "comma.jsonc", "{\n  \"a\": 1, // trailing comma supported\n}")));
        assertFalse(Integrity.isBroken(write(dir, "trailing_array.json5", "{\n  \"items\": [1, 2, ],\n}")));
        assertFalse(Integrity.isBroken(write(dir, "d.toml", "[general]\nkey = \"value\"\ncount = 10\n")));
        assertFalse(Integrity.isBroken(write(dir, "e.snbt", "{id: \"ftbquests:task\", count: 1b}")));
        assertFalse(Integrity.isBroken(write(dir, "f.properties", "a=1\nnot: toml = json\n")));
        assertFalse(Integrity.isBroken(write(dir, "g.cfg", "anything goes {[ here\n")));
    }

    @Test
    void unreadableFilesAreBroken(@TempDir Path dir) throws Exception {
        assertTrue(Integrity.isBroken(write(dir, "truncated.json", "{\n  \"key\": \"value\"")));
        assertTrue(Integrity.isBroken(write(dir, "trailing.json", "{\"a\": 1} garbage")));
        assertTrue(Integrity.isBroken(write(dir, "broken.json5", "/* head */\n{\"a\": }")));
        assertTrue(Integrity.isBroken(write(dir, "unclosed_block.json5", "{\"a\": 1} /* unclosed")));
        assertTrue(Integrity.isBroken(write(dir, "unclosed_str.json5", "{\"a\": \"unclosed}")));
        assertTrue(Integrity.isBroken(write(dir, "bad.toml", "[general\nkey = \n")));
        assertTrue(Integrity.isBroken(write(dir, "bad.snbt", "{id: \"task\", count:")));
        assertTrue(Integrity.isBroken(write(dir, "bad.properties", "key = \\u12xx")));
        assertTrue(Integrity.isBroken(bytes(dir, "empty.properties", new byte[0])));
        assertTrue(Integrity.isBroken(bytes(dir, "nulls.cfg", new byte[64])));
        assertTrue(Integrity.isBroken(bytes(dir, "half.json", new byte[]{'{', 0, '}'})));
    }

    @Test
    void walkSkipsSpammyDirectoriesAndForeignExtensions(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("config");
        write(config, "live.json", "{}");
        write(config.resolve("jei"), "ignored.json", "{}");
        write(config.resolve(Annihilator.MOD_ID).resolve("defaults"), "live.json", "{}");
        write(config, "notes.txt", "text");

        List<Path> found = ConfigWalk.list(config);

        assertEquals(1, found.size());
        assertEquals(config.resolve("live.json"), found.get(0));
    }

    @Test
    void brokenFileWithTemplateIsBackedUpAndRestored(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("config");
        Path live = write(config.resolve("puffish_skills"), "foo.json", "{ truncated");
        write(config.resolve(Annihilator.MOD_ID).resolve("defaults").resolve("puffish_skills"),
            "foo.json", "{\"pack\": true}");

        Repair.run(dir);

        assertEquals("{\"pack\": true}", Files.readString(live));
        assertTrue(log(dir).contains("restore puffish_skills/foo.json"));
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
        assertTrue(log(dir).contains("corrupt-template puffish_skills/foo.json"));
        assertEquals("{ broken", Files.readString(onlyBackup(dir).resolve("puffish_skills/foo.json")));
    }

    @Test
    void cleanRunWritesScanOkLog(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("config");
        write(config, "healthy.json", "{}");

        Repair.run(dir);

        assertEquals(List.of("scan-ok scanned=1"), log(dir));
    }

    @Test
    void oldBackupsArePrunedBeyondLimit(@TempDir Path dir) throws Exception {
        Path backupsDir = dir.resolve("config_doctor_backups");
        for (int i = 1; i <= 25; i++) {
            String stamp = String.format("20260101_%06d", i);
            write(backupsDir.resolve(stamp), "test.txt", "dummy");
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
        try (var stamps = Files.list(gameDir.resolve("config_doctor_backups"))) {
            return stamps.findFirst().orElseThrow();
        }
    }

    private static Path write(Path dir, String name, String text) throws Exception {
        Files.createDirectories(dir);
        return Files.writeString(dir.resolve(name), text);
    }

    private static Path bytes(Path dir, String name, byte[] data) throws Exception {
        Files.createDirectories(dir);
        return Files.write(dir.resolve(name), data);
    }
}
