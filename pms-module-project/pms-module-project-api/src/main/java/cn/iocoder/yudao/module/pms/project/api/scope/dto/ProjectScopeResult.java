package cn.iocoder.yudao.module.pms.project.api.scope.dto;

import java.util.Set;

public record ProjectScopeResult(
        Long rootProjectId,
        Long treeVersion,
        Set<Long> fullProjectIds,
        Set<Long> placeholderProjectIds) {
}
