package cn.iocoder.yudao.module.pms.engineering.service.externalprocurement;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.externalprocurement.vo.ExternalProcurementApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.externalprocurement.vo.ExternalProcurementPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.externalprocurement.vo.ExternalProcurementSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.externalprocurement.ExternalProcurementDO;
import jakarta.validation.Valid;

/**
 * PMS 外采申请 Service 接口（FR-ENG-002）。
 * <p>
 * 状态流转：0 草稿 → 1 已提交 → 2 审批中 → 3 已通过 / 4 已驳回 / 5 已撤回 / 6 已终止。
 * 外采单号全局唯一；草稿/已驳回状态可编辑或删除。
 */
public interface ExternalProcurementService {

    /**
     * 创建外采申请（校验单号唯一 + 项目存在）
     *
     * @param createReqVO 创建信息
     * @return 外采申请编号
     */
    Long createExternalProcurement(@Valid ExternalProcurementSaveReqVO createReqVO);

    /**
     * 更新外采申请（仅 0 草稿 / 4 已驳回 状态可改）
     *
     * @param updateReqVO 更新信息
     */
    void updateExternalProcurement(@Valid ExternalProcurementSaveReqVO updateReqVO);

    /**
     * 删除外采申请（仅 0 草稿 / 4 已驳回 状态可删）
     *
     * @param id 外采申请编号
     */
    void deleteExternalProcurement(Long id);

    /**
     * 查询外采申请详情
     *
     * @param id 外采申请编号
     * @return 外采申请对象
     */
    ExternalProcurementDO getExternalProcurement(Long id);

    /**
     * 校验外采申请存在
     *
     * @param id 外采申请编号
     * @return 外采申请对象
     */
    ExternalProcurementDO validateExternalProcurementExists(Long id);

    /**
     * 分页查询外采申请
     *
     * @param pageReqVO 分页查询条件
     * @return 分页结果
     */
    PageResult<ExternalProcurementDO> getExternalProcurementPage(ExternalProcurementPageReqVO pageReqVO);

    /**
     * 提交外采申请：0 草稿 / 4 已驳回 → 1 已提交
     *
     * @param id 外采申请编号
     */
    void submitExternalProcurement(Long id);

    /**
     * 审批外采申请：1 已提交 / 2 审批中 → 3 已通过 / 4 已驳回 / 0 草稿 / 2 审批中
     * <p>
     * 审批动作决定目标状态：PASS 通过 / REJECT 驳回 / RETURN 退回 / TRANSFER 转签 / COUNTERSIGN 会签
     *
     * @param reqVO 审批信息
     */
    void approveExternalProcurement(@Valid ExternalProcurementApproveReqVO reqVO);

    /**
     * 撤回外采申请：1 已提交 / 2 审批中 → 5 已撤回
     *
     * @param id 外采申请编号
     */
    void withdrawExternalProcurement(Long id);

    /**
     * 终止外采申请：非 3 已通过 / 非 6 已终止 → 6 已终止
     *
     * @param id 外采申请编号
     */
    void terminateExternalProcurement(Long id);
}
