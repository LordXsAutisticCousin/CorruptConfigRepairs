package dev.autism.prominence.annihilator;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ConfigWalk {
    private static final Set<String> EXTENSIONS =
        Set.of("json", "json5", "jsonc", "toml", "snbt", "properties", "cfg");
    private static final Set<String> SKIPPED = Set.of(Annihilator.MOD_ID, "jei", "rei", "emi", "spark");

    private ConfigWalk() {
    }

    static List<Path> list(Path config) throws IOException {
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(config)) {
            return files;
        }
        Files.walkFileTree(config, new SimpleFileVisitor<>() {
            @Override
            public @NotNull FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) {
                if (SKIPPED.contains(name(dir))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && EXTENSIONS.contains(extension(name(file)))) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NotNull FileVisitResult visitFileFailed(@NotNull Path file, @NotNull IOException e) {
                return FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(files);
        return files;
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    static String name(Path path) {
        Path name = path.getFileName();
        return name == null ? "" : name.toString().toLowerCase(Locale.ROOT);
    }
}
