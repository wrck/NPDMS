package cn.iocoder.yudao.module.pms.project.api.workbinding.dto;

/** PRE-02冻结WorkBinding查询；tenantId与目标四元组均由PROJ受信边界固定。 */
public record ProjectWorkBindingFactQuery(Long projectId) {
}
