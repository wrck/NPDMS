package com.dp.deviceops.core.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandPlanTest {

    @Test
    void normalizesEveryNonBlankLineIntoOneOrderedCommand() {
        CommandPlan plan = CommandPlan.fromScript("  first  \r\n\r\n second-long\r third \n");

        assertEquals(List.of(
                new CommandPlan.CommandSpec(1, "first"),
                new CommandPlan.CommandSpec(2, "second-long"),
                new CommandPlan.CommandSpec(3, "third")), plan.commands());
    }

    @Test
    void rejectsEmptyScriptsAndInvalidPlans() {
        assertThrows(IllegalArgumentException.class, () -> CommandPlan.fromScript(" \r\n\t"));
        assertThrows(IllegalArgumentException.class, () -> new CommandPlan(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CommandPlan(List.of(
                new CommandPlan.CommandSpec(2, "first"))));
    }

    @Test
    void exposesImmutableCommandAndEvidenceCollections() {
        CommandPlan plan = CommandPlan.fromScript("first");
        CommandOutputBlock legacy = CommandOutputBlock.legacy(
                "out", "err", 0, false, Map.of("version", "1"), null);

        assertThrows(UnsupportedOperationException.class,
                () -> plan.commands().add(new CommandPlan.CommandSpec(2, "second")));
        assertTrue(legacy.legacy());
        assertEquals("", legacy.commandText());
        assertEquals(Map.of("version", "1"), legacy.parsedFacts());
        assertThrows(UnsupportedOperationException.class,
                () -> legacy.parsedFacts().put("vendor", "x"));
    }
}
