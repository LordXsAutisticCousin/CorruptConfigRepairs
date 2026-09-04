package dev.autism.prominence.annihilator;

import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.JsonParser;
import net.minecraft.nbt.StringNbtReader;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class Integrity {
    private Integrity() {
    }

    static boolean isBroken(Path file) {
        byte[] data;
        try {
            data = Files.readAllBytes(file);
        } catch (IOException e) {
            return true;
        }
        if (data.length == 0) {
            return true;
        }
        for (byte b : data) {
            if (b == 0) {
                return true;
            }
        }

        String text = new String(data, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        String body = text;

        return switch (ConfigWalk.extension(file)) {
            case "json" -> fails(() -> JsonParser.parseString(body));
            case "json5", "jsonc" -> fails(() -> JsonParser.parseString(stripComments(body)));
            case "toml" -> fails(() -> new TomlParser().parse(new StringReader(body)));
            case "snbt" -> fails(() -> StringNbtReader.parse(body));
            default -> false;
        };
    }

    static String stripComments(String text) {
        StringBuilder out = new StringBuilder(text.length());
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                out.append(c);
                continue;
            }
            if (c == '/' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == '/') {
                    while (i < text.length() && text.charAt(i) != '\n') {
                        i++;
                    }
                    out.append('\n');
                    continue;
                }
                if (next == '*') {
                    int end = text.indexOf("*/", i + 2);
                    i = end < 0 ? text.length() : end + 1;
                    out.append(' ');
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean fails(Parser parser) {
        try {
            parser.parse();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private interface Parser {
        void parse() throws Exception;
    }
}
