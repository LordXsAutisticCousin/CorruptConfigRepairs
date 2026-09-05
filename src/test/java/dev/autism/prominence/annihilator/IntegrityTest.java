package dev.autism.prominence.annihilator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static dev.autism.prominence.annihilator.TestFiles.bytes;
import static dev.autism.prominence.annihilator.TestFiles.write;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrityTest {

    @Test
    void readableFilesAreLeftAlone(@TempDir Path dir) throws Exception {
        assertFalse(Integrity.isBroken(write(dir, "a.json", "{\"url\": \"https://example.com\", \"n\": 1}")));
        assertFalse(Integrity.isBroken(write(dir, "array.json", "[\"minecraft:stone\", \"minecraft:dirt\"]")));
        assertFalse(Integrity.isBroken(write(dir, "utf8.json", "{\"name\": \"Épée ☃ 日本\"}")));
        assertFalse(Integrity.isBroken(write(dir, "bom.json", "\uFEFF{\"a\": 1}")));
        assertFalse(Integrity.isBroken(write(dir, "commented.json", "{ // gson-lenient, as mods read it\n  \"a\": 1\n}")));
        assertFalse(Integrity.isBroken(write(dir, "b.json5", "// head\n{\n  key: 'value' // tail } \n}")));
        assertFalse(Integrity.isBroken(write(dir, "tab.json5", "{\"desc\": \"line one\tline two\"}")));
        assertFalse(Integrity.isBroken(write(dir, "numbers.json5", "{a: +1, b: .5, c: 5., d: NaN, e: Infinity, f: 007}")));
        assertFalse(Integrity.isBroken(write(dir, "c.jsonc", "/* block { */\n{\"a\": [1, 2]}")));
        assertFalse(Integrity.isBroken(write(dir, "comma.jsonc", "{\n  \"a\": 1, // trailing comma supported\n}")));
        assertFalse(Integrity.isBroken(write(dir, "comma_block.json5", "{\n  \"a\": 1, /* block */ \n}")));
        assertFalse(Integrity.isBroken(write(dir, "trailing_array.json5", "{\n  \"items\": [1, 2, ],\n}")));
        assertFalse(Integrity.isBroken(write(dir, "array.json5", "[/* ids */ 'a', 'b',]")));
        assertFalse(Integrity.isBroken(write(dir, "d.toml", "[general]\nkey = \"value\"\ncount = 10\n")));
        assertFalse(Integrity.isBroken(write(dir, "f.properties", "a=1\nnot: toml = json\n")));
        assertFalse(Integrity.isBroken(write(dir, "g.cfg", "anything goes {[ here\n")));
    }

    @Test
    void realWorldQuirksAreLeftAlone(@TempDir Path dir) throws Exception {
        // FTB Quests / Crash Assistant write with the platform charset (Cp1252 'ñ' = 0xF1) and read it back the same way
        assertFalse(Integrity.isBroken(bytes(dir, "ftbqt-cache.json",
            new byte[]{'{', '"', 'E', 's', 'p', 'a', (byte) 0xF1, 'o', 'l', '"', ':', '1', '}'})));
        // Create: Estrogen ships zero-byte TOML files; night-config reads them as an empty config
        assertFalse(Integrity.isBroken(bytes(dir, "estrogen-client.toml", new byte[0])));
        assertFalse(Integrity.isBroken(bytes(dir, "empty.properties", new byte[0])));
        assertFalse(Integrity.isBroken(write(dir, "bom_only.cfg", "\uFEFF")));
        // FTB Library SNBT dialect: '#' comments and no commas between entries
        assertFalse(Integrity.isBroken(write(dir, "chapter_groups.snbt",
            "{\n\t# comment\n\tchapter_groups: [\n\t\t{ id: \"5EA21B61\", title: \"&6Tutorials\" }\n\t\t{ id: \"3DBADFFD\", title: \"&6Campaign\" }\n\t]\n}\n")));
        // Balm serialises resource-location sets as bare words, which is not spec TOML, and parses them itself
        assertFalse(Integrity.isBroken(write(dir, "balm-client.toml",
            "\n# This is an example resource location set property\nexampleResourceLocationSet = [minecraft:dirt, minecraft:diamond]\n")));
        // Sortilege's *.sol.json is its own format with stray top-level values
        assertFalse(Integrity.isBroken(write(dir, "sortilege.sol.json",
            "// This config file uses a custom defined parser.\n\nversion: 9.0\nreset: false\n\n\"enchanting\": {\n  \"default\": 6\n}\n")));
    }

    @Test
    void unreadableFilesAreBroken(@TempDir Path dir) throws Exception {
        assertTrue(Integrity.isBroken(write(dir, "truncated.json", "{\n  \"key\": \"value\"")));
        assertTrue(Integrity.isBroken(write(dir, "trailing.json", "{\"a\": 1} garbage")));
        assertTrue(Integrity.isBroken(write(dir, "trailing.json5", "{\"a\": 1} garbage")));
        assertTrue(Integrity.isBroken(write(dir, "word.json", "garbage")));
        assertTrue(Integrity.isBroken(write(dir, "scalar.json", "\"just a string\"")));
        assertTrue(Integrity.isBroken(write(dir, "scalar.json5", "// comment\n42")));
        assertTrue(Integrity.isBroken(write(dir, "missing_colon.json", "{\"a\" 1}")));
        assertTrue(Integrity.isBroken(write(dir, "missing_comma.json", "{\"a\": 1 \"b\": 2}")));
        assertTrue(Integrity.isBroken(write(dir, "unclosed_str.json", "{\"a\": \"unclosed}")));
        assertTrue(Integrity.isBroken(write(dir, "broken.json5", "/* head */\n{\"a\": }")));
        assertTrue(Integrity.isBroken(write(dir, "unclosed_block.json5", "{\"a\": 1} /* unclosed")));
        assertTrue(Integrity.isBroken(write(dir, "unclosed_str.json5", "{\"a\": \"unclosed}")));
        assertTrue(Integrity.isBroken(write(dir, "bad.toml", "[general\nkey = \n")));
        assertTrue(Integrity.isBroken(write(dir, "bad.properties", "key = \\u12xx")));
        assertTrue(Integrity.isBroken(bytes(dir, "empty.json", new byte[0])));
        assertTrue(Integrity.isBroken(write(dir, "blank.json", " \n\t\n")));
        assertTrue(Integrity.isBroken(write(dir, "blank.json5", "// only a comment\n")));
        assertTrue(Integrity.isBroken(write(dir, "bom_only.json", "\uFEFF")));
        assertTrue(Integrity.isBroken(dir.resolve("missing.json")));
        assertTrue(Integrity.isBroken(bytes(dir, "nulls.cfg", new byte[64])));
        assertTrue(Integrity.isBroken(bytes(dir, "half.json", new byte[]{'{', 0, '}'})));
        assertTrue(Integrity.isBroken(bytes(dir, "nulls.snbt", new byte[]{'{', 0, '}'})));
        assertTrue(Integrity.isBroken(bytes(dir, "sortilege.sol.json", new byte[]{'v', 0})));
        assertTrue(Integrity.isBroken(bytes(dir, "balm-common.toml", new byte[]{'a', 0})));
    }

    @Test
    void deeplyNestedTruncationIsBrokenWithoutStackOverflow(@TempDir Path dir) throws Exception {
        String deep = "[".repeat(100_000);
        assertTrue(Integrity.isBroken(write(dir, "deep.json", deep)));
        assertTrue(Integrity.isBroken(write(dir, "deep.json5", deep)));
    }
}
