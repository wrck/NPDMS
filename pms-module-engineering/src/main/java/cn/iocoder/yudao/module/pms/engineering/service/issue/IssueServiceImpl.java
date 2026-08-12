package cn.iocoder.yudao.module.pms.engineering.service.issue;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.issue.vo.IssuePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.issue.vo.IssueSaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.issue.vo.IssueVerifyReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.issue.IssueDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.issue.IssueMapper;
import cn.iocoder.yudao.module.pms.engineering.domain.IssueStatusRules;
import cn.iocoder.yudao.module.pms.engineering.enums.EngStatusEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/**
 * PMS 实施问题 Service 实现（FR-ENG-026）。
 * <p>
 * 状态机：待处理 → 整改中 → 待验证 → 已关闭；支持挂起/恢复/驳回。
 * 验收门禁：项目存在未关闭问题时阻断验收。
 */
@Service
@Validated
@Slf4j
public class IssueServiceImpl implements IssueService {

    @Resource
    private IssueMapper issueMapper;

    @Override
    public Long createIssue(IssueSaveReqVO createReqVO) {
        validateCodeUniqueInProject(null, createReqVO.getProjectId(), createReqVO.getCode());
        IssueDO entity = BeanUtils.toBean(createReqVO, IssueDO.class);
        entity.setStatus(EngStatusEnum.ISSUE_OPEN);
        issueMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateIssue(IssueSaveReqVO updateReqVO) {
        IssueDO existing = validateIssueExists(updateReqVO.getId());
        if (!Objects.equals(existing.getCode(), updateReqVO.getCode())) {
            throw exception(ISSUE_CODE_DUPLICATE, updateReqVO.getCode());
        }
        // 终态已关闭不允许修改
        if (IssueStatusRules.isClosed(existing.getStatus())) {
            throw exception(ISSUE_STATUS_INVALID);
        }
        IssueDO update = BeanUtils.toBean(updateReqVO, IssueDO.class);
        issueMapper.updateById(update);
    }

    @Override
    public void deleteIssue(Long id) {
        validateIssueExists(id);
        issueMapper.deleteById(id);
    }

    @Override
    public IssueDO getIssue(Long id) {
        return issueMapper.selectById(id);
    }

    @Override
    public IssueDO validateIssueExists(Long id) {
        IssueDO entity = issueMapper.selectById(id);
        if (entity == null) {
            throw exception(ISSUE_NOT_EXISTS);
        }
        return entity;
    }

    @Override
    public PageResult<IssueDO> getIssuePage(IssuePageReqVO pageReqVO) {
        return issueMapper.selectPage(pageReqVO);
    }

    @Override
    public void startRectify(Long id) {
        IssueDO entity = validateIssueExists(id);
        IssueStatusRules.requireTransition(entity.getStatus(), IssueStatusRules.Action.START_RECTIFY);
        updateStatus(id, IssueStatusRules.Action.START_RECTIFY, entity.getVersion());
    }

    @Override
    public void submitForVerify(Long id) {
        IssueDO entity = validateIssueExists(id);
        IssueStatusRules.requireTransition(entity.getStatus(), IssueStatusRules.Action.SUBMIT_FOR_VERIFY);
        updateStatus(id, IssueStatusRules.Action.SUBMIT_FOR_VERIFY, entity.getVersion());
    }

    @Override
    public void close(IssueVerifyReqVO reqVO) {
        if (StringUtils.isBlank(reqVO.getVerifyResult())) {
            throw exception(ISSUE_STATUS_INVALID);
        }
        IssueDO entity = validateIssueExists(reqVO.getId());
        IssueStatusRules.requireTransition(entity.getStatus(), IssueStatusRules.Action.CLOSE);
        IssueDO update = new IssueDO();
        update.setId(reqVO.getId());
        update.setStatus(IssueStatusRules.targetStatus(IssueStatusRules.Action.CLOSE));
        update.setVerifyResult(reqVO.getVerifyResult());
        update.setVerifiedBy(reqVO.getVerifiedBy());
        update.setVerifiedTime(LocalDateTime.now());
        update.setVersion(reqVO.getVersion() != null ? reqVO.getVersion() : entity.getVersion());
        issueMapper.updateById(update);
    }

    @Override
    public void reject(IssueVerifyReqVO reqVO) {
        if (StringUtils.isBlank(reqVO.getRejectReason())) {
            throw exception(ISSUE_STATUS_INVALID);
        }
        IssueDO entity = validateIssueExists(reqVO.getId());
        IssueStatusRules.requireTransition(entity.getStatus(), IssueStatusRules.Action.REJECT);
        IssueDO update = new IssueDO();
        update.setId(reqVO.getId());
        update.setStatus(IssueStatusRules.targetStatus(IssueStatusRules.Action.REJECT));
        // 驳回原因追加到复测结果字段，便于追溯
        update.setVerifyResult("【驳回】" + reqVO.getRejectReason());
        update.setVersion(reqVO.getVersion() != null ? reqVO.getVersion() : entity.getVersion());
        issueMapper.updateById(update);
    }

    @Override
    public void suspend(Long id) {
        IssueDO entity = validateIssueExists(id);
        IssueStatusRules.requireTransition(entity.getStatus(), IssueStatusRules.Action.SUSPEND);
        updateStatus(id, IssueStatusRules.Action.SUSPEND, entity.getVersion());
    }

    @Override
    public void resume(Long id) {
        IssueDO entity = validateIssueExists(id);
        IssueStatusRules.requireTransition(entity.getStatus(), IssueStatusRules.Action.RESUME);
        updateStatus(id, IssueStatusRules.Action.RESUME, entity.getVersion());
    }

    @Override
    public List<IssueDO> getUnclosedIssues(Long projectId) {
        return issueMapper.selectUnclosedByProject(projectId);
    }

    @Override
    public void validateProjectAcceptance(Long projectId) {
        List<IssueDO> unclosed = issueMapper.selectUnclosedByProject(projectId);
        if (unclosed != null && !unclosed.isEmpty()) {
            throw exception(ISSUE_ACCEPTANCE_NOT_PASSED);
        }
    }

    private void updateStatus(Long id, IssueStatusRules.Action action, Integer version) {
        IssueDO update = new IssueDO();
        update.setId(id);
        update.setStatus(IssueStatusRules.targetStatus(action));
        update.setVersion(version);
        issueMapper.updateById(update);
    }

    private void validateCodeUniqueInProject(Long id, Long projectId, String code) {
        if (StringUtils.isBlank(code) || projectId == null) {
            return;
        }
        IssueDO existing = issueMapper.selectByProjectIdAndCode(projectId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !Objects.equals(existing.getId(), id)) {
            throw exception(ISSUE_CODE_DUPLICATE, code);
        }
    }
}
