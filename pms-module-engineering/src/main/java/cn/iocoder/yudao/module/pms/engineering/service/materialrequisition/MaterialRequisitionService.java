package cn.iocoder.yudao.module.pms.engineering.service.materialrequisition;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialrequisition.vo.MaterialRequisitionApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialrequisition.vo.MaterialRequisitionPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialrequisition.vo.MaterialRequisitionSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialrequisition.MaterialRequisitionDO;
import jakarta.validation.Valid;

/**
 * PMS OA领料申请 Service 接口（FR-ENG-002）。
 * <p>
 * 状态流转：0 草稿 → 1 已提交 → 2 审批中 → 3 已通过 / 4 已驳回 / 5 已撤回 / 6 已终止。
 * 领料单号全局唯一；草稿/已驳回状态可编辑或删除。
 */
public interface MaterialRequisitionService {

    /**
     * 创建领料申请（校验单号唯一 + 项目存在）
     *
     * @param createReqVO 创建信息
     * @return 领料申请编号
     */
    Long createMaterialRequisition(@Valid MaterialRequisitionSaveReqVO createReqVO);

    /**
     * 更新领料申请（仅 0 草稿 / 4 已驳回 状态可改）
     *
     * @param updateReqVO 更新信息
     */
    void updateMaterialRequisition(@Valid MaterialRequisitionSaveReqVO updateReqVO);

    /**
     * 删除领料申请（仅 0 草稿 / 4 已驳回 状态可删）
     *
     * @param id 领料申请编号
     */
    void deleteMaterialRequisition(Long id);

    /**
     * 查询领料申请详情
     *
     * @param id 领料申请编号
     * @return 领料申请对象
     */
    MaterialRequisitionDO getMaterialRequisition(Long id);

    /**
     * 校验领料申请存在
     *
     * @param id 领料申请编号
     * @return 领料申请对象
     */
    MaterialRequisitionDO validateMaterialRequisitionExists(Long id);

    /**
     * 分页查询领料申请
     *
     * @param pageReqVO 分页查询条件
     * @return 分页结果
     */
    PageResult<MaterialRequisitionDO> getMaterialRequisitionPage(MaterialRequisitionPageReqVO pageReqVO);

    /**
     * 提交领料申请：0 草稿 / 4 已驳回 → 1 已提交
     *
     * @param id 领料申请编号
     */
    void submitMaterialRequisition(Long id);

    /**
     * 审批领料申请：1 已提交 / 2 审批中 → 3 已通过 / 4 已驳回 / 0 草稿 / 2 审批中
     * <p>
     * 审批动作决定目标状态：PASS 通过 / REJECT 驳回 / RETURN 退回 / TRANSFER 转签 / COUNTERSIGN 会签
     *
     * @param reqVO 审批信息
     */
    void approveMaterialRequisition(@Valid MaterialRequisitionApproveReqVO reqVO);

    /**
     * 撤回领料申请：1 已提交 / 2 审批中 → 5 已撤回
     *
     * @param id 领料申请编号
     */
    void withdrawMaterialRequisition(Long id);

    /**
     * 终止领料申请：非 3 已通过 / 非 6 已终止 → 6 已终止
     *
     * @param id 领料申请编号
     */
    void terminateMaterialRequisition(Long id);
}
