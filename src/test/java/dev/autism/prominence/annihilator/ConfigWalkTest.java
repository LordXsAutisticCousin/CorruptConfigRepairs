package dev.autism.prominence.annihilator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static dev.autism.prominence.annihilator.TestFiles.write;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigWalkTest {

    @Test
    void skipsSpammyDirectoriesAndForeignExtensions(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("config");
        write(config, "live.json", "{}");
        write(config.resolve("jei"), "ignored.json", "{}");
        write(config.resolve(Annihilator.MOD_ID).resolve("defaults"), "live.json", "{}");
        write(config, "notes.txt", "text");

        assertEquals(List.of(config.resolve("live.json")), ConfigWalk.list(config));
    }

    @Test
    void returnsSortedPathsAndHandlesMissingDirectory(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("config");
        Path zeta = write(config, "zeta.toml", "");
        Path alpha = write(config.resolve("alpha"), "DEEP.JSON", "");
        Path beta = write(config, "beta.properties", "");

        assertEquals(List.of(alpha, beta, zeta), ConfigWalk.list(config));
        assertEquals("deep.json", ConfigWalk.name(alpha));
        assertEquals("json", ConfigWalk.extension(ConfigWalk.name(alpha)));
        assertTrue(ConfigWalk.list(dir.resolve("absent")).isEmpty());
    }
}
