package cn.iocoder.yudao.module.pms.acceptance.service.deliverablechecklist;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.deliverablechecklist.vo.DeliverableChecklistPageReqVO;
import cn.iocoder.yudao.module.pms.acceptance.controller.admin.deliverablechecklist.vo.DeliverableChecklistSaveReqVO;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.deliverablechecklist.DeliverableChecklistDO;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.deliverablechecklist.DeliverableChecklistMapper;
import cn.iocoder.yudao.module.pms.acceptance.service.AcceptanceRecordCodeGenerator;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.acceptance.enums.ErrorCodeConstants.ACC_DELIVERABLE_CHECKLIST_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.pms.acceptance.enums.ErrorCodeConstants.ACC_DELIVERABLE_CHECKLIST_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.acceptance.enums.ErrorCodeConstants.ACC_DELIVERABLE_CHECKLIST_STATUS_INVALID;

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
    @Resource
    private AcceptanceRecordCodeGenerator recordCodeGenerator;
    @Resource private DeliverableChecklistDeliveryAccess deliveryAccess;
    @Resource private ChecklistAttachmentRegistration attachments;
    @Resource private cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi materials;

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public Long createDeliverableChecklist(DeliverableChecklistSaveReqVO createReqVO) {
        // 插入；编码由系统按项目编码自动生成
        if(createReqVO.getStatus()!=null && createReqVO.getStatus()!=STATUS_DRAFT)throw exception(ACC_DELIVERABLE_CHECKLIST_STATUS_INVALID);
        DeliverableChecklistDO entity = BeanUtils.toBean(createReqVO, DeliverableChecklistDO.class);
        entity.setCode(recordCodeGenerator.next(createReqVO.getProjectId(),
                AcceptanceRecordCodeGenerator.DELIVERABLE_CHECKLIST, deliverableChecklistMapper));
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
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public void updateDeliverableChecklist(DeliverableChecklistSaveReqVO updateReqVO) {
        DeliverableChecklistDO existing = validateExists(updateReqVO.getId());
        deliveryAccess.require(existing,"update");
        if (!Objects.equals(existing.getProjectId(), updateReqVO.getProjectId())
                && !materials.listByEntity("ACC", "deliverableChecklist", existing.getId()).isEmpty()) {
            throw new cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException("CHECKLIST_SOURCE_MOVE_DENIED", "Registered attachment Owner cannot move projects");
        }
        // 仅草稿态允许修改核心字段（编码由系统生成不可改）
        if (!Objects.equals(existing.getStatus(), STATUS_DRAFT)) {
            throw exception(ACC_DELIVERABLE_CHECKLIST_STATUS_INVALID);
        }
        DeliverableChecklistDO updateObj = BeanUtils.toBean(updateReqVO, DeliverableChecklistDO.class);
        // 保持状态不被前端覆盖
        updateObj.setStatus(existing.getStatus());
        updateObj.setTenantId(existing.getTenantId());
        deliveryAccess.require(updateObj,"update");
        updateRecord(updateObj);
        attachments.register(existing.getId());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public void deleteDeliverableChecklist(Long id) {
        DeliverableChecklistDO existing = validateExists(id);
        deliveryAccess.require(existing,"delete");
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
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public void submitDeliverableChecklist(Long id) {
        DeliverableChecklistDO entity = validateExists(id);
        deliveryAccess.require(entity,"submit");
        if (!Objects.equals(entity.getStatus(), STATUS_DRAFT)) {
            throw exception(ACC_DELIVERABLE_CHECKLIST_STATUS_INVALID);
        }
        attachments.register(entity.getId());
        updateStatus(entity, STATUS_SUBMITTED);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public void passDeliverableChecklist(Long id) {
        DeliverableChecklistDO entity = validateExists(id);
        deliveryAccess.require(entity,"audit");
        if (!Objects.equals(entity.getStatus(), STATUS_SUBMITTED)) {
            throw exception(ACC_DELIVERABLE_CHECKLIST_STATUS_INVALID);
        }
        DeliverableChecklistDO updateObj = new DeliverableChecklistDO();
        updateObj.setId(id);
        updateObj.setStatus(STATUS_PASSED);
        updateObj.setCheckTime(LocalDateTime.now());
        updateObj.setVersion(entity.getVersion());
        updateRecord(updateObj);
        materials.registerBusinessResultMaterial("ACC","deliverableChecklist",id,"DELIVERABLE_CHECKLIST",
                "deliverableChecklist",id.toString(),null,entity.getName(),entity.getProjectId());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor=Exception.class)
    public void rejectDeliverableChecklist(Long id) {
        DeliverableChecklistDO entity = validateExists(id);
        deliveryAccess.require(entity,"audit");
        if (!Objects.equals(entity.getStatus(), STATUS_SUBMITTED)) {
            throw exception(ACC_DELIVERABLE_CHECKLIST_STATUS_INVALID);
        }
        updateStatus(entity, STATUS_REJECTED);
    }

    private void updateStatus(DeliverableChecklistDO entity, int status) {
        DeliverableChecklistDO updateObj = new DeliverableChecklistDO();
        updateObj.setId(entity.getId());
        updateObj.setVersion(entity.getVersion());
        updateObj.setStatus(status);
        updateRecord(updateObj);
    }

    private void updateRecord(DeliverableChecklistDO row) {
        if(row.getVersion()==null || deliverableChecklistMapper.updateById(row)!=1)
            throw new cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException("CHECKLIST_VERSION_CONFLICT","Current native checklist version required");
    }
    private DeliverableChecklistDO validateExists(Long id) {
        if (id == null) {
            throw exception(ACC_DELIVERABLE_CHECKLIST_NOT_EXISTS);
        }
        DeliverableChecklistDO entity = deliverableChecklistMapper.selectOwnerForUpdate(new cn.iocoder.yudao.module.pms.acceptance.dal.mysql.deliverablechecklist.query.DeliverableChecklistOwnerLockQuery(cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder.getRequiredTenantId(),id));
        if (entity == null) {
            throw exception(ACC_DELIVERABLE_CHECKLIST_NOT_EXISTS);
        }
        return entity;
    }


}
