package cn.iocoder.yudao.module.pms.service.service.srvissue;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssueActionReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssueAssignReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssuePageReqVO;
import cn.iocoder.yudao.module.pms.service.controller.admin.srvissue.vo.SrvIssueSaveReqVO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.srvissue.SrvIssueRetiredDO;
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
  * @deprecated 已随 pms_* 旧域退役：业务由新实现入口承接，数据库仅读；仅保留存量只读兼容，禁止新开发接入。
*/
@Service
@Validated
@Deprecated
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
    public Long createSrvIssueRetired(SrvIssueSaveReqVO createReqVO) {
        validateCodeUnique(null, createReqVO.getTaskId(), createReqVO.getCode());
        SrvIssueRetiredDO issue = BeanUtils.toBean(createReqVO, SrvIssueRetiredDO.class);
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
    public void updateSrvIssueRetired(SrvIssueSaveReqVO updateReqVO) {
        SrvIssueRetiredDO existing = validateSrvIssueExists(updateReqVO.getId());
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getTaskId(), updateReqVO.getCode());
        // 已关闭或已取消的问题不允许修改
        if (Objects.equals(existing.getStatus(), STATUS_CLOSED)
                || Objects.equals(existing.getStatus(), STATUS_CANCELLED)) {
            throw exception(SRV_ISSUE_STATUS_INVALID);
        }
        SrvIssueRetiredDO updateObj = BeanUtils.toBean(updateReqVO, SrvIssueRetiredDO.class);
        // 保持状态不被前端覆盖
        updateObj.setStatus(existing.getStatus());
        srvIssueMapper.updateById(updateObj);
    }

    @Override
    public void deleteSrvIssueRetired(Long id) {
        SrvIssueRetiredDO existing = validateSrvIssueExists(id);
        // 已关闭的问题不允许删除
        if (Objects.equals(existing.getStatus(), STATUS_CLOSED)) {
            throw exception(SRV_ISSUE_STATUS_INVALID);
        }
        srvIssueMapper.deleteById(id);
    }

    @Override
    public PageResult<SrvIssueRetiredDO> getSrvIssuePageRetired(SrvIssuePageReqVO pageReqVO) {
        return srvIssueMapper.selectPageRetired(pageReqVO);
    }

    @Override
    public SrvIssueRetiredDO getSrvIssueRetired(Long id) {
        return srvIssueMapper.selectById(id);
    }

    @Override
    public List<SrvIssueRetiredDO> getSrvIssueListByTaskRetired(Long taskId) {
        if (taskId == null) {
            return List.of();
        }
        return srvIssueMapper.selectListByTaskIdRetired(taskId);
    }

    @Override
    public void assignIssueRetired(SrvIssueAssignReqVO reqVO) {
        SrvIssueRetiredDO issue = validateSrvIssueExists(reqVO.getId());
        if (!Objects.equals(issue.getStatus(), STATUS_PENDING_ASSIGN)) {
            throw exception(SRV_ISSUE_STATUS_INVALID);
        }
        SrvIssueRetiredDO updateObj = new SrvIssueRetiredDO();
        updateObj.setId(reqVO.getId());
        updateObj.setStatus(STATUS_ASSIGNED);
        updateObj.setOwnerUserId(reqVO.getOwnerUserId());
        updateObj.setDeadline(reqVO.getDeadline());
        srvIssueMapper.updateById(updateObj);
    }

    @Override
    public void resolveIssueRetired(SrvIssueActionReqVO reqVO) {
        SrvIssueRetiredDO issue = validateSrvIssueExists(reqVO.getId());
        if (!Objects.equals(issue.getStatus(), STATUS_ASSIGNED)) {
            throw exception(SRV_ISSUE_STATUS_INVALID);
        }
        SrvIssueRetiredDO updateObj = new SrvIssueRetiredDO();
        updateObj.setId(reqVO.getId());
        updateObj.setStatus(STATUS_PENDING_VERIFY);
        updateObj.setSolution(reqVO.getSolution());
        srvIssueMapper.updateById(updateObj);
    }

    @Override
    public void verifyIssueRetired(SrvIssueActionReqVO reqVO) {
        SrvIssueRetiredDO issue = validateSrvIssueExists(reqVO.getId());
        if (!Objects.equals(issue.getStatus(), STATUS_PENDING_VERIFY)) {
            throw exception(SRV_ISSUE_STATUS_INVALID);
        }
        SrvIssueRetiredDO updateObj = new SrvIssueRetiredDO();
        updateObj.setId(reqVO.getId());
        updateObj.setStatus(STATUS_CLOSED);
        updateObj.setVerifyResult(reqVO.getVerifyResult());
        updateObj.setVerifiedTime(LocalDateTime.now());
        srvIssueMapper.updateById(updateObj);
    }

    @Override
    public void cancelIssueRetired(Long id) {
        SrvIssueRetiredDO issue = validateSrvIssueExists(id);
        if (!Objects.equals(issue.getStatus(), STATUS_PENDING_ASSIGN)
                && !Objects.equals(issue.getStatus(), STATUS_ASSIGNED)) {
            throw exception(SRV_ISSUE_STATUS_INVALID);
        }
        updateStatus(id, STATUS_CANCELLED);
    }

    @Override
    public boolean validateInspectionClosureRetired(Long taskId) {
        if (taskId == null) {
            return false;
        }
        List<SrvIssueRetiredDO> issues = srvIssueMapper.selectListByTaskIdRetired(taskId);
        for (SrvIssueRetiredDO issue : issues) {
            if (!Objects.equals(issue.getStatus(), STATUS_CLOSED)
                    && !Objects.equals(issue.getStatus(), STATUS_CANCELLED)) {
                return false;
            }
        }
        return true;
    }

    private void updateStatus(Long id, int status) {
        SrvIssueRetiredDO updateObj = new SrvIssueRetiredDO();
        updateObj.setId(id);
        updateObj.setStatus(status);
        srvIssueMapper.updateById(updateObj);
    }

    private SrvIssueRetiredDO validateSrvIssueExists(Long id) {
        if (id == null) {
            throw exception(SRV_ISSUE_NOT_EXISTS);
        }
        SrvIssueRetiredDO issue = srvIssueMapper.selectById(id);
        if (issue == null) {
            throw exception(SRV_ISSUE_NOT_EXISTS);
        }
        return issue;
    }

    private void validateCodeUnique(Long id, Long taskId, String code) {
        if (taskId == null || code == null) {
            return;
        }
        SrvIssueRetiredDO existing = srvIssueMapper.selectByTaskIdAndCodeRetired(taskId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !id.equals(existing.getId())) {
            throw exception(SRV_ISSUE_CODE_DUPLICATE, code);
        }
    }

}
