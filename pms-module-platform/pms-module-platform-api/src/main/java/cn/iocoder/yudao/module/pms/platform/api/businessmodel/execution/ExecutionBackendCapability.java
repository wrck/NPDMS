package cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution;

import java.util.List;

/**
 * 后端能力声明：可处理的过程/规则语义、版本、恢复与迁移能力。
 * 配置发布前校验；不支持的语义显式拒绝，不静默降级。
 */
public record ExecutionBackendCapability(
        String backendId,
        List<String> supportedSemantics,
        List<String> unsupportedSemantics,
        boolean supportsRecovery,
        boolean supportsInFlightMigration) {
}
