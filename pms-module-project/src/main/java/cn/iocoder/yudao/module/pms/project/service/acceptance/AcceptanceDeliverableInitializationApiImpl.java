package cn.iocoder.yudao.module.pms.project.service.acceptance;

import cn.iocoder.yudao.module.pms.project.api.acceptance.AcceptanceDeliverableInitializationApi;
import cn.iocoder.yudao.module.pms.project.api.acceptance.dto.DeliverableInitializationCommand;
import cn.iocoder.yudao.module.pms.project.api.acceptance.dto.DeliverableInitializationResult;
import cn.iocoder.yudao.module.pms.project.api.acceptance.dto.DeliverableRequirementSnapshot;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.deliverablechecklist.DeliverableChecklistDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.deliverablechecklist.DeliverableChecklistMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** ADR-0032: ACC 必须加入 PROJ 发起的同一 MySQL 本地事务。 */
@Service
public class AcceptanceDeliverableInitializationApiImpl implements AcceptanceDeliverableInitializationApi {

    private static final String STATUS_PENDING = "PENDING";

    private final DeliverableChecklistMapper deliverableMapper;

    public AcceptanceDeliverableInitializationApiImpl(DeliverableChecklistMapper deliverableMapper) {
        this.deliverableMapper = deliverableMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public DeliverableInitializationResult initialize(DeliverableInitializationCommand command) {
        validate(command);
        List<Long> ids = new ArrayList<>(command.requirements().size());
        for (DeliverableRequirementSnapshot requirement : command.requirements()) {
            ids.add(insertOrLoad(command, requirement));
        }
        if (ids.size() != command.requirements().size()) {
            throw new IllegalStateException("deliverable initialization count mismatch");
        }
        return new DeliverableInitializationResult(ids.size(), ids);
    }

    private Long insertOrLoad(DeliverableInitializationCommand command,
                              DeliverableRequirementSnapshot requirement) {
        DeliverableChecklistDO deliverable = new DeliverableChecklistDO();
        deliverable.setTenantId(command.tenantId());
        deliverable.setProjectId(command.projectId());
        deliverable.setTemplateRequirementKey(requirement.requirementKey());
        deliverable.setSourceTemplateRevisionId(command.templateRevisionId());
        deliverable.setApplicableStageCode(requirement.applicableStageCode());
        deliverable.setRequiredFlag(requirement.required());
        deliverable.setTemplateId(requirement.deliverableTemplateId());
        deliverable.setCode(requirement.requirementKey());
        deliverable.setName(requirement.requirementKey());
        deliverable.setDeliverableType(requirement.deliverableType());
        deliverable.setStatus(STATUS_PENDING);
        deliverableMapper.insertInitializationIgnore(deliverable);
        DeliverableChecklistDO persisted = deliverableMapper.selectByInitializationKey(command.tenantId(),
                command.projectId(), command.templateRevisionId(), requirement.requirementKey());
        if (persisted == null) {
            throw new IllegalStateException("deliverable initialization row unavailable");
        }
        return persisted.getId();
    }

    private void validate(DeliverableInitializationCommand command) {
        if (command == null || command.tenantId() <= 0 || command.projectId() <= 0
                || command.templateRevisionId() <= 0) {
            throw new IllegalArgumentException("tenant, project and template revision must be positive");
        }
        Set<String> keys = new HashSet<>();
        for (DeliverableRequirementSnapshot requirement : command.requirements()) {
            if (requirement == null || isBlank(requirement.requirementKey())
                    || isBlank(requirement.deliverableType()) || isBlank(requirement.applicableStageCode())
                    || requirement.requirementKey().length() > 64 || requirement.deliverableType().length() > 32
                    || requirement.applicableStageCode().length() > 32
                    || !keys.add(requirement.requirementKey())) {
                throw new IllegalArgumentException("deliverable requirements must be complete and unique");
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
