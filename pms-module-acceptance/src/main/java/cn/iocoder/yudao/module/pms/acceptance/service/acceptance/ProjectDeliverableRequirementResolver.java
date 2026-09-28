package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryRequirementRuleResolver;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Objects;

/**
 * 统一交付要求的冻结规则判定（P06R I2）：平台经 {@link DeliveryRequirementRuleResolver} SPI 取判定，
 * 数量下限由要求行 minimum_quantity 承接，本判定只负责来源语义与项目确认规则。
 * 判定语义对齐旧 revalidate：来源白名单与文档范围按活跃计划配置（rules.lock）判定，
 * frozen_config_json 仅作冻结快照留档、不参与判定；判定结果（含原因/证据）由调用方冻结到提交台账。
 */
@Service
@RequiredArgsConstructor
public class ProjectDeliverableRequirementResolver implements DeliveryRequirementRuleResolver {

    private final PlatformDeliveryRequirementApi platform;
    private final ProjectDeliverableRuleApi rules;
    private final ProjectDeliverableOwnerSources ownerSources;
    private final ProjectDocumentSourceRegistry documentSources;
    private final FileEvidenceApi fileEvidence;

    @Override
    public Resolution evaluate(Long requirementId, Long projectId, String requirementCode, int materialCount) {
        if (projectId == null) {
            return new Resolution(false, "DELIVERABLE_RULE_CONTEXT_MISSING", "{}");
        }
        var configuration = rules.lock(projectId, requirementCode).configuration();
        var current = platform.findCurrentSubmission(requirementId);
        if (current.isEmpty()) {
            return new Resolution(false, "DELIVERABLE_SOURCE_MISSING", "{}");
        }
        var submission = current.get();
        if (PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION.equals(submission.sourceType())) {
            return evaluateProjection(requirementId, projectId, requirementCode, submission, materialCount);
        }
        if (!PlatformDeliveryRequirementApi.SOURCE_BUSINESS_DOCUMENT.equals(submission.sourceType())
                && !allowed(configuration, submission.sourceType())) {
            return new Resolution(false, "DELIVERABLE_SOURCE_NOT_ALLOWED", "{}");
        }
        // 归集文档范围重验：文件仍解析到配置允许的来源，才算有效证据。
        for (var material : platform.listMaterials(requirementId)) {
            if (!submission.materialIds().contains(material.id())) {
                continue;
            }
            if (!isCollectedDocument(material.materialKind(), material.businessObjectType())) {
                continue;
            }
            var document = fileEvidence.inspectDocument(
                    TenantContextHolder.getRequiredTenantId(), material.fileReferenceId());
            var scope = document == null ? null : documentSources.resolve(
                    TenantContextHolder.getRequiredTenantId(), document);
            if (scope == null || !Objects.equals(scope.projectId(), projectId)
                    || !ProjectDocumentSourceRegistry.matches(configuration, scope.sourceCode())) {
                return new Resolution(false, "DELIVERABLE_DOCUMENT_SCOPE_CHANGED", "{}");
            }
        }
        var confirmation = rules.evaluate(projectId, requirementCode);
        return new Resolution(confirmation.satisfied(), confirmation.reason(),
                JsonUtils.toJsonString(Map.of("confirmation", confirmation)));
    }

    private Resolution evaluateProjection(Long requirementId, Long projectId, String requirementCode,
                                          PlatformDeliveryRequirementApi.TemplateFrozenSubmissionView submission,
                                          int materialCount) {
        var view = platform.findById(requirementId);
        if (view.isEmpty()) {
            return new Resolution(false, "DELIVERABLE_SOURCE_MISSING", "{}");
        }
        var evidence = ownerSources.revalidate(view.get(), submission);
        if (!evidence.valid()) {
            return new Resolution(false, evidence.reason(), JsonUtils.toJsonString(evidence));
        }
        var confirmation = rules.evaluate(projectId, requirementCode);
        return new Resolution(confirmation.satisfied(), confirmation.reason(), JsonUtils.toJsonString(Map.of(
                "confirmation", confirmation,
                "source", Map.of("projectionKind", projectionKind(submission.requestPayloadJson()),
                        "fileCount", materialCount))));
    }

    /** 规则上下文来源白名单（对齐旧 allowedSources 语义）。 */
    static boolean allowed(JsonNode configuration, String source) {
        for (var value : configuration.path("allowedSources")) {
            if (value.isTextual() && source.equals(value.asText())) return true;
        }
        return false;
    }

    /** 平台要求状态 → ACC 兼容状态（PENDING/ACCEPTED/CONFIRMED）。 */
    static String accStatus(String platformStatus) {
        return switch (platformStatus == null ? "" : platformStatus) {
            case "SATISFIED" -> "ACCEPTED";
            case "CONFIRMED" -> "CONFIRMED";
            default -> "PENDING";
        };
    }

    static boolean isCollectedDocument(String materialKind, String businessObjectType) {
        return PlatformDeliveryRequirementApi.MATERIAL_KIND_FILE.equals(materialKind)
                && businessObjectType != null && !businessObjectType.isBlank();
    }

    private static String projectionKind(String requestPayloadJson) {
        try {
            return JsonUtils.parseTree(requestPayloadJson == null ? "{}" : requestPayloadJson)
                    .path("projectionKind").asText("");
        } catch (Exception e) {
            return "";
        }
    }
}
