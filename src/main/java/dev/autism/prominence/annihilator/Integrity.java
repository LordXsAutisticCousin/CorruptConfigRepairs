package dev.autism.prominence.annihilator;

import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.JsonParser;
import net.minecraft.nbt.StringNbtReader;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

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
            case "json" -> fails(() -> parseJson(body));
            case "json5", "jsonc" -> fails(() -> parseJson5(body));
            case "toml" -> fails(() -> new TomlParser().parse(new StringReader(body)));
            case "snbt" -> fails(() -> StringNbtReader.parse(body));
            case "properties" -> fails(() -> parseProperties(body));
            default -> false;
        };
    }

    private static void parseJson(String body) {
        JsonParser.parseString(body);
    }

    private static void parseProperties(String body) throws IOException {
        new Properties().load(new StringReader(body));
    }

    private static void parseJson5(String body) throws IOException {
        String cleaned = cleanJson5(body);
        if (cleaned.trim().isEmpty()) {
            throw new com.google.gson.JsonSyntaxException("Empty JSON5 content");
        }
        try (com.google.gson.stream.JsonReader reader =
            new com.google.gson.stream.JsonReader(new StringReader(cleaned))) {
            reader.setLenient(true);
            JsonParser.parseReader(reader);
            if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) {
                throw new com.google.gson.JsonSyntaxException("Trailing content after JSON5");
            }
        }
    }

    static String cleanJson5(String text) {
        // Pass 1: strip comments, validating matching quotes and closed block comments
        StringBuilder noComments = new StringBuilder(text.length());
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                noComments.append(c);
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
                noComments.append(c);
                continue;
            }
            if (c == '/' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                if (next == '/') {
                    while (i < text.length() && text.charAt(i) != '\n') {
                        i++;
                    }
                    noComments.append('\n');
                    continue;
                }
                if (next == '*') {
                    int end = text.indexOf("*/", i + 2);
                    if (end < 0) {
                        throw new IllegalArgumentException("Unclosed block comment in JSON5");
                    }
                    i = end + 1;
                    noComments.append(' ');
                    continue;
                }
            }
            noComments.append(c);
        }
        if (quote != 0) {
            throw new IllegalArgumentException("Unclosed string literal in JSON5");
        }

        // Pass 2: remove trailing commas before '}' or ']'
        String stripped = noComments.toString();
        StringBuilder out = new StringBuilder(stripped.length());
        quote = 0;
        escaped = false;
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
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
            if (c == ',') {
                int nextNonWs = -1;
                for (int j = i + 1; j < stripped.length(); j++) {
                    char ch = stripped.charAt(j);
                    if (!Character.isWhitespace(ch)) {
                        nextNonWs = j;
                        break;
                    }
                }
                if (nextNonWs != -1) {
                    char nextChar = stripped.charAt(nextNonWs);
                    if (nextChar == '}' || nextChar == ']') {
                        out.append(' ');
                        continue;
                    }
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    static String stripComments(String text) {
        return cleanJson5(text);
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
