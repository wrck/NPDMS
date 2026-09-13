package cn.iocoder.yudao.module.pms.project.domain.rule;

/** A known null value is different from unavailable evidence. Values must come from the owning module. */
public record RuleFact(boolean available, Object value, String reasonCode, java.util.List<RuleDiagnostic> diagnostics) {
    public static RuleFact known(Object value) {
        return new RuleFact(true, value, null, java.util.List.of());
    }

    public static RuleFact unknown(String reasonCode) {
        return unknown(reasonCode, java.util.List.of());
    }

    public static RuleFact unknown(String reasonCode, java.util.List<RuleDiagnostic> diagnostics) {
        return new RuleFact(false, null, reasonCode, java.util.List.copyOf(diagnostics));
    }

    @FunctionalInterface
    public interface Resolver {
        RuleFact resolve(RuleProgram.Leaf input);
    }
}
