package cn.iocoder.yudao.module.pms.service.service.srvissue;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssueActionReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssueAssignReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssuePageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssueSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvissue.SrvIssueDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.srvissue.SrvIssueMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.SRV_ISSUE_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.SRV_ISSUE_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.SRV_ISSUE_STATUS_INVALID;

/**
 * 巡检问题与整改 Service 实现类
 */
@Service
@Validated
public class SrvIssueServiceImpl implements SrvIssueService {

    /**
     * 问题状态：0待分派
     */
    private static final int STATUS_PENDING_ASSIGN = 0;
    /**
     * 问题状态：1已分派
     */
    private static final int STATUS_ASSIGNED = 1;
    /**
     * 问题状态：2待验证
     */
    private static final int STATUS_PENDING_VERIFY = 2;
    /**
     * 问题状态：3已关闭
     */
    private static final int STATUS_CLOSED = 3;
    /**
     * 问题状态：4已取消
     */
    private static final int STATUS_CANCELLED = 4;

    @Resource
    private SrvIssueMapper srvIssueMapper;

    @Override
    public Long createSrvIssue(SrvIssueSaveReqVO createReqVO) {
        validateCodeUnique(null, createReqVO.getTaskId(), createReqVO.getCode());
        SrvIssueDO issue = BeanUtils.toBean(createReqVO, SrvIssueDO.class);
        if (issue.getStatus() == null) {
            issue.setStatus(STATUS_PENDING_ASSIGN);
        }
        if (issue.getSeverity() == null) {
            issue.setSeverity("M");
        }
        srvIssueMapper.insert(issue);
        return issue.getId();
    }

    @Override
    public void updateSrvIssue(SrvIssueSaveReqVO updateReqVO) {
        SrvIssueDO existing = validateSrvIssueExists(updateReqVO.getId());
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getTaskId(), updateReqVO.getCode());
        // 已关闭或已取消的问题不允许修改
        if (Objects.equals(existing.getStatus(), STATUS_CLOSED)
                || Objects.equals(existing.getStatus(), STATUS_CANCELLED)) {
            throw exception(SRV_ISSUE_STATUS_INVALID);
        }
        SrvIssueDO updateObj = BeanUtils.toBean(updateReqVO, SrvIssueDO.class);
        // 保持状态不被前端覆盖
        updateObj.setStatus(existing.getStatus());
        srvIssueMapper.updateById(updateObj);
    }

    @Override
    public void deleteSrvIssue(Long id) {
        SrvIssueDO existing = validateSrvIssueExists(id);
        // 已关闭的问题不允许删除
        if (Objects.equals(existing.getStatus(), STATUS_CLOSED)) {
            throw exception(SRV_ISSUE_STATUS_INVALID);
        }
        srvIssueMapper.deleteById(id);
    }

    @Override
    public PageResult<SrvIssueDO> getSrvIssuePage(SrvIssuePageReqVO pageReqVO) {
        return srvIssueMapper.selectPage(pageReqVO);
    }

    @Override
    public SrvIssueDO getSrvIssue(Long id) {
        return srvIssueMapper.selectById(id);
    }

    @Override
    public List<SrvIssueDO> getSrvIssueListByTask(Long taskId) {
        if (taskId == null) {
            return List.of();
        }
        return srvIssueMapper.selectListByTaskId(taskId);
    }

    @Override
    public void assignIssue(SrvIssueAssignReqVO reqVO) {
        SrvIssueDO issue = validateSrvIssueExists(reqVO.getId());
        if (!Objects.equals(issue.getStatus(), STATUS_PENDING_ASSIGN)) {
            throw exception(SRV_ISSUE_STATUS_INVALID);
        }
        SrvIssueDO updateObj = new SrvIssueDO();
        updateObj.setId(reqVO.getId());
        updateObj.setStatus(STATUS_ASSIGNED);
        updateObj.setOwnerUserId(reqVO.getOwnerUserId());
        updateObj.setDeadline(reqVO.getDeadline());
        srvIssueMapper.updateById(updateObj);
    }

    @Override
    public void resolveIssue(SrvIssueActionReqVO reqVO) {
        SrvIssueDO issue = validateSrvIssueExists(reqVO.getId());
        if (!Objects.equals(issue.getStatus(), STATUS_ASSIGNED)) {
            throw exception(SRV_ISSUE_STATUS_INVALID);
        }
        SrvIssueDO updateObj = new SrvIssueDO();
        updateObj.setId(reqVO.getId());
        updateObj.setStatus(STATUS_PENDING_VERIFY);
        updateObj.setSolution(reqVO.getSolution());
        srvIssueMapper.updateById(updateObj);
    }

    @Override
    public void verifyIssue(SrvIssueActionReqVO reqVO) {
        SrvIssueDO issue = validateSrvIssueExists(reqVO.getId());
        if (!Objects.equals(issue.getStatus(), STATUS_PENDING_VERIFY)) {
            throw exception(SRV_ISSUE_STATUS_INVALID);
        }
        SrvIssueDO updateObj = new SrvIssueDO();
        updateObj.setId(reqVO.getId());
        updateObj.setStatus(STATUS_CLOSED);
        updateObj.setVerifyResult(reqVO.getVerifyResult());
        updateObj.setVerifiedTime(LocalDateTime.now());
        srvIssueMapper.updateById(updateObj);
    }

    @Override
    public void cancelIssue(Long id) {
        SrvIssueDO issue = validateSrvIssueExists(id);
        if (!Objects.equals(issue.getStatus(), STATUS_PENDING_ASSIGN)
                && !Objects.equals(issue.getStatus(), STATUS_ASSIGNED)) {
            throw exception(SRV_ISSUE_STATUS_INVALID);
        }
        updateStatus(id, STATUS_CANCELLED);
    }

    @Override
    public boolean validateInspectionClosure(Long taskId) {
        if (taskId == null) {
            return false;
        }
        List<SrvIssueDO> issues = srvIssueMapper.selectListByTaskId(taskId);
        for (SrvIssueDO issue : issues) {
            if (!Objects.equals(issue.getStatus(), STATUS_CLOSED)
                    && !Objects.equals(issue.getStatus(), STATUS_CANCELLED)) {
                return false;
            }
        }
        return true;
    }

    private void updateStatus(Long id, int status) {
        SrvIssueDO updateObj = new SrvIssueDO();
        updateObj.setId(id);
        updateObj.setStatus(status);
        srvIssueMapper.updateById(updateObj);
    }

    private SrvIssueDO validateSrvIssueExists(Long id) {
        if (id == null) {
            throw exception(SRV_ISSUE_NOT_EXISTS);
        }
        SrvIssueDO issue = srvIssueMapper.selectById(id);
        if (issue == null) {
            throw exception(SRV_ISSUE_NOT_EXISTS);
        }
        return issue;
    }

    private void validateCodeUnique(Long id, Long taskId, String code) {
        if (taskId == null || code == null) {
            return;
        }
        SrvIssueDO existing = srvIssueMapper.selectByTaskIdAndCode(taskId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !id.equals(existing.getId())) {
            throw exception(SRV_ISSUE_CODE_DUPLICATE, code);
        }
    }

}
