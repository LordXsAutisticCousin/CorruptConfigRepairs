package dev.autism.prominence.annihilator;

import com.electronwill.nightconfig.toml.TomlParser;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

import static com.fasterxml.jackson.core.JsonToken.START_ARRAY;
import static com.fasterxml.jackson.core.JsonToken.START_OBJECT;
import static com.google.gson.stream.JsonToken.BEGIN_ARRAY;
import static com.google.gson.stream.JsonToken.BEGIN_OBJECT;
import static com.google.gson.stream.JsonToken.END_DOCUMENT;

final class Integrity {
    private static final Set<String> CUSTOM_PARSER = Set.of("balm-client.toml", "balm-common.toml");
    private static final String SORTILEGE = ".sol.json";
    private static final JsonFactory JSON5 = JsonFactory.builder()
        .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
        .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
        .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
        .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
        .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
        .enable(JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS)
        .enable(JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)
        .enable(JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)
        .enable(JsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS)
        .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
        .build();

    private Integrity() {
    }

    // Files.readString() is strict UTF-8; some mods write Cp1252 and a MalformedInputException would quarantine healthy files
    @SuppressWarnings("ReadWriteStringCanBeUsed")
    static boolean isBroken(Path file) {
        String text;
        try {
            text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return true;
        }
        if (text.indexOf('\0') >= 0) {
            return true;
        }
        String name = ConfigWalk.name(file);
        if (CUSTOM_PARSER.contains(name) || name.endsWith(SORTILEGE)) {
            return false;
        }
        String body = text.startsWith("\uFEFF") ? text.substring(1) : text;
        return switch (ConfigWalk.extension(name)) {
            case "json" -> fails(() -> parseJson(body));
            case "json5", "jsonc" -> fails(() -> parseJson5(body));
            case "toml" -> fails(() -> new TomlParser().parse(new StringReader(body)));
            case "properties" -> fails(() -> new Properties().load(new StringReader(body)));
            default -> false;
        };
    }

    // Same lenient reader Gson-based mods use, but streamed: no DOM per file and no recursion on deep nesting
    private static void parseJson(String body) throws IOException {
        try (JsonReader reader = new JsonReader(new StringReader(body))) {
            reader.setLenient(true);
            var root = reader.peek();
            if (root != BEGIN_OBJECT && root != BEGIN_ARRAY) {
                throw new IOException("JSON root must be an object or array");
            }
            reader.skipValue();
            if (reader.peek() != END_DOCUMENT) {
                throw new IOException("Trailing content after JSON document");
            }
        }
    }

    private static void parseJson5(String body) throws IOException {
        try (var parser = JSON5.createParser(body)) {
            var root = parser.nextToken();
            if (root != START_OBJECT && root != START_ARRAY) {
                throw new IOException("JSON5 root must be an object or array");
            }
            parser.skipChildren();
            if (parser.nextToken() != null) {
                throw new IOException("Trailing content after JSON5 document");
            }
        }
    }

    private static boolean fails(Check check) {
        try {
            check.run();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    @FunctionalInterface
    private interface Check {
        void run() throws Exception;
    }
}
