package cn.iocoder.yudao.module.pms.project.domain.satisfaction;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** ACC-02 schemaVersion=1 questionnaire definition and deterministic scorer. */
public final class SatisfactionQuestionnaireDefinition {

    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "questions", "scoring");
    private static final Set<String> SCORING_FIELDS = Set.of(
            "ruleVersion", "strategy", "scoreMin", "scoreMax", "precision", "roundingMode", "threshold");
    private static final Set<String> ANSWER_ROOT_FIELDS = Set.of("answers");
    private static final Set<String> ANSWER_FIELDS = Set.of("questionCode", "value");
    private static final Set<String> OPTION_FIELDS = Set.of("code", "label", "score");
    private static final Set<String> QUESTION_TYPES = Set.of(
            "SINGLE_CHOICE", "MULTIPLE_CHOICE", "RATING", "TEXT");
    private static final Set<String> STRATEGIES = Set.of("SUM_V1", "WEIGHTED_AVERAGE_V1");
    private static final Set<String> ROUNDING_MODES = Set.of("HALF_UP", "HALF_EVEN", "DOWN");

    private final List<Question> questions;
    private final Map<String, Question> questionsByCode;
    private final String ruleVersion;
    private final String strategy;
    private final int precision;
    private final RoundingMode roundingMode;
    private final BigDecimal threshold;

    private SatisfactionQuestionnaireDefinition(List<Question> questions, String ruleVersion,
                                                 String strategy, int precision, RoundingMode roundingMode,
                                                 BigDecimal threshold) {
        this.questions = List.copyOf(questions);
        this.questionsByCode = new HashMap<>();
        questions.forEach(question -> this.questionsByCode.put(question.code(), question));
        this.ruleVersion = ruleVersion;
        this.strategy = strategy;
        this.precision = precision;
        this.roundingMode = roundingMode;
        this.threshold = threshold;
    }

    public static SatisfactionQuestionnaireDefinition parse(String json) {
        Map<?, ?> root = parseMap(json, "SATISFACTION_QUESTIONNAIRE_CONFIG_INVALID");
        requireFields(root, ROOT_FIELDS);
        if (integer(root.get("schemaVersion")) != 1) invalid();

        Map<?, ?> scoring = map(root.get("scoring"));
        requireFields(scoring, SCORING_FIELDS);
        String ruleVersion = text(scoring.get("ruleVersion"));
        String strategy = text(scoring.get("strategy"));
        if (!STRATEGIES.contains(strategy)) invalid();
        BigDecimal scoreMin = decimal(scoring.get("scoreMin"));
        BigDecimal scoreMax = decimal(scoring.get("scoreMax"));
        BigDecimal threshold = decimal(scoring.get("threshold"));
        int precision = integer(scoring.get("precision"));
        String rounding = text(scoring.get("roundingMode"));
        if (scoreMin.signum() != 0 || scoreMax.signum() < 0 || threshold.signum() < 0
                || threshold.compareTo(scoreMax) > 0 || precision < 0 || precision > 2
                || !ROUNDING_MODES.contains(rounding)) invalid();

        List<?> rawQuestions = list(root.get("questions"));
        if (rawQuestions.isEmpty()) invalid();
        List<Question> questions = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (Object rawQuestion : rawQuestions) {
            Question question = parseQuestion(map(rawQuestion), strategy);
            if (!codes.add(question.code())) invalid();
            questions.add(question);
        }
        List<Question> scored = questions.stream().filter(Question::scored).toList();
        if (scored.isEmpty()) invalid();
        Fraction derivedMax = aggregate(scored, strategy, Question::maximumScore);
        if (!derivedMax.equals(Fraction.from(scoreMax))) invalid();
        return new SatisfactionQuestionnaireDefinition(
                questions, ruleVersion, strategy, precision, RoundingMode.valueOf(rounding), threshold);
    }

    public Evaluation evaluate(String answerJson, boolean signatureValid) {
        Map<?, ?> root = parseMap(answerJson, "SATISFACTION_ANSWER_INVALID");
        requireAnswerFields(root, ANSWER_ROOT_FIELDS);
        List<?> rawAnswers = list(root.get("answers"));
        Map<String, Fraction> scores = new HashMap<>();
        Set<String> answered = new HashSet<>();
        for (Object rawAnswer : rawAnswers) {
            Map<?, ?> answer = mapAnswer(rawAnswer);
            requireAnswerFields(answer, ANSWER_FIELDS);
            String code = answerText(answer.get("questionCode"));
            Question question = questionsByCode.get(code);
            if (question == null || !answered.add(code)) answerInvalid();
            scores.put(code, evaluate(question, answer.get("value")));
        }
        boolean requiredComplete = questions.stream().filter(Question::required)
                .allMatch(question -> answered.contains(question.code()));
        Fraction score = aggregate(questions.stream().filter(Question::scored).toList(), strategy,
                question -> scores.getOrDefault(question.code(), Fraction.ZERO));
        BigDecimal rounded = score.toBigDecimal(precision, roundingMode);
        return new Evaluation(rounded, threshold, requiredComplete && signatureValid
                && rounded.compareTo(threshold) >= 0, requiredComplete, ruleVersion);
    }

    public String ruleVersion() {
        return ruleVersion;
    }

    public BigDecimal threshold() {
        return threshold;
    }

    private static Question parseQuestion(Map<?, ?> raw, String strategy) {
        String type = text(raw.get("type"));
        if (!QUESTION_TYPES.contains(type)) invalid();
        Set<String> fields = new HashSet<>(Set.of("code", "title", "type", "required"));
        boolean scored = !"TEXT".equals(type);
        if (scored) fields.add("options");
        if ("MULTIPLE_CHOICE".equals(type)) {
            fields.add("minSelections");
            fields.add("maxSelections");
        }
        if ("TEXT".equals(type)) {
            fields.add("minLength");
            fields.add("maxLength");
        }
        if (scored && "WEIGHTED_AVERAGE_V1".equals(strategy)) fields.add("weight");
        requireFields(raw, fields);
        String code = text(raw.get("code"));
        text(raw.get("title"));
        boolean required = bool(raw.get("required"));
        List<Option> options = scored ? parseOptions(raw.get("options")) : List.of();
        int minSelections = 0;
        int maxSelections = 0;
        int minLength = 0;
        int maxLength = 0;
        if ("MULTIPLE_CHOICE".equals(type)) {
            minSelections = integer(raw.get("minSelections"));
            maxSelections = integer(raw.get("maxSelections"));
            if (minSelections < 1 || minSelections > maxSelections || maxSelections > options.size()) invalid();
        }
        if ("TEXT".equals(type)) {
            minLength = integer(raw.get("minLength"));
            maxLength = integer(raw.get("maxLength"));
            if (minLength < 0 || minLength > maxLength) invalid();
        }
        Fraction weight = scored && "WEIGHTED_AVERAGE_V1".equals(strategy)
                ? Fraction.from(decimal(raw.get("weight"))) : Fraction.ONE;
        if (weight.signum() <= 0) invalid();
        Fraction maximum = scored ? maximumScore(type, options, minSelections, maxSelections) : Fraction.ZERO;
        return new Question(code, type, required, options, minSelections, maxSelections,
                minLength, maxLength, weight, maximum, scored);
    }

    private static List<Option> parseOptions(Object value) {
        List<?> rawOptions = list(value);
        if (rawOptions.isEmpty()) invalid();
        List<Option> options = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (Object rawOption : rawOptions) {
            Map<?, ?> option = map(rawOption);
            requireFields(option, OPTION_FIELDS);
            String code = text(option.get("code"));
            text(option.get("label"));
            BigDecimal score = decimal(option.get("score"));
            if (score.signum() < 0 || !codes.add(code)) invalid();
            options.add(new Option(code, Fraction.from(score)));
        }
        return List.copyOf(options);
    }

    private static Fraction maximumScore(String type, List<Option> options, int min, int max) {
        if (!"MULTIPLE_CHOICE".equals(type)) {
            return options.stream().map(Option::score).max(Fraction::compareTo).orElseThrow();
        }
        List<Fraction> descending = options.stream().map(Option::score)
                .sorted(Comparator.reverseOrder()).toList();
        Fraction best = Fraction.ZERO;
        Fraction sum = Fraction.ZERO;
        for (int index = 0; index < max; index++) {
            sum = sum.add(descending.get(index));
            int selected = index + 1;
            if (selected >= min) best = best.max(sum.divide(Fraction.of(selected)));
        }
        return best;
    }

    private static Fraction evaluate(Question question, Object value) {
        if ("TEXT".equals(question.type())) {
            if (!(value instanceof String)) answerInvalid();
            String text = (String) value;
            int length = text.codePointCount(0, text.length());
            if (length < question.minLength() || length > question.maxLength()) answerInvalid();
            return Fraction.ZERO;
        }
        if ("MULTIPLE_CHOICE".equals(question.type())) {
            List<?> values = answerList(value);
            if (values.size() < question.minSelections() || values.size() > question.maxSelections()) answerInvalid();
            Set<String> selected = new HashSet<>();
            Fraction total = Fraction.ZERO;
            for (Object item : values) {
                String code = answerText(item);
                if (!selected.add(code)) answerInvalid();
                total = total.add(question.scoreOf(code));
            }
            return total.divide(Fraction.of(values.size()));
        }
        return question.scoreOf(answerText(value));
    }

    private static Fraction aggregate(List<Question> questions, String strategy,
                                      java.util.function.Function<Question, Fraction> value) {
        Fraction numerator = Fraction.ZERO;
        Fraction denominator = Fraction.ZERO;
        for (Question question : questions) {
            if ("SUM_V1".equals(strategy)) numerator = numerator.add(value.apply(question));
            else {
                numerator = numerator.add(value.apply(question).multiply(question.weight()));
                denominator = denominator.add(question.weight());
            }
        }
        return "SUM_V1".equals(strategy) ? numerator : numerator.divide(denominator);
    }

    private static Map<?, ?> parseMap(String json, String error) {
        Map<?, ?> value = JsonUtils.parseObjectQuietly(json, Map.class);
        if (value == null) throw new IllegalArgumentException(error);
        return value;
    }

    private static Map<?, ?> map(Object value) {
        if (!(value instanceof Map<?, ?>)) invalid();
        return (Map<?, ?>) value;
    }

    private static Map<?, ?> mapAnswer(Object value) {
        if (!(value instanceof Map<?, ?>)) answerInvalid();
        return (Map<?, ?>) value;
    }

    private static List<?> list(Object value) {
        if (!(value instanceof List<?>)) invalid();
        return (List<?>) value;
    }

    private static List<?> answerList(Object value) {
        if (!(value instanceof List<?>)) answerInvalid();
        return (List<?>) value;
    }

    private static void requireFields(Map<?, ?> value, Set<String> fields) {
        if (!value.keySet().equals(fields)) invalid();
    }

    private static void requireAnswerFields(Map<?, ?> value, Set<String> fields) {
        if (!value.keySet().equals(fields)) answerInvalid();
    }

    private static String text(Object value) {
        if (!(value instanceof String) || ((String) value).isBlank()) invalid();
        return (String) value;
    }

    private static String answerText(Object value) {
        if (!(value instanceof String) || ((String) value).isBlank()) answerInvalid();
        return (String) value;
    }

    private static int integer(Object value) {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger)) invalid();
        Number number = (Number) value;
        long integer = number.longValue();
        if (integer < Integer.MIN_VALUE || integer > Integer.MAX_VALUE) invalid();
        return (int) integer;
    }

    private static boolean bool(Object value) {
        if (!(value instanceof Boolean)) invalid();
        return (Boolean) value;
    }

    private static BigDecimal decimal(Object value) {
        if (!(value instanceof String)) invalid();
        try {
            BigDecimal decimal = new BigDecimal((String) value);
            if (decimal.scale() < 0 || decimal.scale() > 2 || decimal.precision() > 7) invalid();
            return decimal;
        } catch (NumberFormatException ignored) {
            invalid();
            return BigDecimal.ZERO;
        }
    }

    private static void invalid() {
        throw new IllegalArgumentException("SATISFACTION_QUESTIONNAIRE_CONFIG_INVALID");
    }

    private static void answerInvalid() {
        throw new IllegalArgumentException("SATISFACTION_ANSWER_INVALID");
    }

    public record Evaluation(BigDecimal score, BigDecimal threshold, boolean passed,
                             boolean requiredComplete, String ruleVersion) {}

    private record Option(String code, Fraction score) {}

    private record Question(String code, String type, boolean required, List<Option> options,
                            int minSelections, int maxSelections, int minLength, int maxLength,
                            Fraction weight, Fraction maximumScore, boolean scored) {
        Fraction scoreOf(String optionCode) {
            return options.stream().filter(option -> option.code().equals(optionCode)).findFirst()
                    .map(Option::score).orElseThrow(() -> new IllegalArgumentException("SATISFACTION_ANSWER_INVALID"));
        }
    }

    private record Fraction(BigInteger numerator, BigInteger denominator) implements Comparable<Fraction> {
        private static final Fraction ZERO = new Fraction(BigInteger.ZERO, BigInteger.ONE);
        private static final Fraction ONE = new Fraction(BigInteger.ONE, BigInteger.ONE);

        Fraction {
            if (denominator.signum() == 0) invalid();
            if (denominator.signum() < 0) {
                numerator = numerator.negate();
                denominator = denominator.negate();
            }
            BigInteger gcd = numerator.gcd(denominator);
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);
        }

        static Fraction of(int value) {
            return new Fraction(BigInteger.valueOf(value), BigInteger.ONE);
        }

        static Fraction from(BigDecimal value) {
            return new Fraction(value.unscaledValue(), BigInteger.TEN.pow(value.scale()));
        }

        Fraction add(Fraction other) {
            return new Fraction(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                    denominator.multiply(other.denominator));
        }

        Fraction multiply(Fraction other) {
            return new Fraction(numerator.multiply(other.numerator), denominator.multiply(other.denominator));
        }

        Fraction divide(Fraction other) {
            return new Fraction(numerator.multiply(other.denominator), denominator.multiply(other.numerator));
        }

        Fraction max(Fraction other) {
            return compareTo(other) >= 0 ? this : other;
        }

        int signum() {
            return numerator.signum();
        }

        BigDecimal toBigDecimal(int scale, RoundingMode roundingMode) {
            return new BigDecimal(numerator).divide(new BigDecimal(denominator), scale, roundingMode);
        }

        @Override
        public int compareTo(Fraction other) {
            return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
        }
    }
}
