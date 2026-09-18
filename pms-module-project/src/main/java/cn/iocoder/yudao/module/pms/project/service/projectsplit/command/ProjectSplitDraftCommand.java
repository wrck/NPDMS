package cn.iocoder.yudao.module.pms.project.service.projectsplit.command;

import java.math.BigDecimal;
import java.util.List;

public record ProjectSplitDraftCommand(Long requestId, Integer expectedDraftVersion, Long parentProjectId,
                                       Long templateRevisionId, List<Item> items) {
    public record Item(String clientItemKey, String projectName, String businessLevelCode, Integer treeSort,
                       String departmentCode, List<Scope> scopes,
                       Long templateRevisionId, String templateSelectionReason) {}
    public record Scope(Long orderLineId, BigDecimal quantity, String departmentCode,
                        List<String> serialNumbers) {}
}
