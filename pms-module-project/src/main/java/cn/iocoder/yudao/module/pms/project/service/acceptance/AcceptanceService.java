package cn.iocoder.yudao.module.pms.project.service.acceptance;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.project.controller.admin.acceptance.vo.AcceptancePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.acceptance.vo.AcceptanceSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptance.AcceptanceDO;

/**
 * 初验/终验 Service 接口
 *
 * @deprecated 旧V17单行验收模型仅保留历史兼容；新实施使用F-ACC-001验收活动与报告版本服务。
 */
@Deprecated(since = "F-ACC-001", forRemoval = false)
public interface AcceptanceService {

    /**
     * 创建验收
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createAcceptance(AcceptanceSaveReqVO createReqVO);

    /**
     * 更新验收
     *
     * @param updateReqVO 更新信息
     */
    void updateAcceptance(AcceptanceSaveReqVO updateReqVO);

    /**
     * 删除验收
     *
     * @param id 编号
     */
    void deleteAcceptance(Long id);

    /**
     * 获得验收分页
     *
     * @param pageReqVO 分页查询
     * @return 分页结果
     */
    PageResult<AcceptanceDO> getAcceptancePage(AcceptancePageReqVO pageReqVO);

    /**
     * 获得验收
     *
     * @param id 编号
     * @return 验收
     */
    AcceptanceDO getAcceptance(Long id);

    /**
     * 提交（0草稿 → 1待提交）
     *
     * @param id 编号
     */
    void submitAcceptance(Long id);

    /**
     * 审批（1待提交 → 2审批中）
     *
     * @param id 编号
     */
    void approveAcceptance(Long id);

    /**
     * 通过（2审批中 → 3已通过）
     * 门禁：通过前校验交付件完整性（FR-ACC-005）
     *
     * @param id 编号
     */
    void passAcceptance(Long id);

    /**
     * 驳回（2审批中 → 4已驳回）
     *
     * @param id 编号
     */
    void rejectAcceptance(Long id);

    /**
     * 归档（3已通过 → 5已归档）
     *
     * @param id 编号
     */
    void archiveAcceptance(Long id);

}
