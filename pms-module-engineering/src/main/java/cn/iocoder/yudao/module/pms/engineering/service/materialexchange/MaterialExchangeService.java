package cn.iocoder.yudao.module.pms.engineering.service.materialexchange;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo.MaterialExchangeApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo.MaterialExchangePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo.MaterialExchangeSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.materialexchange.MaterialExchangeDO;
import jakarta.validation.Valid;

/**
 * PMS 物料换货协同 Service 接口（FR-ENG-003）。
 * <p>
 * 单据状态流转：0 草稿 → 1 已提交 → 2 审批中 → 3 已通过 / 4 已驳回 / 5 已撤回 / 6 已终止。
 * CRM 推送状态：PENDING → SENT → RECEIVED，仅 PENDING 可推送。
 * 换货单号全局唯一；草稿/已驳回状态可编辑或删除。
 */
public interface MaterialExchangeService {

    /**
     * 创建换货协同单（校验单号唯一 + 项目存在）
     *
     * @param createReqVO 创建信息
     * @return 换货协同单编号
     */
    Long createMaterialExchange(@Valid MaterialExchangeSaveReqVO createReqVO);

    /**
     * 更新换货协同单（仅 0 草稿 / 4 已驳回 状态可改）
     *
     * @param updateReqVO 更新信息
     */
    void updateMaterialExchange(@Valid MaterialExchangeSaveReqVO updateReqVO);

    /**
     * 删除换货协同单（仅 0 草稿 / 4 已驳回 状态可删）
     *
     * @param id 换货协同单编号
     */
    void deleteMaterialExchange(Long id);

    /**
     * 查询换货协同单详情
     *
     * @param id 换货协同单编号
     * @return 换货协同单对象
     */
    MaterialExchangeDO getMaterialExchange(Long id);

    /**
     * 校验换货协同单存在
     *
     * @param id 换货协同单编号
     * @return 换货协同单对象
     */
    MaterialExchangeDO validateMaterialExchangeExists(Long id);

    /**
     * 分页查询换货协同单
     *
     * @param pageReqVO 分页查询条件
     * @return 分页结果
     */
    PageResult<MaterialExchangeDO> getMaterialExchangePage(MaterialExchangePageReqVO pageReqVO);

    /**
     * 提交换货协同单：0 草稿 / 4 已驳回 → 1 已提交
     *
     * @param id 换货协同单编号
     */
    void submitMaterialExchange(Long id);

    /**
     * 审批换货协同单：1 已提交 / 2 审批中 → 3 已通过 / 4 已驳回 / 0 草稿 / 2 审批中
     * <p>
     * 审批动作决定目标状态：PASS 通过 / REJECT 驳回 / RETURN 退回 / TRANSFER 转签 / COUNTERSIGN 会签
     *
     * @param reqVO 审批信息
     */
    void approveMaterialExchange(@Valid MaterialExchangeApproveReqVO reqVO);

    /**
     * 撤换换货协同单：1 已提交 / 2 审批中 → 5 已撤回
     *
     * @param id 换货协同单编号
     */
    void withdrawMaterialExchange(Long id);

    /**
     * 终止换货协同单：非 3 已通过 / 非 6 已终止 → 6 已终止
     *
     * @param id 换货协同单编号
     */
    void terminateMaterialExchange(Long id);

    /**
     * 推送 CRM（FR-ENG-003 特有）。
     * <p>
     * 仅当 crm_push_status=PENDING 可推送；推送后置为 SENT 并记录推送时间；
     * 若入参 crmOrderNo 非空，则直接置为 RECEIVED 并写入 CRM 工单号。
     * <p>
     * 注：当前为模拟推送，实际 CRM 集成在 T-V2-INT-001 中实现。
     *
     * @param id         换货协同单编号
     * @param crmOrderNo CRM 工单号（可选，传入则直接置为 RECEIVED）
     */
    void pushToCrm(Long id, String crmOrderNo);
}
