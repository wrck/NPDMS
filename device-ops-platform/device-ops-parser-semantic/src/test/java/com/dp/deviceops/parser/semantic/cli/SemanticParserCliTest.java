package com.dp.deviceops.parser.semantic.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticParserCliTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void writesExactlyOneDeterministicResultFile() throws Exception {
        Path input = fixture();
        Path output = temporaryDirectory.resolve("result.json");
        RunResult first = run(input, output);
        byte[] firstBytes = Files.readAllBytes(output);
        RunResult second = run(input, output);

        assertEquals(0, first.exitCode());
        assertEquals(0, second.exitCode());
        assertArrayEquals(firstBytes, Files.readAllBytes(output));
        assertTrue(new String(firstBytes, StandardCharsets.UTF_8).contains("\"deviceBasic\""));
        assertEquals(1, Files.list(temporaryDirectory).filter(Files::isRegularFile).count());
    }

    @Test
    void failedInputPreservesPreviousOutputAndDoesNotLeakContent() throws Exception {
        Path output = temporaryDirectory.resolve("result.json");
        Files.writeString(output, "previous-complete-output", StandardCharsets.UTF_8);
        Path invalid = temporaryDirectory.resolve("invalid.json");
        Files.writeString(invalid, "{\"password\":\"forbidden-value\"", StandardCharsets.UTF_8);

        RunResult result = run(invalid, output);

        assertEquals(3, result.exitCode());
        assertEquals("previous-complete-output", Files.readString(output, StandardCharsets.UTF_8));
        assertFalse(result.stderr().contains("forbidden-value"));
        assertEquals(2, Files.list(temporaryDirectory).filter(Files::isRegularFile).count());
    }

    @Test
    void rejectsDuplicateAndUnknownArguments() {
        RunResult duplicate = run(new String[]{"--input", "a", "--input", "b"});
        RunResult unknown = run(new String[]{"--input", "a", "--unknown", "b"});

        assertEquals(2, duplicate.exitCode());
        assertEquals(2, unknown.exitCode());
    }

    @Test
    void parsesWithAnExplicitVersionedRelease() throws Exception {
        Path release = release("device-show-tech-1.0.0");
        Path input = release.resolve("input.json");
        Path output = temporaryDirectory.resolve("versioned-result.json");

        RunResult result = run(new String[]{"--release-directory", release.toString(),
                "--input", input.toString(), "--output", output.toString()});

        assertEquals(0, result.exitCode(), result.stderr());
        assertArrayEquals(Files.readAllBytes(release.resolve("expected-result.json")),
                Files.readAllBytes(output));
    }

    private RunResult run(Path input, Path output) {
        return run(new String[]{"--input", input.toString(), "--output", output.toString()});
    }

    private RunResult run(String[] arguments) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = SemanticParserCli.run(arguments,
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
        return new RunResult(exitCode, stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    private static Path fixture() throws Exception {
        return Path.of(SemanticParserCliTest.class.getResource(
                "/fixtures/command-output-blocks.json").toURI());
    }

    private static Path release(String name) throws Exception {
        return Path.of(SemanticParserCliTest.class.getResource("/releases/" + name).toURI());
    }

    private record RunResult(int exitCode, String stdout, String stderr) {
    }
}
