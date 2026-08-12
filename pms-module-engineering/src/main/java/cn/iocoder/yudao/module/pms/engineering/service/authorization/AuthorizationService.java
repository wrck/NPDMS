package cn.iocoder.yudao.module.pms.engineering.service.authorization;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.authorization.vo.AuthorizationApproveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.authorization.vo.AuthorizationPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.authorization.vo.AuthorizationSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.authorization.AuthorizationDO;

/**
 * PMS 授权与借货 Service 接口（FR-ENG-010）。
 * <p>
 * 状态流转：0 草稿 → 1 已提交 → 2 审批中 → 3 已通过 / 4 已驳回 / 5 已撤回 / 6 已终止。
 */
public interface AuthorizationService {

    /**
     * 创建授权
     */
    Long createAuthorization(AuthorizationSaveReqVO createReqVO);

    /**
     * 更新授权（仅草稿/已驳回/已撤回状态可编辑）
     */
    void updateAuthorization(AuthorizationSaveReqVO updateReqVO);

    /**
     * 删除授权（仅草稿状态可删除）
     */
    void deleteAuthorization(Long id);

    /**
     * 查询授权详情
     */
    AuthorizationDO getAuthorization(Long id);

    /**
     * 校验授权存在，不存在则抛异常
     */
    AuthorizationDO validateAuthorizationExists(Long id);

    /**
     * 分页查询
     */
    PageResult<AuthorizationDO> getAuthorizationPage(AuthorizationPageReqVO pageReqVO);

    /**
     * 提交授权（0 草稿/4 已驳回/5 已撤回 → 1 已提交 → 2 审批中）
     */
    void submitAuthorization(Long id);

    /**
     * 审批授权（2 审批中 → 3 已通过 / 4 已驳回 / 6 已终止）
     */
    void approveAuthorization(AuthorizationApproveReqVO reqVO);

    /**
     * 撤回授权（1 已提交/2 审批中 → 5 已撤回）
     */
    void recallAuthorization(Long id);

    /**
     * 终止授权（已通过 → 6 已终止）
     */
    void terminateAuthorization(Long id);
}
