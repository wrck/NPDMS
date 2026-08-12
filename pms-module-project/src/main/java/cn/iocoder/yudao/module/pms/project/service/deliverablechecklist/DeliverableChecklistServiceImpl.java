package cn.iocoder.yudao.module.pms.project.service.deliverablechecklist;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.project.controller.admin.deliverablechecklist.vo.DeliverableChecklistPageReqVO;
import cn.iocoder.yudao.module.pms.project.controller.admin.deliverablechecklist.vo.DeliverableChecklistSaveReqVO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.deliverablechecklist.DeliverableChecklistDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.deliverablechecklist.DeliverableChecklistMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_DELIVERABLE_CHECKLIST_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_DELIVERABLE_CHECKLIST_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.ACC_DELIVERABLE_CHECKLIST_STATUS_INVALID;

/**
 * 交付件完整性检查 Service 实现类
 * <p>
 * 状态机：0草稿 → 1已提交 → 2已通过 / 3已驳回
 */
@Service
@Validated
public class DeliverableChecklistServiceImpl implements DeliverableChecklistService {

    /**
     * 状态：0草稿
     */
    private static final int STATUS_DRAFT = 0;
    /**
     * 状态：1已提交
     */
    private static final int STATUS_SUBMITTED = 1;
    /**
     * 状态：2已通过
     */
    private static final int STATUS_PASSED = 2;
    /**
     * 状态：3已驳回
     */
    private static final int STATUS_REJECTED = 3;

    /**
     * 交付件类型：必交
     */
    private static final String TYPE_REQUIRED = "REQUIRED";

    @Resource
    private DeliverableChecklistMapper deliverableChecklistMapper;

    @Override
    public Long createDeliverableChecklist(DeliverableChecklistSaveReqVO createReqVO) {
        // 校验项目内编码唯一
        validateCodeUnique(null, createReqVO.getProjectId(), createReqVO.getCode());
        // 插入
        DeliverableChecklistDO entity = BeanUtils.toBean(createReqVO, DeliverableChecklistDO.class);
        if (entity.getStatus() == null) {
            entity.setStatus(STATUS_DRAFT);
        }
        if (entity.getDeliverableType() == null) {
            entity.setDeliverableType(TYPE_REQUIRED);
        }
        deliverableChecklistMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateDeliverableChecklist(DeliverableChecklistSaveReqVO updateReqVO) {
        DeliverableChecklistDO existing = validateExists(updateReqVO.getId());
        // 校验项目内编码唯一
        validateCodeUnique(updateReqVO.getId(), updateReqVO.getProjectId(), updateReqVO.getCode());
        // 仅草稿态允许修改核心字段
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)) {
            throw exception(ACC_DELIVERABLE_CHECKLIST_STATUS_INVALID);
        }
        DeliverableChecklistDO updateObj = BeanUtils.toBean(updateReqVO, DeliverableChecklistDO.class);
        // 保持状态不被前端覆盖
        updateObj.setStatus(existing.getStatus());
        deliverableChecklistMapper.updateById(updateObj);
    }

    @Override
    public void deleteDeliverableChecklist(Long id) {
        DeliverableChecklistDO existing = validateExists(id);
        // 仅草稿或已驳回状态允许删除
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)
                && !Objects.equals(existing.getStatus(), STATUS_REJECTED)) {
            throw exception(ACC_DELIVERABLE_CHECKLIST_STATUS_INVALID);
        }
        deliverableChecklistMapper.deleteById(id);
    }

    @Override
    public PageResult<DeliverableChecklistDO> getDeliverableChecklistPage(DeliverableChecklistPageReqVO pageReqVO) {
        return deliverableChecklistMapper.selectPage(pageReqVO);
    }

    @Override
    public DeliverableChecklistDO getDeliverableChecklist(Long id) {
        return deliverableChecklistMapper.selectById(id);
    }

    @Override
    public void submitDeliverableChecklist(Long id) {
        DeliverableChecklistDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_DRAFT)) {
            throw exception(ACC_DELIVERABLE_CHECKLIST_STATUS_INVALID);
        }
        updateStatus(id, STATUS_SUBMITTED);
    }

    @Override
    public void passDeliverableChecklist(Long id) {
        DeliverableChecklistDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_SUBMITTED)) {
            throw exception(ACC_DELIVERABLE_CHECKLIST_STATUS_INVALID);
        }
        DeliverableChecklistDO updateObj = new DeliverableChecklistDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_PASSED);
        updateObj.setCheckTime(LocalDateTime.now());
        deliverableChecklistMapper.updateById(updateObj);
    }

    @Override
    public void rejectDeliverableChecklist(Long id) {
        DeliverableChecklistDO entity = validateExists(id);
        if (!Objects.equals(entity.getStatus(), STATUS_SUBMITTED)) {
            throw exception(ACC_DELIVERABLE_CHECKLIST_STATUS_INVALID);
        }
        updateStatus(id, STATUS_REJECTED);
    }

    private void updateStatus(Long id, int status) {
        DeliverableChecklistDO updateObj = new DeliverableChecklistDO();
        updateObj.setId(id);
        updateObj.setStatus(status);
        deliverableChecklistMapper.updateById(updateObj);
    }

    private DeliverableChecklistDO validateExists(Long id) {
        if (id == null) {
            throw exception(ACC_DELIVERABLE_CHECKLIST_NOT_EXISTS);
        }
        DeliverableChecklistDO entity = deliverableChecklistMapper.selectById(id);
        if (entity == null) {
            throw exception(ACC_DELIVERABLE_CHECKLIST_NOT_EXISTS);
        }
        return entity;
    }

    private void validateCodeUnique(Long id, Long projectId, String code) {
        if (projectId == null || code == null) {
            return;
        }
        DeliverableChecklistDO existing = deliverableChecklistMapper.selectByProjectIdAndCode(projectId, code);
        if (existing == null) {
            return;
        }
        if (id == null || !id.equals(existing.getId())) {
            throw exception(ACC_DELIVERABLE_CHECKLIST_CODE_DUPLICATE, code);
        }
    }

}
