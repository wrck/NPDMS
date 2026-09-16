package com.dp.plat.lowcode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.plat.lowcode.entity.LowCodeConfigAuditLog;

/**
 * 低代码配置审计日志服务（缺口2）。
 *
 * <p>提供审计日志的写入与查询能力。写入由 AOP 切面
 * {@code ConfigAuditAspect} 自动触发，业务代码无需显式调用。</p>
 */
public interface LowCodeConfigAuditLogService extends IService<LowCodeConfigAuditLog> {

    /**
     * 记录一条配置审计日志（best-effort，失败不抛异常）。
     *
     * @param actor       操作人
     * @param configType  配置类型（ENTITY/FORM/LIST/MICROFLOW/RULE/CONNECTOR/...）
     * @param configId    配置ID（可为 null）
     * @param configCode  配置编码（可为 null）
     * @param action      动作（CREATE/UPDATE/DELETE/PUBLISH/ROLLBACK/PROMOTE）
     * @param before      操作前对象（可为 null，将序列化为 JSON）
     * @param after       操作后对象（可为 null，将序列化为 JSON）
     * @param diffSummary 变更摘要（可为 null）
     */
    void record(String actor, String configType, Long configId, String configCode,
                String action, Object before, Object after, String diffSummary);
}
