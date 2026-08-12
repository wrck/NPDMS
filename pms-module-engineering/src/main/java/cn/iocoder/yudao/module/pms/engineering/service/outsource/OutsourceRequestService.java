package cn.iocoder.yudao.module.pms.engineering.service.outsource;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.outsource.vo.OutsourceRequestApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.outsource.vo.OutsourceRequestPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.outsource.vo.OutsourceRequestSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.outsource.OutsourceRequestDO;
import jakarta.validation.Valid;

/**
 * PMS 外包申请 Service 接口（FR-ENG-002）。
 * <p>
 * 状态流转：0 草稿 → 1 已提交 → 2 审批中 → 3 已通过 / 4 已驳回 / 5 已撤回 / 6 已终止。
 * 外包单号全局唯一；草稿/已驳回状态可编辑或删除。
 */
public interface OutsourceRequestService {

    /**
     * 创建外包申请（校验单号唯一 + 项目存在）
     *
     * @param createReqVO 创建信息
     * @return 外包申请编号
     */
    Long createOutsourceRequest(@Valid OutsourceRequestSaveReqVO createReqVO);

    /**
     * 更新外包申请（仅 0 草稿 / 4 已驳回 状态可改）
     *
     * @param updateReqVO 更新信息
     */
    void updateOutsourceRequest(@Valid OutsourceRequestSaveReqVO updateReqVO);

    /**
     * 删除外包申请（仅 0 草稿 / 4 已驳回 状态可删）
     *
     * @param id 外包申请编号
     */
    void deleteOutsourceRequest(Long id);

    /**
     * 查询外包申请详情
     *
     * @param id 外包申请编号
     * @return 外包申请对象
     */
    OutsourceRequestDO getOutsourceRequest(Long id);

    /**
     * 校验外包申请存在
     *
     * @param id 外包申请编号
     * @return 外包申请对象
     */
    OutsourceRequestDO validateOutsourceRequestExists(Long id);

    /**
     * 分页查询外包申请
     *
     * @param pageReqVO 分页查询条件
     * @return 分页结果
     */
    PageResult<OutsourceRequestDO> getOutsourceRequestPage(OutsourceRequestPageReqVO pageReqVO);

    /**
     * 提交外包申请：0 草稿 / 4 已驳回 → 1 已提交
     *
     * @param id 外包申请编号
     */
    void submitOutsourceRequest(Long id);

    /**
     * 审批外包申请：1 已提交 / 2 审批中 → 3 已通过 / 4 已驳回 / 0 草稿 / 2 审批中
     * <p>
     * 审批动作决定目标状态：PASS 通过 / REJECT 驳回 / RETURN 退回 / TRANSFER 转签 / COUNTERSIGN 会签
     *
     * @param reqVO 审批信息
     */
    void approveOutsourceRequest(@Valid OutsourceRequestApproveReqVO reqVO);

    /**
     * 撤回外包申请：1 已提交 / 2 审批中 → 5 已撤回
     *
     * @param id 外包申请编号
     */
    void withdrawOutsourceRequest(Long id);

    /**
     * 终止外包申请：非 3 已通过 / 非 6 已终止 → 6 已终止
     *
     * @param id 外包申请编号
     */
    void terminateOutsourceRequest(Long id);
}
