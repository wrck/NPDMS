package cn.iocoder.yudao.module.pms.platform.api.spi;

import cn.iocoder.yudao.module.pms.platform.api.spi.dto.TemplateSnapshot;

import java.util.List;
import java.util.Map;

/**
 * 审批计划批量注册 SPI（迁移自源工程 pms-common com.dp.plat.common.spi.ApprovalPlanBatchCreator，
 * TD-P8-003 模板深拷贝用）。
 *
 * <p>从模板创建项目时，pms-module-project 通过本 SPI 跨模块调用 pms-module-workflow
 * 注册审批计划（按阶段触发审批），避免 pms-module-project 直接调用审批中心内部 API。</p>
 *
 * <p>由 pms-module-workflow 模块实现并注册为 Spring Bean，
 * pms-module-project 通过 {@code @Autowired(required=false)} 注入。
 * 若模块未加载，跳过审批计划注册并 log.warn。</p>
 *
 * <p>注：审批计划定义了「当项目进入某阶段时触发某类型审批」的规则，本 SPI 仅注册计划
 * （持久化或调度），不立即创建审批记录。实际审批在阶段推进时触发。</p>
 */
public interface ApprovalPlanBatchCreator {

    /**
     * 批量注册审批计划。
     *
     * @param projectId         项目ID
     * @param phaseCodeToIdMap  阶段编码 → 阶段ID 映射（用于解析 triggerPhaseCode）
     * @param approvalPlanDefs  审批计划定义列表
     */
    void batchCreateApprovalPlans(Long projectId, Map<String, Long> phaseCodeToIdMap, List<TemplateSnapshot.ApprovalPlanDef> approvalPlanDefs);
}