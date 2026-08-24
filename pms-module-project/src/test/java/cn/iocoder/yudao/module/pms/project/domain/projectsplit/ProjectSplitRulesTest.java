package cn.iocoder.yudao.module.pms.project.domain.projectsplit;

import cn.iocoder.yudao.module.pms.project.service.projectsplit.command.ProjectSplitDraftCommand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProjectSplitRulesTest {
    private final ProjectSplitRules rules = new ProjectSplitRules();

    @Test
    void shouldAllowFreeCombinationOfOrderQuantityOfficeAndSerials() {
        ProjectSplitDraftCommand command = new ProjectSplitDraftCommand(null, null, 100L, null, List.of(
                new ProjectSplitDraftCommand.Item("A", "子项目A", "L2", 1, "OFF-01", List.of(
                        new ProjectSplitDraftCommand.Scope(10L, new BigDecimal("2"), "OFF-01", List.of("SN-1", "SN-2")),
                        new ProjectSplitDraftCommand.Scope(11L, new BigDecimal("3.5"), null, List.of())))));

        assertTrue(rules.validate(command).isEmpty());
    }

    @Test
    void shouldRejectDuplicateSerialAcrossItems() {
        ProjectSplitDraftCommand command = new ProjectSplitDraftCommand(null, null, 100L, null, List.of(
                item("A", "SN-1"), item("B", "SN-1")));

        assertTrue(rules.validate(command).stream().anyMatch(error -> error.contains("DUPLICATE_OR_INVALID_SERIAL")));
    }

    private ProjectSplitDraftCommand.Item item(String key, String serial) {
        return new ProjectSplitDraftCommand.Item(key, "子项目" + key, null, 0, null, List.of(
                new ProjectSplitDraftCommand.Scope(10L, BigDecimal.ONE, null, List.of(serial))));
    }
}
