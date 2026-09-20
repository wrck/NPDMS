package cn.iocoder.yudao.module.pms.engineering.service.deliverable;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo.DeliverablePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo.DeliverableSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo.DeliverableSummaryItemVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.deliverable.DeliverableDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * PMS 阶段交付件归集 Service 接口（FR-ENG-027）。
 * <p>
 * 交付件编码在项目内唯一；已归集（status=1）的交付件不可修改，仅可作废。
 * 归集版本不可覆盖：同来源业务再次归集时返回已存在记录，不创建新版本。
 */
public interface DeliverableService {

    /**
     * 创建交付件（待归集状态）
     */
    Long createDeliverable(@Valid DeliverableSaveReqVO createReqVO);

    /**
     * 更新交付件（已归集不可修改）
     */
    void updateDeliverable(@Valid DeliverableSaveReqVO updateReqVO);

    /**
     * 删除交付件（已归集不可删除）
     */
    void deleteDeliverable(Long id);

    /**
     * 查询交付件详情
     */
    DeliverableDO getDeliverable(Long id);

    /**
     * 校验交付件存在
     */
    DeliverableDO validateDeliverableExists(Long id);

    /**
     * 分页查询交付件
     */
    PageResult<DeliverableDO> getDeliverablePage(DeliverablePageReqVO pageReqVO);

    /**
     * 归集交付件（0待归集 → 1已归集）。
     * <p>
     * 归集版本不可覆盖：若来源业务已归集，返回已存在记录编号，不重复归集。
     *
     * @param id 交付件编号
     * @param archivedBy 归集人
     * @return 已归集的交付件编号（幂等）
     */
    Long archive(Long id, Long archivedBy);

    /**
     * 作废交付件（0待归集 / 1已归集 → 2已作废）
     */
    void voidDeliverable(Long id);

    /**
     * 按项目交付件汇总（6.4 / ACC-04）：
     * 工程归集件 + 批准实施方案 + ACC 归档事实（初验/终验/满意度），
     * 按 Demo 6 类合并输出；到货签收单与现场培训记录的自动归档未接通时如实标注。
     */
    List<DeliverableSummaryItemVO> getProjectSummary(Long projectId);
}
