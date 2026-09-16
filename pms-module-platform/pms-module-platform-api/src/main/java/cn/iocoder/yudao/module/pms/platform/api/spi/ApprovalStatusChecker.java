package cn.iocoder.yudao.module.pms.platform.api.spi;

import cn.iocoder.yudao.module.pms.platform.api.spi.dto.ApprovalViolation;

import java.util.List;

/**
 * 审批状态校验 SPI（迁移自源工程 pms-common com.dp.plat.common.spi.ApprovalStatusChecker，
 * TD-P8-005）。
 *
 * <p>pms-module-project 的 validateExitGate APPROVAL 分支通过本 SPI 跨模块校验
 * pms-module-workflow 审批中心中关联审批是否已通过。设计文档 §3.4 定义 APPROVAL 类退出条件为
 * 「关联审批通过」，本 SPI 提供按项目+审批类型查询审批状态的能力。</p>
 *
 * <p>由 pms-module-workflow 模块实现并注册为 Spring Bean，
 * pms-module-project 通过 {@code @Autowired(required=false)} 注入。
 * 若模块未加载（bean 不存在），APPROVAL 分支跳过校验（仅 log.warn）。</p>
 */
public interface ApprovalStatusChecker {

    /**
     * 查询指定项目下指定审批类型的违规项（未通过审批）。
     *
     * <p>若 {@code mustApproved=true} 但审批未通过或不存在，返回对应违规；
     * 若审批已 APPROVED，返回空列表。</p>
     *
     * @param projectId     项目ID
     * @param approvalType  审批类型（如 PHASE_EXIT）
     * @param mustApproved  是否必须已通过
     * @return 违规列表（空列表表示已通过或不要求）
     */
    List<ApprovalViolation> findApprovalViolations(Long projectId, String approvalType, boolean mustApproved);
}