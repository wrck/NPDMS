package cn.iocoder.yudao.module.pms.project.domain.template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Framework-independent scenarios also invoked by the JUnit bridge. */
public final class TemplateExecutionContractSetScenarios {
    private TemplateExecutionContractSetScenarios() { }

    public static int runAll() {
        var a = new TemplateExecutionContractSet.Identity("a", 1L, 11L);
        var b = new TemplateExecutionContractSet.Identity("b", 2L, 12L);
        List<Case> cases = new ArrayList<>();
        cases.add(new Case("empty", () -> check(List.of(), List.of()), true));
        cases.add(new Case("exact", () -> check(List.of("a", "b"), List.of(a, b)), true));
        cases.add(new Case("order-independent", () -> check(List.of("a", "b"), List.of(b, a)), true));
        cases.add(new Case("null-expected", () -> check(null, List.of()), false));
        cases.add(new Case("null-actual", () -> check(List.of(), null), false));
        cases.add(new Case("missing-contract", () -> check(List.of("a", "b"), List.of(a)), false));
        cases.add(new Case("extra-contract", () -> check(List.of("a"), List.of(a, b)), false));
        cases.add(new Case("same-count-wrong-node", () -> check(List.of("a"), List.of(b)), false));
        cases.add(new Case("duplicate-template-key", () -> check(List.of("a", "a"), List.of(a, b)), false));
        cases.add(new Case("null-template-key", () -> check(Collections.singletonList(null), List.of(a)), false));
        cases.add(new Case("blank-template-key", () -> check(List.of(" "), List.of(a)), false));
        cases.add(new Case("null-contract", () -> check(List.of("a"), Collections.singletonList(null)), false));
        cases.add(new Case("duplicate-contract-node", () -> check(List.of("a", "b"), List.of(a, a)), false));
        cases.add(new Case("node-case-is-significant", () -> check(List.of("A"), List.of(a)), false));
        cases.add(new Case("node-whitespace-is-significant", () -> check(List.of(" a"), List.of(a)), false));
        cases.add(new Case("null-contract-key", () -> check(List.of("a"), List.of(
                new TemplateExecutionContractSet.Identity(null, 1L, 11L))), false));
        cases.add(new Case("duplicate-instance", () -> check(List.of("a", "b"), List.of(a,
                new TemplateExecutionContractSet.Identity("b", 1L, 12L))), false));
        cases.add(new Case("duplicate-contract-id", () -> check(List.of("a", "b"), List.of(a,
                new TemplateExecutionContractSet.Identity("b", 2L, 11L))), false));
        for (Long id : new Long[]{null, 0L, -1L}) {
            cases.add(new Case("invalid-instance-" + id, () -> check(List.of("a"), List.of(
                    new TemplateExecutionContractSet.Identity("a", id, 11L))), false));
            cases.add(new Case("invalid-contract-id-" + id, () -> check(List.of("a"), List.of(
                    new TemplateExecutionContractSet.Identity("a", 1L, id))), false));
        }
        cases.add(new Case("long-identities-are-lossless", () -> check(List.of("a"), List.of(
                new TemplateExecutionContractSet.Identity("a", Long.MAX_VALUE, Long.MAX_VALUE))), true));
        cases.add(new Case("does-not-mutate-input", () -> {
            var keys = new ArrayList<>(List.of("a", "b"));
            var rows = new ArrayList<>(List.of(b, a));
            check(keys, rows);
            if (!keys.equals(List.of("a", "b")) || !rows.equals(List.of(b, a))) {
                throw new AssertionError("inputs mutated");
            }
        }, true));
        for (Case scenario : cases) {
            try {
                scenario.action().run();
                if (!scenario.allowed()) throw new AssertionError(scenario.name() + " unexpectedly allowed");
            } catch (IllegalArgumentException rejected) {
                if (scenario.allowed()) throw new AssertionError(scenario.name(), rejected);
                if (!"FROZEN_CONTRACT_SET_INCOMPLETE".equals(rejected.getMessage())) {
                    throw new AssertionError(scenario.name() + " changed error contract", rejected);
                }
            }
        }
        return cases.size();
    }

    private static void check(List<String> keys, List<TemplateExecutionContractSet.Identity> rows) {
        TemplateExecutionContractSet.requireExact(keys, rows);
    }

    private record Case(String name, Runnable action, boolean allowed) { }

    public static void main(String[] args) {
        System.out.println("TemplateExecutionContractSet: " + runAll() + " scenarios passed");
    }
}
