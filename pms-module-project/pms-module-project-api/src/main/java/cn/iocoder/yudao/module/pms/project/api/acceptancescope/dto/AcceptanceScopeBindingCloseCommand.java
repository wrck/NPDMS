package cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto;

/**
 * 关闭项目既有验收范围锁定（Q-FCOM-002 裁决：退出或回退验收阶段即关闭解锁）。
 * operationId 须携带调用方动作标识（如治理动作编号），用于审计对应。
 */
public record AcceptanceScopeBindingCloseCommand(
        Long tenantId,
        Long projectId,
        String operationId) {
}
