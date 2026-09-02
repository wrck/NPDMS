package cn.iocoder.yudao.module.pms.service.service.inspectionrule.security;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Service
public class InspectionRuleContentDigestService {

    public String digest(ReviewContent content) {
        validate(content);
        StringBuilder canonical = new StringBuilder();
        content.commands().stream()
                .sorted(Comparator.comparingInt(CommandContent::executionOrder))
                .forEach(command -> {
                    append(canonical, "command.executionOrder", command.executionOrder());
                    append(canonical, "command.content", command.content());
                    append(canonical, "command.timeoutSeconds", command.timeoutSeconds());
                    append(canonical, "command.continueOnTimeout", command.continueOnTimeout());
                });
        append(canonical, "expectedResultRegex", content.expectedResultRegex());
        return sha256(canonical.toString());
    }

    private static void validate(ReviewContent content) {
        if (content == null) {
            throw new IllegalArgumentException("review content required");
        }
        Set<Integer> orders = new HashSet<>();
        for (CommandContent command : content.commands()) {
            if (command.executionOrder() < 1 || !orders.add(command.executionOrder())) {
                throw new IllegalArgumentException("command execution order must be positive and unique");
            }
        }
    }

    private static void append(StringBuilder canonical, String name, Object value) {
        String text = value == null ? "" : value.toString();
        canonical.append(name.length()).append(':').append(name)
                .append('=').append(text.length()).append(':').append(text).append('\n');
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256摘要算法不可用", exception);
        }
    }

    public record ReviewContent(
            List<CommandContent> commands,
            String expectedResultRegex) {

        public ReviewContent {
            commands = commands == null ? List.of() : List.copyOf(commands);
        }
    }

    public record CommandContent(
            String content,
            int executionOrder,
            int timeoutSeconds,
            boolean continueOnTimeout) {
    }
}
