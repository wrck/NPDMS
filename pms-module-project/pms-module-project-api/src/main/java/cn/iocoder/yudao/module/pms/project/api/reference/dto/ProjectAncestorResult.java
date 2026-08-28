package cn.iocoder.yudao.module.pms.project.api.reference.dto;

import java.util.List;

public record ProjectAncestorResult(
        Long projectId,
        Long rootProjectId,
        Long treeVersion,
        List<Long> ancestorProjectIds) {
}
