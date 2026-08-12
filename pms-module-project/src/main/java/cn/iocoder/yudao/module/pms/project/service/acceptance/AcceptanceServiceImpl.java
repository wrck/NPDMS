package cn.iocoder.yudao.module.pms.project.service.acceptance;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.acceptance.vo.AcceptancePageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.acceptance.vo.AcceptanceSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptance.AcceptanceDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.deliverablechecklist.DeliverableChecklistDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.acceptance.AcceptanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.deliverablechecklist.DeliverableChecklistMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_ACCEPTANCE_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_ACCEPTANCE_DELIVERABLE_INCOMPLETE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_ACCEPTANCE_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_ACCEPTANCE_STATUS_INVALID;

/**
 * 初验/终验 Service 实现类
 * <p>
 * 状态机：0草稿 → 1待提交 → 2审批中 → 3已通过 → 4已驳回 → 5已归档
 * 门禁：验收通过（pass 2→3）前校验交付件完整性（FR-ACC-005），必交交付件必须全部通过
 */
@Service
@Validated
public class AcceptanceServiceImpl implements AcceptanceService {

    /**
     * 状态：0草稿
     */
    private static final int STATUS_DRAFT = 0;
    /**
     * 状态：1待提交
     */
    private static final int STATUS_PENDING_SUBMIT = 1;
    /**
     * 状态：2审批中
     */
    private static final int STATUS_APPROVING = 2;
    /**
     * 状态：3已通过
     */
    private static final int STATUS_PASSED = 3;
    /**
     * 状态：4已驳回
     */
    private static final int STATUS_REJECTED = 4;
    /**
     * 状态：5已归档
     */
    private static final int STATUS_ARCHIVED = 5;

    /**
     * 交付件类型：必交
     */
    private static final String DELIVERABLE_TYPE_REQUIRED = "REQUIRED";
    /**
     * 交付件状态：已通过
     */
    private static final int DELIVERABLE_STATUS_PASSED = 2;

    @Resource
    private AcceptanceMapper acceptanceMapper;
    @Resource
    private DeliverableChecklistMapper deliverableChecklistMapper;

    @Override
    public Long createAcceptance(AcceptanceSaveReqVO createReqVO) {
        // 校验项目内编码唯一
        validateCodeUnique(null, createReqVO.getProjectId(), createReqVO.getCode());
        // 插入
        AcceptanceDO entity = BeanUtils.toBean(createReqVO, AcceptanceDO.class);
        if (entity.getStatus() == null) {
            entity.setStatus(STATUS_DRAFT);
        }
        if (entity.getAcceptanceType() == null) {
            entity.setAcceptanceType("PRELIMINARY");
        }
        acceptanceMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateAcceptance(AcceptanceSaveReqVO updateReqVO) {
        AcceptanceDO existing = validateExists(updateReqVO.getId());
        // 校验项目内编码唯一
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getProjectId(), updateReqVO.getCode());
        // 仅草稿态允许修改核心字段
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)) {
            throw exception(ACC_ACCEPTANCE_STATUS_INVALID);
        }
        AcceptanceDO updateObj = BeanUtils.toBean(updateReqVO, AcceptanceDO.class);
        // 保持状态不被前端覆盖
        updateObj.setStatus(existing.getStatus());
        acceptanceMapper.updateById(updateObj);
    }

    @Override
    public void deleteAcceptance(Long id) {
        AcceptanceDO existing = validateExists(id);
        // 仅草稿或已驳回状态允许删除
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)
                && !Objects.equals(existing.getStatus(), STATUS_REJECTED)) {
            throw exception(ACC_ACCEPTANCE_STATUS_INVALID);
        }
        acceptanceMapper.deleteById(id);
    }

    @Override
    public PageResult<AcceptanceDO> getAcceptancePage(AcceptancePageReqVO pageReqVO) {
        return acceptanceMapper.selectPage(pageReqVO);
    }

    @Override
    public AcceptanceDO getAcceptance(Long id) {
        return acceptanceMapper.selectById(id);
    }

    @Override
    public void submitAcceptance(Long id) {
        AcceptanceDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_DRAFT)) {
            throw exception(ACC_ACCEPTANCE_STATUS_INVALID);
        }
        AcceptanceDO updateObj = new AcceptanceDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_PENDING_SUBMIT);
        updateObj.setApplyTime(LocalDateTime.now());
        acceptanceMapper.updateById(updateObj);
    }

    @Override
    public void approveAcceptance(Long id) {
        AcceptanceDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_PENDING_SUBMIT)) {
            throw exception(ACC_ACCEPTANCE_STATUS_INVALID);
        }
        updateStatus(id, STATUS_APPROVING);
    }

    @Override
    public void passAcceptance(Long id) {
        AcceptanceDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_APPROVING)) {
            throw exception(ACC_ACCEPTANCE_STATUS_INVALID);
        }
        // FR-ACC-005 门禁：校验关联的必交交付件全部通过
        validateDeliverableComplete(id);
        AcceptanceDO updateObj = new AcceptanceDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_PASSED);
        updateObj.setApproveTime(LocalDateTime.now());
        acceptanceMapper.updateById(updateObj);
    }

    @Override
    public void rejectAcceptance(Long id) {
        AcceptanceDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_APPROVING)) {
            throw exception(ACC_ACCEPTANCE_STATUS_INVALID);
        }
        AcceptanceDO updateObj = new AcceptanceDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_REJECTED);
        updateObj.setApproveTime(LocalDateTime.now());
        acceptanceMapper.updateById(updateObj);
    }

    @Override
    public void archiveAcceptance(Long id) {
        AcceptanceDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_PASSED)) {
            throw exception(ACC_ACCEPTANCE_STATUS_INVALID);
        }
        AcceptanceDO updateObj = new AcceptanceDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_ARCHIVED);
        updateObj.setArchiveTime(LocalDateTime.now());
        acceptanceMapper.updateById(updateObj);
    }

    /**
     * FR-ACC-005 交付件完整性门禁：校验该验收关联的所有 REQUIRED 交付件均已通过
     */
    private void validateDeliverableComplete(Long acceptanceId) {
        List<DeliverableChecklistDO> requiredList =
                deliverableChecklistMapper.selectListByAcceptanceIdAndType(acceptanceId, DELIVERABLE_TYPE_REQUIRED);
        if (requiredList == null || requiredList.isEmpty()) {
            // 无必交交付件时，门禁默认放行（由配置或上层规则决定必交清单）
            return;
        }
        for (DeliverableChecklistDO deliverable : requiredList) {
            if (!Objects.equals(deliverable.getStatus(), DELIVERABLE_STATUS_PASSED)) {
                throw exception(ACC_ACCEPTANCE_DELIVERABLE_INCOMPLETE);
            }
        }
    }

    private void updateStatus(Long id, int status) {
        AcceptanceDO updateObj = new AcceptanceDO();
        updateObj.setId(id);
        updateObj.setStatus(status);
        acceptanceMapper.updateById(updateObj);
    }

    private AcceptanceDO validateExists(Long id) {
        if (id == null) {
            throw exception(ACC_ACCEPTANCE_NOT_EXISTS);
        }
        AcceptanceDO entity = acceptanceMapper.selectById(id);
        if (entity == null) {
            throw exception(ACC_ACCEPTANCE_NOT_EXISTS);
        }
        return entity;
    }

    private void validateCodeUnique(Long id, Long projectId, String code) {
        if (projectId == null || code == null) {
            return;
        }
        AcceptanceDO existing = acceptanceMapper.selectByProjectIdAndCode(projectId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !id.equals(existing.getId())) {
            throw exception(ACC_ACCEPTANCE_CODE_DUPLICATE, code);
        }
    }

}
