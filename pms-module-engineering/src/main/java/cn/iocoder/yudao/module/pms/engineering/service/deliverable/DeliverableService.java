package cn.iocoder.yudao.module.pms.engineering.service.deliverable;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo.DeliverablePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.deliverable.vo.DeliverableSummaryItemVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.deliverable.DeliverableDO;

import java.util.List;

/**
 * PMS 阶段交付件历史查询接口（原 FR-ENG-027 归集入口）。
 * <p>
 * P06R 统一交付件后本服务只读：新写入一律经统一交付件能力（plt_delivery_material），
 * imp_eng_deliverable 存量保留为不可变历史，本接口仅供历史查询与 6.4 汇总。
 */
public interface DeliverableService {

    /**
     * 查询历史交付件详情
     */
    DeliverableDO getDeliverable(Long id);

    /**
     * 分页查询历史交付件
     */
    PageResult<DeliverableDO> getDeliverablePage(DeliverablePageReqVO pageReqVO);

    /**
     * 按项目交付件汇总（6.4 / ACC-04）：
     * 统一交付件材料（批准实施方案、确认培训、项目手工/挂接材料）+ ACC 归档事实
     * （初验/终验/满意度）+ imp_eng_deliverable 历史行（标注"历史归集"）。
     */
    List<DeliverableSummaryItemVO> getProjectSummary(Long projectId);
}
