package cn.iocoder.yudao.module.pms.project.api.reference.dto;

public record ProjectAncestorQuery(
        Long tenantId,
        Long projectId,
        Long treeVersion) {
}
