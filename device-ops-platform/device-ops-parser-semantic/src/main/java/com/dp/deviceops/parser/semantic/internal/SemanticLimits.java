package com.dp.deviceops.parser.semantic.internal;

public record SemanticLimits(
        int maxRules,
        int maxProjectionFields,
        int maxRegexLength,
        int maxCommandBlocks,
        long maxBlockBytes,
        long maxTotalInputBytes,
        int maxFacts,
        long maxResultBytes,
        int maxGenericSections,
        int maxTableRows,
        int maxRecords,
        int maxStructureNodes) {

    public SemanticLimits(
            int maxRules,
            int maxProjectionFields,
            int maxRegexLength,
            int maxCommandBlocks,
            long maxBlockBytes,
            long maxTotalInputBytes,
            int maxFacts,
            long maxResultBytes) {
        this(maxRules, maxProjectionFields, maxRegexLength, maxCommandBlocks,
                maxBlockBytes, maxTotalInputBytes, maxFacts, maxResultBytes,
                20_000, 100_000, 20_000, 500_000);
    }

    public static SemanticLimits defaults() {
        return new SemanticLimits(2_000, 500, 2_048, 10_000,
                16L << 20, 128L << 20, 100_000, 64L << 20,
                20_000, 100_000, 20_000, 500_000);
    }
}
