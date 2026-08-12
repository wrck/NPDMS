package cn.iocoder.yudao.module.pms.engineering.service.issue;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.issue.vo.IssuePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.issue.vo.IssueSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.issue.vo.IssueVerifyReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.issue.IssueDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * PMS 实施问题 Service 接口（FR-ENG-026）。
 * <p>
 * 问题编码在项目内唯一；状态变更必须使用 IssueStatusRules 状态机校验。
 * 未关闭问题阻断项目验收（{@link #validateProjectAcceptance(Long)}）。
 */
public interface IssueService {

    /**
     * 创建问题
     */
    Long createIssue(@Valid IssueSaveReqVO createReqVO);

    /**
     * 更新问题（终态已关闭不允许修改）
     */
    void updateIssue(@Valid IssueSaveReqVO updateReqVO);

    /**
     * 删除问题
     */
    void deleteIssue(Long id);

    /**
     * 查询问题详情
     */
    IssueDO getIssue(Long id);

    /**
     * 校验问题存在
     */
    IssueDO validateIssueExists(Long id);

    /**
     * 分页查询问题
     */
    PageResult<IssueDO> getIssuePage(IssuePageReqVO pageReqVO);

    /**
     * 开始整改（0待处理 → 1整改中）
     */
    void startRectify(Long id);

    /**
     * 提交验证（1整改中 → 2待验证）
     */
    void submitForVerify(Long id);

    /**
     * 关闭问题（2待验证 → 3已关闭），需复测结果
     */
    void close(IssueVerifyReqVO reqVO);

    /**
     * 验证驳回（2待验证 → 1整改中），需驳回原因
     */
    void reject(IssueVerifyReqVO reqVO);

    /**
     * 挂起（任意非终态 → 4已挂起）
     */
    void suspend(Long id);

    /**
     * 恢复（4已挂起 → 1整改中）
     */
    void resume(Long id);

    /**
     * 查询项目下未关闭的问题列表
     */
    List<IssueDO> getUnclosedIssues(Long projectId);

    /**
     * 验收门禁：若存在未关闭问题则抛业务异常阻断验收（FR-ENG-026 验收门禁）。
     *
     * @param projectId 项目编号
     */
    void validateProjectAcceptance(Long projectId);
}
