package cn.iocoder.yudao.module.pms.service.domain.inspection;

import cn.iocoder.yudao.module.pms.service.domain.inspectionrule.InspectionRuleRegexValidator;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure evaluation of matched, frozen command outputs; does not identify devices or read business data. */
public final class InspectionResultEvaluator {
    // Technical resource limits only; none changes a rule's business threshold or permits truncated evaluation.
    private static final int MAX_OUTPUT_CHARS = 262_144;
    private static final int MAX_COMMANDS = 1024;
    private static final int MAX_STRUCTURED_VALUES = 1024;
    private static final int MAX_NUMBER_CHARS = 4096;
    private static final long MAX_REGEX_CHAR_READS = 1_000_000;
    private static final long MAX_REGEX_NANOS = 250_000_000;
    private static final Set<String> OPERATORS = Set.of(">", "<", "≥", "≤", "=", "≠");
    private static final Pattern NUMBER = Pattern.compile(
            "\\A\\s*+([+-]?(?:[0-9]++(?:\\.[0-9]*+)?|\\.[0-9]++)(?:[eE][+-]?[0-9]++)?)"
                    + "\\s*+(1|[^\\s0-9+\\-.][^\\s]*+)?\\s*+\\z");
    private final InspectionRuleRegexValidator regexValidator = new InspectionRuleRegexValidator();

    @Builder
    public record Input(String expectedRegex, String thresholdOperator, BigDecimal thresholdValue,
                        String thresholdUnit, List<CommandOutput> commands) { }

    public record CommandOutput(String commandText, String status, String structureStatus, String output,
                                List<String> structuredValues, boolean present) { }

    public record Evaluation(String outcome, BigDecimal measuredValue, String measuredUnit, Boolean regexMatched,
                             String detailCode, String detailMessage) { }

    public Evaluation evaluate(Input input) {
        if (input == null) return result(new Decision("ERROR", "INPUT_INVALID", "判定输入不能为空"));
        if (input.commands() == null || input.commands().isEmpty()) {
            return result(new Decision("UNJUDGED", "COMMAND_MISSING", "没有完整命令输出"));
        }
        if (input.commands().size() > MAX_COMMANDS) return outputLimit();
        // A proved execution failure takes priority over missing outputs of other commands.
        for (CommandOutput command : input.commands()) {
            if (command != null && "FAILED".equals(command.status())) {
                return result(new Decision("ERROR", "COMMAND_FAILED", "命令执行失败"));
            }
            if (command != null && "TIMED_OUT".equals(command.status())) {
                return result(new Decision("ERROR", "COMMAND_TIMED_OUT", "命令执行超时"));
            }
        }
        long chars = input.commands().size() - 1L;
        long values = 0;
        for (CommandOutput command : input.commands()) {
            if (command == null || !command.present()) {
                return result(new Decision("UNJUDGED", "COMMAND_MISSING", "缺少冻结命令对应的输出"));
            }
            if (!"SUCCEEDED".equals(command.status())) {
                return result(new Decision("UNJUDGED", "COMMAND_NOT_SUCCEEDED", "命令尚无正式成功结果"));
            }
            if (command.output() == null || command.output().isBlank()) {
                return result(new Decision("UNJUDGED", "OUTPUT_EMPTY", "命令输出为空"));
            }
            if ("EMPTY".equals(command.structureStatus())) {
                return result(new Decision("UNJUDGED", "STRUCTURE_EMPTY", "解析输出为空，不能判定"));
            }
            if ("LIMITED".equals(command.structureStatus())) {
                return result(new Decision("UNJUDGED", "STRUCTURE_LIMITED", "解析输出受限，不能判定"));
            }
            chars += command.output().length();
            if (command.structuredValues() != null) {
                values += command.structuredValues().size();
                if (values > MAX_STRUCTURED_VALUES) return outputLimit();
                for (String value : command.structuredValues()) if (value != null) chars += value.length();
            }
            if (chars > MAX_OUTPUT_CHARS) return outputLimit();
        }
        if (!regexValidator.validate(input.expectedRegex()).isEmpty()) {
            return result(new Decision("ERROR", "REGEX_INVALID", "预期正则不符合已批准的受限正则规则"));
        }
        if (input.thresholdOperator() == null || !OPERATORS.contains(input.thresholdOperator())
                || input.thresholdValue() == null || input.thresholdUnit() == null || input.thresholdUnit().isBlank()) {
            return result(new Decision("ERROR", "THRESHOLD_INVALID", "数值阈值、比较运算符及单位必须完整"));
        }
        String merged = String.join("\n", input.commands().stream().map(CommandOutput::output).toList());
        boolean matched;
        try {
            RegexBudget budget = new RegexBudget();
            matched = Pattern.compile(input.expectedRegex()).matcher(new BudgetText(merged, budget)).find();
            budget.check();
        } catch (RegexLimit | StackOverflowError limit) {
            return result(new Decision("ERROR", "REGEX_LIMIT", "正则匹配超过技术资源限制，未形成通过判定"));
        }
        if (!matched) {
            return new Evaluation("ABNORMAL", null, null, false, "REGEX_NOT_MATCHED", "完整命令输出与预期模式不匹配");
        }
        try {
            return numeric(input);
        } catch (NumericLimit limit) {
            return new Evaluation("UNJUDGED", null, null, true, "OUTPUT_LIMIT", "完整数值超过技术资源限制，未判为通过");
        }
    }

    private static Evaluation numeric(Input input) {
        List<Measurement> measurements = new ArrayList<>();
        for (CommandOutput command : input.commands()) {
            // Only whole parsed values and whole standard numeric outputs are eligible, never regex groups or substrings.
            if (command.structuredValues() != null) {
                for (String value : command.structuredValues()) {
                    Measurement measurement = measurement(value);
                    if (measurement != null) measurements.add(measurement);
                }
            }
            Measurement measurement = measurement(command.output());
            if (measurement != null) measurements.add(measurement);
        }
        if (measurements.isEmpty()) {
            return new Evaluation("UNJUDGED", null, null, true, "VALUE_MISSING", "没有完整的单一标准数值与单位");
        }
        String expectedUnit = input.thresholdUnit().trim();
        Measurement first = measurements.getFirst();
        for (Measurement value : measurements) {
            String unit = value.unit();
            if (unit == null && "1".equals(expectedUnit)) unit = "1";
            if (unit == null) {
                return new Evaluation("UNJUDGED", null, null, true, "UNIT_MISSING", "数值没有明确单位");
            }
            String firstUnit = first.unit() == null && "1".equals(expectedUnit) ? "1" : first.unit();
            if (first.value().compareTo(value.value()) != 0 || !unit.equals(firstUnit)) {
                return new Evaluation("UNJUDGED", null, null, true, "VALUE_AMBIGUOUS", "存在不同数值或不同单位，不能任选或换算");
            }
        }
        String measuredUnit = first.unit() == null ? "1" : first.unit();
        if (!expectedUnit.equals(measuredUnit)) {
            return new Evaluation("UNJUDGED", first.value(), measuredUnit, true, "UNIT_MISMATCH", "检测单位与规则单位不完全一致");
        }
        int comparison = first.value().compareTo(input.thresholdValue());
        boolean passes = switch (input.thresholdOperator()) {
            case ">" -> comparison > 0;
            case "<" -> comparison < 0;
            case "≥" -> comparison >= 0;
            case "≤" -> comparison <= 0;
            case "=" -> comparison == 0;
            case "≠" -> comparison != 0;
            default -> throw new IllegalStateException("Validated threshold operator changed");
        };
        return new Evaluation(passes ? "PASS" : "ABNORMAL", first.value(), measuredUnit, true,
                passes ? "MATCHED" : "THRESHOLD_NOT_MET", passes ? "预期模式与数值阈值均满足" : "数值不满足规则阈值");
    }

    private record Measurement(BigDecimal value, String unit) { }

    private static Measurement measurement(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher matcher = NUMBER.matcher(text);
        if (!matcher.matches()) return null;
        if (matcher.group(1).length() > MAX_NUMBER_CHARS) throw new NumericLimit();
        try {
            return new Measurement(new BigDecimal(matcher.group(1)), matcher.group(2));
        } catch (NumberFormatException invalidNumber) {
            return null;
        }
    }

    private record Decision(String outcome, String code, String message) { }

    private static Evaluation result(Decision decision) {
        return new Evaluation(decision.outcome(), null, null, null, decision.code(), decision.message());
    }

    private static Evaluation outputLimit() {
        return result(new Decision("UNJUDGED", "OUTPUT_LIMIT", "输出超过技术资源限制，未截断或判为通过"));
    }

    private static final class RegexLimit extends RuntimeException {
        RegexLimit() { super("Regex resource limit", null, false, false); }
    }

    private static final class NumericLimit extends RuntimeException {
        NumericLimit() { super("Numeric resource limit", null, false, false); }
    }

    private static final class RegexBudget {
        private final long startedAt = System.nanoTime();
        private long reads;

        void read() {
            if (++reads > MAX_REGEX_CHAR_READS) throw new RegexLimit();
            check();
        }

        void check() {
            if (System.nanoTime() - startedAt > MAX_REGEX_NANOS) throw new RegexLimit();
        }
    }

    private static final class BudgetText implements CharSequence {
        private final TextSlice slice;
        private final RegexBudget budget;

        BudgetText(String text, RegexBudget budget) { this(new TextSlice(text, 0, text.length()), budget); }

        private BudgetText(TextSlice slice, RegexBudget budget) {
            this.slice = slice;
            this.budget = budget;
        }

        @Override public int length() { budget.check(); return slice.end() - slice.start(); }

        @Override public char charAt(int index) {
            Objects.checkIndex(index, slice.end() - slice.start());
            budget.read();
            return slice.text().charAt(slice.start() + index);
        }

        @Override public CharSequence subSequence(int from, int to) {
            Objects.checkFromToIndex(from, to, slice.end() - slice.start());
            budget.check();
            return new BudgetText(new TextSlice(slice.text(), slice.start() + from, slice.start() + to), budget);
        }

        @Override public String toString() {
            StringBuilder result = new StringBuilder(slice.end() - slice.start());
            for (int i = 0; i < slice.end() - slice.start(); i++) result.append(charAt(i));
            return result.toString();
        }
    }

    private record TextSlice(String text, int start, int end) { }
}
