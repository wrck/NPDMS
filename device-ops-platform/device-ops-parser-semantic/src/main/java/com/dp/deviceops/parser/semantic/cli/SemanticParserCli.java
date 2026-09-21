package com.dp.deviceops.parser.semantic.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dp.deviceops.core.model.CommandOutputBlock;
import com.dp.deviceops.parser.semantic.DefaultSemanticParser;
import com.dp.deviceops.parser.semantic.DefaultDynamicSemanticParser;
import com.dp.deviceops.parser.semantic.SemanticParseResult;
import com.dp.deviceops.parser.semantic.SemanticParserError;
import com.dp.deviceops.parser.semantic.internal.CanonicalJson;
import com.dp.deviceops.parser.semantic.plan.ParserPlan;
import com.dp.deviceops.parser.semantic.plan.ParserPlanCompiler;
import com.dp.deviceops.parser.semantic.release.ParserReleaseBundle;
import com.dp.deviceops.parser.semantic.release.ParserReleaseBundleCodec;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SemanticParserCli {

    private static final long MAX_INPUT_FILE_BYTES = 160L << 20;

    private SemanticParserCli() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    public static int run(String[] args, PrintStream stdout, PrintStream stderr) {
        try {
            Map<String, Path> options = parseArguments(args);
            Path inputPath = options.get("--input").toAbsolutePath().normalize();
            Path outputPath = options.get("--output").toAbsolutePath().normalize();
            if (!Files.isRegularFile(inputPath) || Files.size(inputPath) > MAX_INPUT_FILE_BYTES) {
                throw new SemanticParserError(SemanticParserError.INVALID_INPUT,
                        "input file is missing or exceeds the size limit");
            }
            CanonicalJson canonicalJson = new CanonicalJson();
            ObjectMapper mapper = canonicalJson.mapper();
            SemanticParseResult result = options.containsKey("--release-directory")
                    ? parseVersioned(options.get("--release-directory"), inputPath)
                    : parseBundled(mapper, inputPath);
            new AtomicJsonOutput().write(outputPath, canonicalJson.bytes(result));
            stdout.println("Structured result written to " + outputPath);
            return 0;
        } catch (ArgumentError error) {
            stderr.println("INVALID_ARGUMENT: " + error.getMessage());
            return 2;
        } catch (SemanticParserError error) {
            stderr.println(error.code() + ": " + error.getMessage());
            return exitCode(error.code());
        } catch (JsonProcessingException error) {
            stderr.println("INVALID_INPUT: input JSON is invalid");
            return 3;
        } catch (IOException error) {
            stderr.println("INVALID_INPUT: input or output file cannot be accessed");
            return 3;
        } catch (RuntimeException error) {
            stderr.println("INTERNAL_ERROR: semantic parsing failed");
            return 1;
        }
    }

    private static SemanticParseResult parseVersioned(Path releaseDirectory, Path inputPath) throws IOException {
        ParserReleaseBundle bundle = new ParserReleaseBundleCodec().decode(releaseDirectory);
        ParserPlan plan = new ParserPlanCompiler().compile(bundle);
        return new DefaultDynamicSemanticParser().parse(plan, () -> Files.newInputStream(inputPath));
    }

    private static SemanticParseResult parseBundled(ObjectMapper mapper, Path inputPath) throws IOException {
        SemanticParserInput input = mapper.readValue(inputPath.toFile(), SemanticParserInput.class);
        if (input == null || !"1.0.0".equals(input.schemaVersion()) || input.commandBlocks() == null) {
            throw new SemanticParserError(SemanticParserError.INVALID_INPUT,
                    "input schemaVersion or commandBlocks is invalid");
        }
        List<CommandOutputBlock> blocks;
        try {
            blocks = input.commandBlocks().stream().map(CommandOutputBlockInput::toDomain).toList();
        } catch (RuntimeException exception) {
            throw new SemanticParserError(SemanticParserError.INVALID_INPUT,
                    "command block fields are invalid", exception);
        }
        return DefaultSemanticParser.bundled().parse(
                blocks, DefaultSemanticParser.bundledSpecification());
    }

    private static Map<String, Path> parseArguments(String[] args) {
        if (args == null || (args.length != 4 && args.length != 6)) {
            throw new ArgumentError("--input and --output are required");
        }
        Map<String, Path> options = new HashMap<>();
        for (int index = 0; index < args.length; index += 2) {
            String name = args[index];
            if (!"--input".equals(name) && !"--output".equals(name)
                    && !"--release-directory".equals(name)) {
                throw new ArgumentError("unsupported option");
            }
            if (args[index + 1] == null || args[index + 1].isBlank()) {
                throw new ArgumentError("option value must not be blank");
            }
            if (options.put(name, Path.of(args[index + 1])) != null) {
                throw new ArgumentError("duplicate option");
            }
        }
        if (!options.keySet().containsAll(List.of("--input", "--output"))) {
            throw new ArgumentError("--input and --output are required");
        }
        if (args.length == 6 && !options.containsKey("--release-directory")) {
            throw new ArgumentError("--release-directory is required for versioned parsing");
        }
        return options;
    }

    private static int exitCode(String code) {
        return switch (code) {
            case SemanticParserError.INVALID_INPUT -> 3;
            case SemanticParserError.INVALID_RULES -> 4;
            case SemanticParserError.INVALID_PROJECTIONS -> 5;
            case SemanticParserError.RESOURCE_LIMIT -> 6;
            default -> 1;
        };
    }

    private static final class ArgumentError extends RuntimeException {
        private ArgumentError(String message) {
            super(message);
        }
    }
}
