package dev.autism.prominence.annihilator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class TestFiles {
    private TestFiles() {
    }

    static Path write(Path dir, String name, String text) throws IOException {
        Files.createDirectories(dir);
        return Files.writeString(dir.resolve(name), text);
    }

    static Path bytes(Path dir, String name, byte[] data) throws IOException {
        Files.createDirectories(dir);
        return Files.write(dir.resolve(name), data);
    }
}
