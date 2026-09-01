package cn.iocoder.yudao.module.pms.service.domain.inspectionrule;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class InspectionRuleRegexValidator {

    private static final int MAX_LENGTH = 1024;
    private static final int MAX_GROUPS = 32;
    private static final int MAX_GROUP_DEPTH = 8;
    private static final int MAX_BRANCHES = 31;
    private static final int MAX_QUANTIFIERS = 64;
    private static final int MAX_INTERVAL_UPPER_BOUND = 1000;

    public List<InspectionRuleRevisionRules.ValidationError> validate(String expression) {
        if (expression == null || expression.isBlank()) {
            return List.of(error("REQUIRED"));
        }
        if (expression.length() > MAX_LENGTH) {
            return List.of(error("REGEX_TOO_LONG"));
        }
        ScanResult scanResult = scan(expression);
        if (scanResult.unsupported()) {
            return List.of(error("REGEX_UNSUPPORTED_FEATURE"));
        }
        if (scanResult.complexityExceeded()) {
            return List.of(error("REGEX_COMPLEXITY_EXCEEDED"));
        }
        try {
            Pattern.compile(expression);
            return List.of();
        } catch (PatternSyntaxException exception) {
            return List.of(error("INVALID_SYNTAX"));
        }
    }

    private static ScanResult scan(String expression) {
        Deque<GroupState> groups = new ArrayDeque<>();
        groups.push(new GroupState());
        int groupCount = 0;
        int branchCount = 0;
        int quantifierCount = 0;
        boolean unsupported = false;
        boolean complexityExceeded = false;
        int index = globalFlagsEnd(expression);
        if (index < 0) {
            return new ScanResult(true, false);
        }
        while (index < expression.length()) {
            char current = expression.charAt(index);
            if (current == '\\') {
                if (index + 1 < expression.length()) {
                    char escaped = expression.charAt(index + 1);
                    if (Character.isDigit(escaped) || escaped == 'k' || escaped == 'g' || escaped == 'K') {
                        unsupported = true;
                    }
                    if (escaped == 'Q') {
                        int quotedEnd = expression.indexOf("\\E", index + 2);
                        index = quotedEnd < 0 ? expression.length() : quotedEnd + 2;
                        groups.peek().lastAtom = new AtomState(false, false, false);
                        continue;
                    }
                    index += 2;
                    groups.peek().lastAtom = new AtomState(false, false, false);
                    continue;
                }
                index++;
                continue;
            }
            if (current == '[') {
                index = characterClassEnd(expression, index + 1);
                groups.peek().lastAtom = new AtomState(false, false, false);
                continue;
            }
            if (current == '(') {
                if (expression.startsWith("(*", index)) {
                    unsupported = true;
                }
                GroupPrefix prefix = groupPrefix(expression, index);
                if (prefix.unsupported()) {
                    unsupported = true;
                }
                if (prefix.globalFlags()) {
                    unsupported = true;
                    index = prefix.endIndex();
                    continue;
                }
                groupCount++;
                if (groupCount > MAX_GROUPS || groups.size() > MAX_GROUP_DEPTH) {
                    complexityExceeded = true;
                }
                groups.push(new GroupState());
                index = prefix.endIndex();
                continue;
            }
            if (current == ')') {
                if (groups.size() > 1) {
                    GroupState completed = groups.pop();
                    GroupState parent = groups.peek();
                    parent.containsQuantifier |= completed.containsQuantifier;
                    parent.containsBranch |= completed.containsBranch;
                    parent.lastAtom = new AtomState(
                            completed.containsQuantifier,
                            completed.containsBranch,
                            false);
                }
                index++;
                continue;
            }
            if (current == '|') {
                branchCount++;
                if (branchCount > MAX_BRANCHES) {
                    complexityExceeded = true;
                }
                GroupState group = groups.peek();
                group.containsBranch = true;
                group.lastAtom = null;
                index++;
                continue;
            }
            Quantifier quantifier = quantifier(expression, index);
            if (quantifier != null) {
                quantifierCount++;
                if (quantifierCount > MAX_QUANTIFIERS) {
                    complexityExceeded = true;
                }
                if (quantifier.unboundedInterval() || quantifier.upperBoundExceeded()) {
                    unsupported = true;
                }
                GroupState group = groups.peek();
                AtomState atom = group.lastAtom;
                if (atom != null) {
                    if (atom.quantified() || atom.containsQuantifier() || atom.containsBranch()) {
                        unsupported = true;
                    }
                    group.lastAtom = new AtomState(
                            atom.containsQuantifier(),
                            atom.containsBranch(),
                            true);
                }
                group.containsQuantifier = true;
                index = quantifier.endIndex();
                continue;
            }
            groups.peek().lastAtom = new AtomState(false, false, false);
            index++;
        }
        return new ScanResult(unsupported, complexityExceeded);
    }

    private static int globalFlagsEnd(String expression) {
        if (!expression.startsWith("(?")) {
            return 0;
        }
        int closing = expression.indexOf(')', 2);
        if (closing < 0) {
            return 0;
        }
        String flags = expression.substring(2, closing);
        if (flags.isEmpty() || flags.indexOf(':') >= 0 || flags.indexOf('-') >= 0) {
            return 0;
        }
        boolean i = false;
        boolean m = false;
        boolean s = false;
        for (int index = 0; index < flags.length(); index++) {
            switch (flags.charAt(index)) {
                case 'i' -> {
                    if (i) {
                        return -1;
                    }
                    i = true;
                }
                case 'm' -> {
                    if (m) {
                        return -1;
                    }
                    m = true;
                }
                case 's' -> {
                    if (s) {
                        return -1;
                    }
                    s = true;
                }
                default -> {
                    return 0;
                }
            }
        }
        return closing + 1;
    }

    private static GroupPrefix groupPrefix(String expression, int index) {
        if (index + 1 >= expression.length() || expression.charAt(index + 1) != '?') {
            return new GroupPrefix(index + 1, false, false);
        }
        if (expression.startsWith("(?:", index)) {
            return new GroupPrefix(index + 3, false, false);
        }
        int closing = expression.indexOf(')', index + 2);
        if (closing > index + 2) {
            String flags = expression.substring(index + 2, closing);
            if (flags.matches("[ims]+")) {
                return new GroupPrefix(closing + 1, true, true);
            }
        }
        return new GroupPrefix(Math.min(index + 3, expression.length()), true, false);
    }

    private static int characterClassEnd(String expression, int index) {
        boolean escaped = false;
        while (index < expression.length()) {
            char current = expression.charAt(index++);
            if (escaped) {
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == ']') {
                break;
            }
        }
        return index;
    }

    private static Quantifier quantifier(String expression, int index) {
        char current = expression.charAt(index);
        if (current == '*' || current == '+' || current == '?') {
            return new Quantifier(quantifierModifierEnd(expression, index + 1), false, false);
        }
        if (current != '{') {
            return null;
        }
        int closing = expression.indexOf('}', index + 1);
        if (closing < 0) {
            return null;
        }
        String bounds = expression.substring(index + 1, closing);
        if (!bounds.matches("\\d+(?:,\\d*)?")) {
            return null;
        }
        int comma = bounds.indexOf(',');
        boolean unbounded = comma >= 0 && comma == bounds.length() - 1;
        boolean upperBoundExceeded = false;
        if (!unbounded) {
            String upperBound = comma < 0 ? bounds : bounds.substring(comma + 1);
            try {
                upperBoundExceeded = Integer.parseInt(upperBound) > MAX_INTERVAL_UPPER_BOUND;
            } catch (NumberFormatException exception) {
                upperBoundExceeded = true;
            }
        }
        return new Quantifier(
                quantifierModifierEnd(expression, closing + 1),
                unbounded,
                upperBoundExceeded);
    }

    private static int quantifierModifierEnd(String expression, int index) {
        if (index < expression.length()) {
            char modifier = expression.charAt(index);
            if (modifier == '?' || modifier == '+') {
                return index + 1;
            }
        }
        return index;
    }

    private static InspectionRuleRevisionRules.ValidationError error(String code) {
        return new InspectionRuleRevisionRules.ValidationError("expectedResultRegex", code, code);
    }

    private static final class GroupState {
        private boolean containsQuantifier;
        private boolean containsBranch;
        private AtomState lastAtom;
    }

    private record AtomState(
            boolean containsQuantifier,
            boolean containsBranch,
            boolean quantified) {
    }

    private record GroupPrefix(
            int endIndex,
            boolean unsupported,
            boolean globalFlags) {
    }

    private record Quantifier(
            int endIndex,
            boolean unboundedInterval,
            boolean upperBoundExceeded) {
    }

    private record ScanResult(
            boolean unsupported,
            boolean complexityExceeded) {
    }
}
