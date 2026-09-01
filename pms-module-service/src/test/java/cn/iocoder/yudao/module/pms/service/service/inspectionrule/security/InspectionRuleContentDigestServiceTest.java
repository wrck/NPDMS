package cn.iocoder.yudao.module.pms.service.service.inspectionrule.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectionRuleContentDigestServiceTest {

    private final InspectionRuleContentDigestService service = new InspectionRuleContentDigestService();

    @Test
    void shouldKeepDigestStableForSameBusinessContentRegardlessOfInputOrder() {
        String first = service.digest(content(commands(firstCommand(), secondCommand()), "OK"));
        String reordered = service.digest(content(commands(secondCommand(), firstCommand()), "OK"));

        assertEquals(first, reordered);
        assertTrue(first.matches("[0-9a-f]{64}"));
    }

    @Test
    void shouldChangeDigestWhenReviewedContentChanges() {
        String baseline = service.digest(content(commands(firstCommand(), secondCommand()), "OK"));

        assertNotEquals(baseline, service.digest(content(
                commands(command("show changed", 1, 10, true), secondCommand()), "OK")));
        assertNotEquals(baseline, service.digest(content(
                commands(command("show status", 2, 10, true), command("show alarm", 1, 20, false)), "OK")));
        assertNotEquals(baseline, service.digest(content(
                commands(command("show status", 1, 11, true), secondCommand()), "OK")));
        assertNotEquals(baseline, service.digest(content(
                commands(command("show status", 1, 10, false), secondCommand()), "OK")));
        assertNotEquals(baseline, service.digest(content(commands(firstCommand(), secondCommand()), "WARN")));
    }

    @Test
    void shouldRejectInvalidExecutionOrder() {
        assertThrows(IllegalArgumentException.class, () -> service.digest(content(
                commands(command("first", 1, 10, true), command("second", 1, 10, true)), "OK")));
        assertThrows(IllegalArgumentException.class, () -> service.digest(content(
                commands(command("first", 0, 10, true)), "OK")));
    }

    private static InspectionRuleContentDigestService.ReviewContent content(
            List<InspectionRuleContentDigestService.CommandContent> commands,
            String expectedResultRegex) {
        return new InspectionRuleContentDigestService.ReviewContent(commands, expectedResultRegex);
    }

    private static List<InspectionRuleContentDigestService.CommandContent> commands(
            InspectionRuleContentDigestService.CommandContent... commands) {
        return List.of(commands);
    }

    private static InspectionRuleContentDigestService.CommandContent firstCommand() {
        return command("show status", 1, 10, true);
    }

    private static InspectionRuleContentDigestService.CommandContent secondCommand() {
        return command("show alarm", 2, 20, false);
    }

    private static InspectionRuleContentDigestService.CommandContent command(
            String content,
            int executionOrder,
            int timeoutSeconds,
            boolean continueOnTimeout) {
        return new InspectionRuleContentDigestService.CommandContent(
                content, executionOrder, timeoutSeconds, continueOnTimeout);
    }
}
