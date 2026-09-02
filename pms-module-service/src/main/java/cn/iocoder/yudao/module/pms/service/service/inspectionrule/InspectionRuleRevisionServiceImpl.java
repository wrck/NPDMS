package cn.iocoder.yudao.module.pms.service.service.inspectionrule;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodeResult;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodesQuery;
import cn.iocoder.yudao.module.pms.asset.api.producttype.inspection.InspectionAssetProductTypeApi;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleCommandRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleProductTypeRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleCommandRevisionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleProductTypeRevisionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleRevisionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command.InspectionRuleDraftUpdate;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleChildrenQuery;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleDetectionIdQuery;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleIdentityLockQuery;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleNameQuery;
import cn.iocoder.yudao.module.pms.service.domain.inspectionrule.InspectionRuleRevisionRules;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.security.InspectionRuleManagePermissionGuard;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_DETECTION_ID_DUPLICATE;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_DRAFT_INVALID;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_NAME_DUPLICATE;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_REVISION_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.service.enums.ErrorCodeConstants.INSPECTION_RULE_REVISION_VERSION_CONFLICT;

@Service
public class InspectionRuleRevisionServiceImpl implements InspectionRuleRevisionService {

    @Resource
    private InspectionRuleMapper ruleMapper;
    @Resource
    private InspectionRuleRevisionMapper revisionMapper;
    @Resource
    private InspectionRuleCommandRevisionMapper commandMapper;
    @Resource
    private InspectionRuleProductTypeRevisionMapper productTypeMapper;
    @Resource
    private InspectionAssetProductTypeApi assetProductTypeApi;
    @Resource
    private DictDataApi dictDataApi;
    @Resource
    private InspectionRuleManagePermissionGuard managePermissionGuard;

    private final InspectionRuleRevisionRules revisionRules = new InspectionRuleRevisionRules();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DraftResult createDraft(CreateDraftCommand command) {
        managePermissionGuard.check();
        String detectionId = trimRequired(command == null ? null : command.detectionId());
        String ruleName = trimRequired(command == null ? null : command.ruleName());
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (ruleMapper.selectByTenantAndDetectionId(new InspectionRuleDetectionIdQuery(tenantId, detectionId)) != null) {
            throw exception(INSPECTION_RULE_DETECTION_ID_DUPLICATE, detectionId);
        }
        if (ruleMapper.selectByTenantAndRuleName(new InspectionRuleNameQuery(tenantId, ruleName)) != null) {
            throw exception(INSPECTION_RULE_NAME_DUPLICATE, ruleName);
        }
        InspectionRuleDO rule = new InspectionRuleDO();
        rule.setId(IdWorker.getId());
        rule.setTenantId(tenantId);
        rule.setDetectionId(detectionId);
        rule.setRuleName(ruleName);
        rule.setVersion(0);
        try {
            if (ruleMapper.insert(rule) != 1) {
                throw new IllegalStateException("INSPECTION_RULE_CREATE_WRITE_FAILED");
            }
        } catch (DuplicateKeyException conflict) {
            throw mapIdentityConflict(conflict, detectionId, ruleName);
        }
        InspectionRuleRevisionDO revision = new InspectionRuleRevisionDO();
        revision.setId(IdWorker.getId());
        revision.setTenantId(tenantId);
        revision.setRuleId(rule.getId());
        revision.setRevisionNo(1);
        revision.setStatusCode("DRAFT");
        revision.setRuleNameSnapshot(ruleName);
        revision.setVersion(0);
        if (revisionMapper.insert(revision) != 1) {
            throw new IllegalStateException("INSPECTION_RULE_DRAFT_CREATE_WRITE_FAILED");
        }
        return new DraftResult(rule.getId(), revision.getId(), revision.getRevisionNo(), revision.getVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DraftResult saveDraft(SaveDraftCommand command) {
        managePermissionGuard.check();
        if (command == null || command.revisionId() == null || command.expectedVersion() == null
                || command.expectedVersion() < 0) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
        validateLocalDraft(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        InspectionRuleRevisionDO current = revisionMapper.selectById(command.revisionId());
        if (current == null || !Objects.equals(current.getTenantId(), tenantId)) {
            throw exception(INSPECTION_RULE_REVISION_NOT_EXISTS);
        }
        if (!"DRAFT".equals(current.getStatusCode())) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
        InspectionRuleDraftUpdate update = toRevisionUpdate(command, current);
        if (revisionMapper.updateDraftIfMatch(update) != 1) {
            throw exception(INSPECTION_RULE_REVISION_VERSION_CONFLICT);
        }
        InspectionRuleChildrenQuery childrenQuery =
                new InspectionRuleChildrenQuery(tenantId, Set.of(current.getId()), null);
        commandMapper.hardDeleteByRevisionIds(childrenQuery);
        productTypeMapper.hardDeleteByRevisionIds(childrenQuery);
        command.commands().forEach(item -> insertCommand(tenantId, current.getId(), item));
        command.productTypes().forEach(item -> insertProductType(tenantId, current.getId(), item));
        return new DraftResult(current.getRuleId(), current.getId(), current.getRevisionNo(), current.getVersion() + 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DraftResult copyRevision(Long sourceRevisionId) {
        managePermissionGuard.check();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        InspectionRuleRevisionDO source = requireRevision(sourceRevisionId, tenantId);
        if (!Set.of("PUBLISHED", "DISABLED").contains(source.getStatusCode())) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
        InspectionRuleIdentityLockQuery lockQuery = new InspectionRuleIdentityLockQuery(tenantId, source.getRuleId());
        InspectionRuleDO rule = ruleMapper.selectByIdForUpdate(lockQuery);
        if (rule == null) {
            throw exception(INSPECTION_RULE_REVISION_NOT_EXISTS);
        }
        Integer maxRevisionNo = revisionMapper.selectMaxRevisionNoByRule(lockQuery);
        InspectionRuleRevisionDO target = copyRevisionRow(source, maxRevisionNo == null ? 1 : maxRevisionNo + 1);
        if (revisionMapper.insert(target) != 1) {
            throw new IllegalStateException("INSPECTION_RULE_DRAFT_CREATE_WRITE_FAILED");
        }
        InspectionRuleChildrenQuery childrenQuery =
                new InspectionRuleChildrenQuery(tenantId, Set.of(source.getId()), null);
        commandMapper.selectListByRevisionIds(childrenQuery)
                .forEach(item -> insertCopiedCommand(tenantId, target.getId(), item));
        productTypeMapper.selectListByRevisionIds(childrenQuery)
                .forEach(item -> insertCopiedProductType(tenantId, target.getId(), item));
        return new DraftResult(rule.getId(), target.getId(), target.getRevisionNo(), target.getVersion());
    }

    @Override
    @Transactional(readOnly = true)
    public ValidationResult validateRevision(Long revisionId) {
        managePermissionGuard.check();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        InspectionRuleRevisionDO revision = requireRevision(revisionId, tenantId);
        if (!"DRAFT".equals(revision.getStatusCode())) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
        InspectionRuleDO rule = ruleMapper.selectById(revision.getRuleId());
        if (rule == null || !Objects.equals(rule.getTenantId(), tenantId)) {
            throw exception(INSPECTION_RULE_REVISION_NOT_EXISTS);
        }
        InspectionRuleChildrenQuery childrenQuery =
                new InspectionRuleChildrenQuery(tenantId, Set.of(revision.getId()), null);
        List<InspectionRuleCommandRevisionDO> commands = commandMapper.selectListByRevisionIds(childrenQuery);
        List<InspectionRuleProductTypeRevisionDO> productTypes = productTypeMapper.selectListByRevisionIds(childrenQuery);
        List<InspectionRuleRevisionRules.ValidationError> errors = new ArrayList<>(revisionRules.validate(
                toRevisionDefinition(rule, revision, commands, productTypes)));
        List<DictionaryNameCandidate> dictionaryCandidates = new ArrayList<>();
        validateEnabledDictionaryValue(errors, dictionaryCandidates, "categoryCode",
                "pms_inspection_rule_category", revision.getCategoryCode());
        validateEnabledDictionaryValue(errors, dictionaryCandidates, "severityCode",
                "pms_inspection_rule_severity", revision.getSeverityCode());
        List<ProductTypeNameCandidate> productTypeCandidates = validateProductTypesWithAsset(errors, productTypes);
        return new ValidationResult(errors, dictionaryCandidates, productTypeCandidates);
    }

    private InspectionRuleRevisionDO requireRevision(Long revisionId, Long tenantId) {
        if (revisionId == null) {
            throw exception(INSPECTION_RULE_REVISION_NOT_EXISTS);
        }
        InspectionRuleRevisionDO revision = revisionMapper.selectById(revisionId);
        if (revision == null || !Objects.equals(revision.getTenantId(), tenantId)) {
            throw exception(INSPECTION_RULE_REVISION_NOT_EXISTS);
        }
        return revision;
    }

    private static InspectionRuleDraftUpdate toRevisionUpdate(
            SaveDraftCommand command,
            InspectionRuleRevisionDO current) {
        return new InspectionRuleDraftUpdate(
                current.getTenantId(),
                current.getId(),
                command.expectedVersion(),
                trim(command.inspectionItem()),
                trim(command.description()),
                trim(command.categoryCode()),
                trim(command.categoryNameSnapshot()),
                trim(command.severityCode()),
                trim(command.severityNameSnapshot()),
                command.sortOrder(),
                trim(command.expectedResultRegex()),
                trim(command.thresholdDataType()),
                trim(command.thresholdOperator()),
                command.thresholdValue(),
                trim(command.thresholdUnit()));
    }

    private void insertCommand(Long tenantId, Long revisionId, CommandDraft item) {
        InspectionRuleCommandRevisionDO row = new InspectionRuleCommandRevisionDO();
        row.setId(IdWorker.getId());
        row.setTenantId(tenantId);
        row.setRevisionId(revisionId);
        row.setStableCommandKey(trim(item.stableCommandKey()));
        row.setCommandContent(trim(item.commandContent()));
        row.setExecutionOrder(item.executionOrder());
        row.setTimeoutSeconds(item.timeoutSeconds());
        row.setContinueOnTimeout(item.continueOnTimeout());
        row.setVersion(0);
        if (commandMapper.insert(row) != 1) {
            throw new IllegalStateException("INSPECTION_RULE_COMMAND_WRITE_FAILED");
        }
    }

    private void insertProductType(Long tenantId, Long revisionId, ProductTypeDraft item) {
        InspectionRuleProductTypeRevisionDO row = new InspectionRuleProductTypeRevisionDO();
        row.setId(IdWorker.getId());
        row.setTenantId(tenantId);
        row.setRevisionId(revisionId);
        row.setProductTypeCode(trim(item.productTypeCode()));
        row.setProductTypeNameSnapshot(trim(item.productTypeNameSnapshot()));
        row.setVersion(0);
        if (productTypeMapper.insert(row) != 1) {
            throw new IllegalStateException("INSPECTION_RULE_PRODUCT_TYPE_WRITE_FAILED");
        }
    }

    private static InspectionRuleRevisionDO copyRevisionRow(InspectionRuleRevisionDO source, int revisionNo) {
        InspectionRuleRevisionDO target = new InspectionRuleRevisionDO();
        target.setId(IdWorker.getId());
        target.setTenantId(source.getTenantId());
        target.setRuleId(source.getRuleId());
        target.setRevisionNo(revisionNo);
        target.setStatusCode("DRAFT");
        target.setRuleNameSnapshot(source.getRuleNameSnapshot());
        target.setInspectionItem(source.getInspectionItem());
        target.setDescription(source.getDescription());
        target.setCategoryCode(source.getCategoryCode());
        target.setCategoryNameSnapshot(source.getCategoryNameSnapshot());
        target.setSeverityCode(source.getSeverityCode());
        target.setSeverityNameSnapshot(source.getSeverityNameSnapshot());
        target.setSortOrder(source.getSortOrder());
        target.setExpectedResultRegex(source.getExpectedResultRegex());
        target.setThresholdDataType(source.getThresholdDataType());
        target.setThresholdOperator(source.getThresholdOperator());
        target.setThresholdValue(source.getThresholdValue());
        target.setThresholdUnit(source.getThresholdUnit());
        target.setVersion(0);
        return target;
    }

    private void insertCopiedCommand(Long tenantId, Long revisionId, InspectionRuleCommandRevisionDO source) {
        insertCommand(tenantId, revisionId, new CommandDraft(
                source.getStableCommandKey(), source.getCommandContent(), source.getExecutionOrder(),
                source.getTimeoutSeconds(), source.getContinueOnTimeout()));
    }

    private void insertCopiedProductType(
            Long tenantId,
            Long revisionId,
            InspectionRuleProductTypeRevisionDO source) {
        insertProductType(tenantId, revisionId, new ProductTypeDraft(
                source.getProductTypeCode(), source.getProductTypeNameSnapshot()));
    }

    private static InspectionRuleRevisionRules.RevisionDefinition toRevisionDefinition(
            InspectionRuleDO rule,
            InspectionRuleRevisionDO revision,
            List<InspectionRuleCommandRevisionDO> commands,
            List<InspectionRuleProductTypeRevisionDO> productTypes) {
        InspectionRuleRevisionRules.ThresholdDefinition threshold = hasThreshold(revision)
                ? new InspectionRuleRevisionRules.ThresholdDefinition(
                revision.getThresholdDataType(), revision.getThresholdOperator(),
                revision.getThresholdValue(), revision.getThresholdUnit())
                : null;
        return new InspectionRuleRevisionRules.RevisionDefinition(
                revision.getStatusCode(), rule.getDetectionId(), rule.getRuleName(),
                revision.getInspectionItem(), revision.getDescription(), revision.getCategoryCode(),
                revision.getSeverityCode(), revision.getSortOrder(), commands.stream()
                .map(item -> new InspectionRuleRevisionRules.CommandDefinition(
                        item.getStableCommandKey(), item.getCommandContent(),
                        intValue(item.getExecutionOrder()), intValue(item.getTimeoutSeconds()),
                        Boolean.TRUE.equals(item.getContinueOnTimeout())))
                .toList(), revision.getExpectedResultRegex(), threshold, productTypes.stream()
                .map(InspectionRuleProductTypeRevisionDO::getProductTypeCode)
                .toList());
    }

    private List<ProductTypeNameCandidate> validateProductTypesWithAsset(
            List<InspectionRuleRevisionRules.ValidationError> errors,
            List<InspectionRuleProductTypeRevisionDO> productTypes) {
        if (productTypes.isEmpty()) {
            return List.of();
        }
        List<String> codes = productTypes.stream()
                .map(InspectionRuleProductTypeRevisionDO::getProductTypeCode)
                .toList();
        Map<String, ProductTypeCodeResult> results = new HashMap<>();
        try {
            List<ProductTypeCodeResult> response = assetProductTypeApi.getByCodes(new ProductTypeCodesQuery(codes));
            if (response != null) {
                response.stream().filter(Objects::nonNull).forEach(item -> results.put(item.productTypeCode(), item));
            }
        } catch (RuntimeException exception) {
            for (int index = 0; index < productTypes.size(); index++) {
                errors.add(new InspectionRuleRevisionRules.ValidationError(
                        "productTypes[" + index + "]", "DEPENDENCY_TEMPORARY", "DEPENDENCY_TEMPORARY"));
            }
            return List.of();
        }
        List<ProductTypeNameCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < productTypes.size(); index++) {
            ProductTypeCodeResult result = results.get(productTypes.get(index).getProductTypeCode());
            if (!hasAuthoritativeSourceProof(result)) {
                errors.add(new InspectionRuleRevisionRules.ValidationError(
                        "productTypes[" + index + "]", "DEPENDENCY_REJECTED", "DEPENDENCY_REJECTED"));
                continue;
            }
            candidates.add(new ProductTypeNameCandidate(result.productTypeCode(), trim(result.displayName())));
        }
        return List.copyOf(candidates);
    }

    private void validateEnabledDictionaryValue(
            List<InspectionRuleRevisionRules.ValidationError> errors,
            List<DictionaryNameCandidate> candidates,
            String location,
            String dictType,
            String value) {
        String normalized = trim(value);
        if (normalized == null) {
            return;
        }
        try {
            dictDataApi.validateDictDataList(dictType, List.of(normalized));
            String authoritativeName = dictDataApi.getDictDataList(dictType).stream()
                    .filter(Objects::nonNull)
                    .filter(item -> Objects.equals(dictType, item.getDictType()))
                    .filter(item -> Objects.equals(normalized, trim(item.getValue())))
                    .map(DictDataRespDTO::getLabel)
                    .map(InspectionRuleRevisionServiceImpl::trim)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (authoritativeName == null) {
                errors.add(new InspectionRuleRevisionRules.ValidationError(
                        location, "DEPENDENCY_REJECTED", "DEPENDENCY_REJECTED"));
                return;
            }
            candidates.add(new DictionaryNameCandidate(location, normalized, authoritativeName));
        } catch (ServiceException exception) {
            errors.add(new InspectionRuleRevisionRules.ValidationError(
                    location, "UNSUPPORTED_VALUE", "UNSUPPORTED_VALUE"));
        } catch (RuntimeException exception) {
            errors.add(new InspectionRuleRevisionRules.ValidationError(
                    location, "DEPENDENCY_TEMPORARY", "DEPENDENCY_TEMPORARY"));
        }
    }

    private static boolean hasAuthoritativeSourceProof(ProductTypeCodeResult result) {
        return result != null && result.exists() && result.enabled()
                && trim(result.productTypeCode()) != null
                && trim(result.displayName()) != null
                && trim(result.sourceSystem()) != null
                && trim(result.sourceVersion()) != null
                && trim(result.syncStatus()) != null
                && result.lastSuccessfulSyncTime() != null;
    }

    private static boolean hasThreshold(InspectionRuleRevisionDO revision) {
        return revision.getThresholdDataType() != null || revision.getThresholdOperator() != null
                || revision.getThresholdValue() != null || revision.getThresholdUnit() != null;
    }

    private static int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private static void validateLocalDraft(SaveDraftCommand command) {
        if (command.commands().stream().anyMatch(Objects::isNull)
                || command.productTypes().stream().anyMatch(Objects::isNull)
                || command.commands().stream().anyMatch(item -> item.timeoutSeconds() != null
                && (item.timeoutSeconds() < 1 || item.timeoutSeconds() > 30))
                || hasDuplicateCommandKey(command)
                || hasDuplicateExecutionOrder(command)
                || hasDuplicateProductType(command)) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
    }

    private static boolean hasDuplicateCommandKey(SaveDraftCommand command) {
        Set<String> values = new HashSet<>();
        return command.commands().stream()
                .map(CommandDraft::stableCommandKey)
                .map(InspectionRuleRevisionServiceImpl::trim)
                .filter(Objects::nonNull)
                .anyMatch(value -> !values.add(value));
    }

    private static boolean hasDuplicateExecutionOrder(SaveDraftCommand command) {
        Set<Integer> values = new HashSet<>();
        return command.commands().stream()
                .map(CommandDraft::executionOrder)
                .filter(Objects::nonNull)
                .anyMatch(value -> !values.add(value));
    }

    private static boolean hasDuplicateProductType(SaveDraftCommand command) {
        Set<String> values = new HashSet<>();
        return command.productTypes().stream()
                .map(ProductTypeDraft::productTypeCode)
                .map(InspectionRuleRevisionServiceImpl::trim)
                .filter(Objects::nonNull)
                .anyMatch(value -> !values.add(value));
    }

    private static ServiceException mapIdentityConflict(
            DuplicateKeyException conflict,
            String detectionId,
            String ruleName) {
        String message = conflict.getMostSpecificCause() == null
                ? conflict.getMessage() : conflict.getMostSpecificCause().getMessage();
        if (message != null && message.contains("uk_srv_inspection_rule_tenant_detection")) {
            return exception(INSPECTION_RULE_DETECTION_ID_DUPLICATE, detectionId);
        }
        if (message != null && message.contains("uk_srv_inspection_rule_tenant_name")) {
            return exception(INSPECTION_RULE_NAME_DUPLICATE, ruleName);
        }
        throw conflict;
    }

    private static String trimRequired(String value) {
        String trimmed = trim(value);
        if (trimmed == null) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
        return trimmed;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
