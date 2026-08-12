package cn.iocoder.yudao.module.pms.engineering.service.forminstance;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.forminstance.vo.FormInstanceApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.forminstance.vo.FormInstancePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.forminstance.vo.FormInstanceSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.forminstance.FormInstanceDO;
import jakarta.validation.Valid;

/**
 * PMS 准备数据表单实例 Service 接口（FR-ENG-007）。
 * <p>
 * 状态流转：0 待填 → 1 已填 → 2 已提交 → 3 已审核 / 4 已驳回（驳回回到 1 已填）。
 * 实例编号全局唯一；待填/已填/已驳回状态可编辑或删除。
 */
public interface FormInstanceService {

    /**
     * 创建表单实例（校验编号唯一 + 项目存在 + 模板存在，并写入模板快照）
     *
     * @param createReqVO 创建信息
     * @return 实例编号
     */
    Long createFormInstance(@Valid FormInstanceSaveReqVO createReqVO);

    /**
     * 更新表单实例（仅 0 待填 / 1 已填 / 4 已驳回 状态可改）
     *
     * @param updateReqVO 更新信息
     */
    void updateFormInstance(@Valid FormInstanceSaveReqVO updateReqVO);

    /**
     * 删除表单实例（仅 0 待填 / 1 已填 / 4 已驳回 状态可删）
     *
     * @param id 实例编号
     */
    void deleteFormInstance(Long id);

    /**
     * 查询表单实例详情
     *
     * @param id 实例编号
     * @return 实例对象
     */
    FormInstanceDO getFormInstance(Long id);

    /**
     * 校验表单实例存在
     *
     * @param id 实例编号
     * @return 实例对象
     */
    FormInstanceDO validateFormInstanceExists(Long id);

    /**
     * 分页查询表单实例
     *
     * @param pageReqVO 分页查询条件
     * @return 分页结果
     */
    PageResult<FormInstanceDO> getFormInstancePage(FormInstancePageReqVO pageReqVO);

    /**
     * 保存填报：0 待填 / 4 已驳回 → 1 已填
     *
     * @param reqVO 保存信息
     */
    void saveFormInstance(@Valid FormInstanceSaveReqVO reqVO);

    /**
     * 提交表单实例：0 待填 / 1 已填 / 4 已驳回 → 2 已提交
     *
     * @param id 实例编号
     */
    void submitFormInstance(Long id);

    /**
     * 审核表单实例：2 已提交 → 3 已审核 / 4 已驳回
     *
     * @param reqVO 审核信息
     */
    void approveFormInstance(@Valid FormInstanceApproveReqVO reqVO);
}
