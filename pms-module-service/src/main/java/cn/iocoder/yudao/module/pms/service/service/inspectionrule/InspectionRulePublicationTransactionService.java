package cn.iocoder.yudao.module.pms.service.service.inspectionrule;

import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleProductTypeRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleProductTypeRevisionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleRevisionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command.InspectionRuleDisableUpdate;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command.InspectionRuleProductTypeNameUpdate;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command.InspectionRulePublishUpdate;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.projection.InspectionRulePublicationLockProjection;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleChildrenQuery;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRulePublicationLockQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_DRAFT_INVALID;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_REVISION_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_REVISION_VERSION_CONFLICT;

@Service
@RequiredArgsConstructor
class InspectionRulePublicationTransactionService {

    private final InspectionRuleRevisionMapper revisionMapper;
    private final InspectionRuleProductTypeRevisionMapper productTypeMapper;

    @Transactional(rollbackFor = Exception.class)
    PublishResult publishVerified(PublishCommand command) {
        InspectionRuleRevisionDO inspected = revisionMapper.selectById(command.revisionId());
        if (inspected == null || !Objects.equals(inspected.getTenantId(), command.tenantId())) {
            throw exception(INSPECTION_RULE_REVISION_NOT_EXISTS);
        }
        if (!Objects.equals(inspected.getVersion(), command.expectedVersion())) {
            throw exception(INSPECTION_RULE_REVISION_VERSION_CONFLICT);
        }
        if (!"DRAFT".equals(inspected.getStatusCode())) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
        InspectionRulePublicationLockProjection locked = revisionMapper.selectPublicationLockForUpdate(
                new InspectionRulePublicationLockQuery(command.tenantId(), inspected.getRuleId(), inspected.getId()));
        if (locked == null || !Objects.equals(locked.targetRevisionId(), inspected.getId())) {
            throw exception(INSPECTION_RULE_REVISION_NOT_EXISTS);
        }
        if (!"DRAFT".equals(locked.targetRevisionStatusCode())) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
        if (!Objects.equals(locked.targetRevisionVersion(), command.expectedVersion())
                || !Objects.equals(locked.currentPublishedRevisionId(), command.expectedPublishedRevisionId())) {
            throw exception(INSPECTION_RULE_REVISION_VERSION_CONFLICT);
        }
        List<InspectionRuleProductTypeRevisionDO> productTypes = productTypeMapper.selectListByRevisionIds(
                new InspectionRuleChildrenQuery(command.tenantId(), Set.of(inspected.getId()), null));
        validateProductTypeNames(productTypes, command.productTypeNames());
        for (InspectionRuleProductTypeRevisionDO productType : productTypes) {
            if (productTypeMapper.updateNameSnapshot(new InspectionRuleProductTypeNameUpdate(
                    command.tenantId(), inspected.getId(), productType.getProductTypeCode(),
                    command.productTypeNames().get(productType.getProductTypeCode()))) != 1) {
                throw exception(INSPECTION_RULE_REVISION_VERSION_CONFLICT);
            }
        }
        if (locked.currentPublishedRevisionId() != null
                && revisionMapper.disablePublishedIfMatch(new InspectionRuleDisableUpdate(
                command.tenantId(), locked.currentPublishedRevisionId(), locked.currentPublishedRevisionVersion(),
                command.actorId(), command.publishedAt())) != 1) {
            throw exception(INSPECTION_RULE_REVISION_VERSION_CONFLICT);
        }
        if (revisionMapper.publishDraftIfMatch(new InspectionRulePublishUpdate(
                command.tenantId(), inspected.getId(), command.expectedVersion(), command.categoryNameSnapshot(),
                command.severityNameSnapshot(), command.actorId(), command.publishedAt())) != 1) {
            throw exception(INSPECTION_RULE_REVISION_VERSION_CONFLICT);
        }
        return new PublishResult(inspected.getId(), command.expectedVersion() + 1,
                locked.currentPublishedRevisionId());
    }

    private static void validateProductTypeNames(
            List<InspectionRuleProductTypeRevisionDO> productTypes,
            Map<String, String> productTypeNames) {
        if (productTypeNames == null || productTypes == null || productTypes.isEmpty()) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
        Set<String> persistedCodes = productTypes.stream()
                .map(InspectionRuleProductTypeRevisionDO::getProductTypeCode)
                .collect(Collectors.toSet());
        if (persistedCodes.size() != productTypes.size()
                || !persistedCodes.equals(productTypeNames.keySet())
                || productTypeNames.values().stream().anyMatch(name -> name == null || name.isBlank())) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
    }

    record PublishCommand(
            Long tenantId,
            Long revisionId,
            Integer expectedVersion,
            Long expectedPublishedRevisionId,
            String categoryNameSnapshot,
            String severityNameSnapshot,
            Map<String, String> productTypeNames,
            Long actorId,
            LocalDateTime publishedAt) {
    }

    record PublishResult(
            Long revisionId,
            Integer version,
            Long disabledRevisionId) {
    }
}
