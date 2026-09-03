package cn.iocoder.yudao.module.pms.service.service.inspectionrule;

import cn.iocoder.yudao.framework.common.biz.system.dict.dto.DictDataRespDTO;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodeResult;
import cn.iocoder.yudao.module.pms.asset.api.producttype.dto.ProductTypeCodesQuery;
import cn.iocoder.yudao.module.pms.asset.api.producttype.inspection.InspectionAssetProductTypeApi;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleCommandRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleProductTypeRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleRevisionDO;
import cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule.InspectionRuleSecurityReviewDO;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleCommandRevisionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleProductTypeRevisionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleRevisionMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.InspectionRuleSecurityReviewMapper;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command.InspectionRuleDisableUpdate;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command.InspectionRuleProductTypeNameUpdate;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.command.InspectionRulePublishUpdate;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.projection.InspectionRulePublicationLockProjection;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleChildrenQuery;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRulePublicationLockQuery;
import cn.iocoder.yudao.module.pms.service.dal.mysql.inspectionrule.query.InspectionRuleSecurityReviewQuery;
import cn.iocoder.yudao.module.pms.service.domain.inspectionrule.InspectionRuleRevisionRules;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.security.InspectionRuleContentDigestService;
import cn.iocoder.yudao.module.pms.service.service.inspectionrule.security.InspectionRuleSecurityReviewPermissionGuard;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
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
public class InspectionRulePublicationTransactionService {

    private static final String CATEGORY_DICT = "pms_inspection_rule_category";
    private static final String SEVERITY_DICT = "pms_inspection_rule_severity";

    private final InspectionRuleMapper ruleMapper;
    private final InspectionRuleRevisionMapper revisionMapper;
    private final InspectionRuleCommandRevisionMapper commandMapper;
    private final InspectionRuleProductTypeRevisionMapper productTypeMapper;
    private final InspectionRuleSecurityReviewMapper securityReviewMapper;
    private final InspectionAssetProductTypeApi assetProductTypeApi;
    private final DictDataApi dictDataApi;
    private final InspectionRuleContentDigestService contentDigestService;

    private final InspectionRuleRevisionRules revisionRules = new InspectionRuleRevisionRules();

    @Transactional(rollbackFor = Exception.class)
    SecurityReviewResult recordSecurityReview(SecurityReviewCommand command) {
        LockedDraft locked = lockDraft(
                command.tenantId(), command.revisionId(), command.expectedVersion(), null, false);
        List<InspectionRuleCommandRevisionDO> commands = loadCommands(command.tenantId(), locked.revision().getId());
        String contentDigest = digest(commands, locked.revision().getExpectedResultRegex());

        long reviewId = IdWorker.getId();
        InspectionRuleSecurityReviewDO review = new InspectionRuleSecurityReviewDO();
        review.setId(reviewId);
        review.setTenantId(command.tenantId());
        review.setReviewReference("INS-REVIEW-" + reviewId);
        review.setRevisionId(locked.revision().getId());
        review.setContentDigest(contentDigest);
        review.setReviewedBy(command.authorization().actorId());
        review.setPermissionCode(command.authorization().permissionCode());
        review.setAuthorizationType(command.authorization().authorizationType());
        review.setAuthorizationSourceId(command.authorization().authorizationSourceId());
        review.setConclusionCode(command.conclusionCode());
        review.setReviewedAt(command.reviewedAt());
        review.setVersion(0);
        if (securityReviewMapper.insert(review) != 1) {
            throw new IllegalStateException("INSPECTION_RULE_SECURITY_REVIEW_WRITE_FAILED");
        }
        return new SecurityReviewResult(
                review.getReviewReference(), review.getRevisionId(), review.getContentDigest(),
                review.getConclusionCode(), review.getReviewedAt());
    }

    @Transactional(rollbackFor = Exception.class)
    ApprovedPublishResult publishApproved(ApprovedPublishCommand command) {
        LockedDraft locked = lockDraft(
                command.tenantId(), command.revisionId(), command.expectedVersion(),
                command.expectedPublishedRevisionId(), true);
        InspectionRuleRevisionDO revision = locked.revision();
        InspectionRuleDO rule = ruleMapper.selectById(revision.getRuleId());
        if (rule == null || !Objects.equals(rule.getTenantId(), command.tenantId())) {
            throw exception(INSPECTION_RULE_REVISION_NOT_EXISTS);
        }
        List<InspectionRuleCommandRevisionDO> commands = loadCommands(command.tenantId(), revision.getId());
        List<InspectionRuleProductTypeRevisionDO> productTypes = loadProductTypes(command.tenantId(), revision.getId());
        PublicationValidation validation = validatePublication(rule, revision, commands, productTypes);
        InspectionRuleSecurityReviewDO latestReview = securityReviewMapper.selectLatestByRevisionAndDigest(
                new InspectionRuleSecurityReviewQuery(command.tenantId(), revision.getId(), validation.contentDigest()));
        if (latestReview == null || !"PASSED".equals(latestReview.getConclusionCode())) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
        applyPublication(
                locked.lock(), revision, command.actorId(), command.publishedAt(),
                validation.categoryName(), validation.severityName(), validation.productTypeNames());
        return new ApprovedPublishResult(
                revision.getId(), command.expectedVersion() + 1,
                locked.lock().currentPublishedRevisionId(), validation.contentDigest(),
                latestReview.getReviewReference());
    }

    /**
     * Task 8前序CAS基础的内部验证入口；生产发布必须调用{@link #publishApproved(ApprovedPublishCommand)}。
     */
    @Deprecated(forRemoval = true)
    @Transactional(rollbackFor = Exception.class)
    PublishResult publishVerified(PublishCommand command) {
        LockedDraft locked = lockDraft(
                command.tenantId(), command.revisionId(), command.expectedVersion(),
                command.expectedPublishedRevisionId(), true);
        List<InspectionRuleProductTypeRevisionDO> productTypes =
                loadProductTypes(command.tenantId(), locked.revision().getId());
        validateProductTypeNames(productTypes, command.productTypeNames());
        applyPublication(
                locked.lock(), locked.revision(), command.actorId(), command.publishedAt(),
                command.categoryNameSnapshot(), command.severityNameSnapshot(), command.productTypeNames());
        return new PublishResult(
                locked.revision().getId(), command.expectedVersion() + 1,
                locked.lock().currentPublishedRevisionId());
    }

    private LockedDraft lockDraft(
            Long tenantId,
            Long revisionId,
            Integer expectedVersion,
            Long expectedPublishedRevisionId,
            boolean comparePublishedRevision) {
        InspectionRuleRevisionDO inspected = requireRevision(revisionId, tenantId);
        if (!Objects.equals(inspected.getVersion(), expectedVersion)) {
            throw exception(INSPECTION_RULE_REVISION_VERSION_CONFLICT);
        }
        if (!"DRAFT".equals(inspected.getStatusCode())) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
        InspectionRulePublicationLockProjection locked = revisionMapper.selectPublicationLockForUpdate(
                new InspectionRulePublicationLockQuery(tenantId, inspected.getRuleId(), inspected.getId()));
        if (locked == null || !Objects.equals(locked.targetRevisionId(), inspected.getId())) {
            throw exception(INSPECTION_RULE_REVISION_NOT_EXISTS);
        }
        if (!"DRAFT".equals(locked.targetRevisionStatusCode())) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
        if (!Objects.equals(locked.targetRevisionVersion(), expectedVersion)
                || comparePublishedRevision
                && !Objects.equals(locked.currentPublishedRevisionId(), expectedPublishedRevisionId)) {
            throw exception(INSPECTION_RULE_REVISION_VERSION_CONFLICT);
        }
        InspectionRuleRevisionDO current = requireRevision(revisionId, tenantId);
        if (!Objects.equals(current.getVersion(), expectedVersion)) {
            throw exception(INSPECTION_RULE_REVISION_VERSION_CONFLICT);
        }
        return new LockedDraft(locked, current);
    }

    private PublicationValidation validatePublication(
            InspectionRuleDO rule,
            InspectionRuleRevisionDO revision,
            List<InspectionRuleCommandRevisionDO> commands,
            List<InspectionRuleProductTypeRevisionDO> productTypes) {
        if (!revisionRules.validate(toRevisionDefinition(rule, revision, commands, productTypes)).isEmpty()) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
        String categoryName = requireDictionaryName(CATEGORY_DICT, revision.getCategoryCode());
        String severityName = requireDictionaryName(SEVERITY_DICT, revision.getSeverityCode());
        Map<String, String> productTypeNames = requireProductTypeNames(productTypes);
        return new PublicationValidation(
                categoryName, severityName, productTypeNames,
                digest(commands, revision.getExpectedResultRegex()));
    }

    private String requireDictionaryName(String dictType, String value) {
        String normalized = trim(value);
        try {
            dictDataApi.validateDictDataList(dictType, List.of(normalized));
            List<DictDataRespDTO> values = dictDataApi.getDictDataList(dictType);
            if (values != null) {
                return values.stream()
                        .filter(Objects::nonNull)
                        .filter(item -> Objects.equals(dictType, item.getDictType()))
                        .filter(item -> Objects.equals(normalized, trim(item.getValue())))
                        .map(DictDataRespDTO::getLabel)
                        .map(InspectionRulePublicationTransactionService::trim)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElseThrow(() -> exception(INSPECTION_RULE_DRAFT_INVALID));
            }
        } catch (RuntimeException failure) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
        throw exception(INSPECTION_RULE_DRAFT_INVALID);
    }

    private Map<String, String> requireProductTypeNames(List<InspectionRuleProductTypeRevisionDO> productTypes) {
        List<String> codes = productTypes.stream()
                .map(InspectionRuleProductTypeRevisionDO::getProductTypeCode)
                .toList();
        Map<String, ProductTypeCodeResult> results = new HashMap<>();
        try {
            List<ProductTypeCodeResult> response =
                    assetProductTypeApi.getByCodes(new ProductTypeCodesQuery(codes));
            if (response == null) {
                throw exception(INSPECTION_RULE_DRAFT_INVALID);
            }
            for (ProductTypeCodeResult result : response) {
                if (!hasAuthoritativeSourceProof(result)
                        || results.put(result.productTypeCode(), result) != null) {
                    throw exception(INSPECTION_RULE_DRAFT_INVALID);
                }
            }
        } catch (RuntimeException failure) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
        if (!results.keySet().equals(Set.copyOf(codes))) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
        return results.values().stream().collect(Collectors.toUnmodifiableMap(
                ProductTypeCodeResult::productTypeCode,
                item -> trim(item.displayName())));
    }

    private void applyPublication(
            InspectionRulePublicationLockProjection locked,
            InspectionRuleRevisionDO revision,
            Long actorId,
            LocalDateTime publishedAt,
            String categoryName,
            String severityName,
            Map<String, String> productTypeNames) {
        List<InspectionRuleProductTypeRevisionDO> productTypes =
                loadProductTypes(revision.getTenantId(), revision.getId());
        validateProductTypeNames(productTypes, productTypeNames);
        for (InspectionRuleProductTypeRevisionDO productType : productTypes) {
            if (productTypeMapper.updateNameSnapshot(new InspectionRuleProductTypeNameUpdate(
                    revision.getTenantId(), revision.getId(), productType.getProductTypeCode(),
                    productTypeNames.get(productType.getProductTypeCode()))) != 1) {
                throw exception(INSPECTION_RULE_REVISION_VERSION_CONFLICT);
            }
        }
        if (locked.currentPublishedRevisionId() != null
                && revisionMapper.disablePublishedIfMatch(new InspectionRuleDisableUpdate(
                revision.getTenantId(), locked.currentPublishedRevisionId(),
                locked.currentPublishedRevisionVersion(), actorId, publishedAt)) != 1) {
            throw exception(INSPECTION_RULE_REVISION_VERSION_CONFLICT);
        }
        if (revisionMapper.publishDraftIfMatch(new InspectionRulePublishUpdate(
                revision.getTenantId(), revision.getId(), revision.getVersion(),
                categoryName, severityName, actorId, publishedAt)) != 1) {
            throw exception(INSPECTION_RULE_REVISION_VERSION_CONFLICT);
        }
    }

    private InspectionRuleRevisionDO requireRevision(Long revisionId, Long tenantId) {
        InspectionRuleRevisionDO revision = revisionMapper.selectById(revisionId);
        if (revision == null || !Objects.equals(revision.getTenantId(), tenantId)) {
            throw exception(INSPECTION_RULE_REVISION_NOT_EXISTS);
        }
        return revision;
    }

    private List<InspectionRuleCommandRevisionDO> loadCommands(Long tenantId, Long revisionId) {
        return commandMapper.selectListByRevisionIds(
                new InspectionRuleChildrenQuery(tenantId, Set.of(revisionId), null));
    }

    private List<InspectionRuleProductTypeRevisionDO> loadProductTypes(Long tenantId, Long revisionId) {
        return productTypeMapper.selectListByRevisionIds(
                new InspectionRuleChildrenQuery(tenantId, Set.of(revisionId), null));
    }

    private String digest(List<InspectionRuleCommandRevisionDO> commands, String expectedResultRegex) {
        try {
            return contentDigestService.digest(new InspectionRuleContentDigestService.ReviewContent(
                    commands.stream().map(item -> new InspectionRuleContentDigestService.CommandContent(
                            item.getCommandContent(), intValue(item.getExecutionOrder()),
                            intValue(item.getTimeoutSeconds()), Boolean.TRUE.equals(item.getContinueOnTimeout())))
                            .toList(),
                    expectedResultRegex));
        } catch (IllegalArgumentException failure) {
            throw exception(INSPECTION_RULE_DRAFT_INVALID);
        }
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
                .toList(), revision.getExpectedResultRegex(), threshold,
                productTypes.stream().map(InspectionRuleProductTypeRevisionDO::getProductTypeCode).toList());
    }

    private static boolean hasThreshold(InspectionRuleRevisionDO revision) {
        return revision.getThresholdDataType() != null || revision.getThresholdOperator() != null
                || revision.getThresholdValue() != null || revision.getThresholdUnit() != null;
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

    private static int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    record SecurityReviewCommand(
            Long tenantId,
            Long revisionId,
            Integer expectedVersion,
            String conclusionCode,
            InspectionRuleSecurityReviewPermissionGuard.ReviewAuthorization authorization,
            LocalDateTime reviewedAt) {
    }

    record SecurityReviewResult(
            String reviewReference,
            Long revisionId,
            String contentDigest,
            String conclusionCode,
            LocalDateTime reviewedAt) {
    }

    record ApprovedPublishCommand(
            Long tenantId,
            Long revisionId,
            Integer expectedVersion,
            Long expectedPublishedRevisionId,
            Long actorId,
            LocalDateTime publishedAt) {
    }

    record ApprovedPublishResult(
            Long revisionId,
            Integer version,
            Long disabledRevisionId,
            String contentDigest,
            String reviewReference) {
    }

    @Deprecated(forRemoval = true)
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

    @Deprecated(forRemoval = true)
    record PublishResult(
            Long revisionId,
            Integer version,
            Long disabledRevisionId) {
    }

    private record LockedDraft(
            InspectionRulePublicationLockProjection lock,
            InspectionRuleRevisionDO revision) {
    }

    private record PublicationValidation(
            String categoryName,
            String severityName,
            Map<String, String> productTypeNames,
            String contentDigest) {
    }
}
