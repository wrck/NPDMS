package cn.iocoder.yudao.module.pms.project.service.rule;

import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Operator syntax mapping only: Spring evaluates every comparison; no client expression is executed. */
final class RuleFieldComparison {
    private static final Map<String, Expression> EXPRESSIONS = expressions();
    private static final Map<String, Expression> QUANTIFIED_EXPRESSIONS = quantifiedExpressions();

    private RuleFieldComparison() { }

    static void validate(JsonNode parameters) {
        if (!parameters.path("fieldCode").isTextual() || parameters.path("fieldCode").asText().isBlank())
            throw new IllegalArgumentException("fieldCode required");
        String operator = parameters.path("operator").asText();
        if (!EXPRESSIONS.containsKey(operator)) throw new IllegalArgumentException("unsupported field operator");
        String quantifier = parameters.path("quantifier").asText();
        if (!quantifier.isEmpty() && !List.of("ALL", "ANY").contains(quantifier))
            throw new IllegalArgumentException("collection quantifier must be ALL or ANY");
        ValueType type = ValueType.valueOf(parameters.path("valueType").asText());
        if (List.of("contains", "beginsWith", "endsWith").contains(operator) && type != ValueType.TEXT)
            throw new IllegalArgumentException("string operator requires TEXT");
        if (List.of("<", "<=", ">", ">=", "between").contains(operator) && type == ValueType.BOOLEAN)
            throw new IllegalArgumentException("ordered operator cannot compare BOOLEAN");
        if (operator.equals("null") || operator.equals("notNull")) return;
        JsonNode value = parameters.path("value");
        if (value.isMissingNode()) throw new IllegalArgumentException("comparison value required");
        if (List.of("in", "notIn", "between").contains(operator)) {
            if (!value.isArray() || value.isEmpty() || (operator.equals("between") && value.size() != 2))
                throw new IllegalArgumentException("comparison requires a nonempty value list; between requires two values");
            for (JsonNode item : value) type.convert(item);
        } else {
            type.convert(value);
        }
    }

    static void validateValue(Object value, JsonNode parameters) {
        ValueType type = ValueType.valueOf(parameters.path("valueType").asText());
        if (!parameters.path("quantifier").asText().isEmpty()) {
            if (!(value instanceof List<?> collection)) throw new IllegalArgumentException("collection value required");
            collection.forEach(type::convert);
        } else {
            type.convert(value);
        }
    }

    static boolean evaluate(Object value, JsonNode parameters) {
        ValueType type = ValueType.valueOf(parameters.path("valueType").asText());
        String operator = parameters.path("operator").asText();
        // Only immutable scalar values enter this context. No Bean resolver, Java type access or user script.
        // https://docs.spring.io/spring-framework/reference/core/expressions/evaluation.html
        var context = SimpleEvaluationContext.forReadOnlyDataBinding().withInstanceMethods().build();
        String quantifier = parameters.path("quantifier").asText();
        if (!quantifier.isEmpty()) {
            if (!(value instanceof List<?> collection)) throw new IllegalArgumentException("collection value required");
            context.setVariable("values", collection.stream().map(type::convert).toList());
        } else {
            context.setVariable("left", type.convert(value));
        }
        JsonNode configured = parameters.path("value");
        if (configured.isArray()) {
            List<Object> values = new ArrayList<>();
            for (JsonNode item : configured) values.add(type.convert(item));
            context.setVariable("right", java.util.Collections.unmodifiableList(values));
        } else {
            context.setVariable("right", configured.isMissingNode() ? null : type.convert(configured));
        }
        Expression expression = quantifier.isEmpty() ? EXPRESSIONS.get(operator)
                : QUANTIFIED_EXPRESSIONS.get(quantifier + ":" + operator);
        return Boolean.TRUE.equals(expression.getValue(context, Boolean.class));
    }

    private static Map<String, Expression> expressions() {
        Map<String, String> syntax = new LinkedHashMap<>();
        syntax.put("=", "#left == #right");
        syntax.put("!=", "#left != #right");
        for (String op : List.of("<", "<=", ">", ">="))
            syntax.put(op, "#left != null and #right != null and #left " + op + " #right");
        syntax.put("contains", "#left != null and #right != null and #left.contains(#right)");
        syntax.put("beginsWith", "#left != null and #right != null and #left.startsWith(#right)");
        syntax.put("endsWith", "#left != null and #right != null and #left.endsWith(#right)");
        syntax.put("in", "#right.contains(#left)");
        syntax.put("notIn", "!#right.contains(#left)");
        syntax.put("between", "#left != null and #right[0] != null and #right[1] != null and #left >= #right[0] and #left <= #right[1]");
        syntax.put("null", "#left == null");
        syntax.put("notNull", "#left != null");
        var parser = new SpelExpressionParser();
        Map<String, Expression> result = new LinkedHashMap<>();
        syntax.forEach((key, expression) -> result.put(key, parser.parseExpression(expression)));
        return Map.copyOf(result);
    }

    private static Map<String, Expression> quantifiedExpressions() {
        var parser = new SpelExpressionParser();
        Map<String, Expression> result = new LinkedHashMap<>();
        EXPRESSIONS.forEach((operator, expression) -> {
            String selection = "#values.?[" + expression.getExpressionString().replace("#left", "#this") + "]";
            result.put("ANY:" + operator, parser.parseExpression(selection + ".size() > 0"));
            result.put("ALL:" + operator, parser.parseExpression("!#values.isEmpty() and "
                    + selection + ".size() == #values.size()"));
        });
        return Map.copyOf(result);
    }

    enum ValueType {
        TEXT, NUMBER, BOOLEAN, DATE, DATETIME;

        Object convert(Object raw) {
            if (raw == null || (raw instanceof JsonNode node && node.isNull())) return null;
            if (raw instanceof JsonNode node) {
                if (!node.isValueNode()) throw new IllegalArgumentException("scalar value required");
                raw = node.isBoolean() ? node.booleanValue()
                        : node.isNumber() ? node.decimalValue() : node.asText();
            }
            return switch (this) {
                case TEXT -> {
                    if (!(raw instanceof String)) throw new IllegalArgumentException("TEXT value required");
                    yield raw;
                }
                case NUMBER -> {
                    if (!(raw instanceof Number) && !(raw instanceof String))
                        throw new IllegalArgumentException("NUMBER value required");
                    yield raw instanceof BigDecimal decimal ? decimal : new BigDecimal(raw.toString());
                }
                case BOOLEAN -> {
                    if (!(raw instanceof Boolean)) throw new IllegalArgumentException("BOOLEAN value required");
                    yield raw;
                }
                case DATE -> raw instanceof LocalDate ? raw : LocalDate.parse(raw.toString());
                case DATETIME -> raw instanceof LocalDateTime ? raw : LocalDateTime.parse(raw.toString());
            };
        }
    }
}
