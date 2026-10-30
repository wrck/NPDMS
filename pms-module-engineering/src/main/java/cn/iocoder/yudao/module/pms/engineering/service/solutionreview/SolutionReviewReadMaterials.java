package cn.iocoder.yudao.module.pms.engineering.service.solutionreview;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.SolutionSourceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.RequirementRevisionQuery;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryMaterialApi;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import java.util.*;
import java.util.regex.Pattern;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;

/** Reads only documents explicitly bound to the reviewed version and its frozen requirement source. */
@Service @RequiredArgsConstructor
public class SolutionReviewReadMaterials implements OwnerScopedFileReadPolicy {
    private final SolutionTieredReviewService reviews;
    private final SolutionSourceMapper sources;
    private final RequirementAnalysisMapper requirements;
    private final PlatformDeliveryMaterialApi materials;
    private final FileEvidenceApi evidence;
    private final org.springframework.beans.factory.ObjectProvider<OwnerScopedFileContentApi> contents;
    public record Attachment(Long referenceId, String name) { }
    public record Materials(Map<String, Object> requirement, List<Attachment> attachments) { }
    @Override public String ownerCode() { return "SOL_REVIEW"; }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Materials read(String businessKey) {
        var solution = reviews.sourceForReview(businessKey);
        JsonNode source = requirement(solution);
        var references = references(solution, source);
        Map<String, Object> requirement = new LinkedHashMap<>();
        for (String field : List.of("projectBackground", "projectObjective", "networkTopology", "businessDeviceDetails")) {
            var value = source.path("revision").get(field);
            if (value != null && !value.isNull()) requirement.put(field, JsonUtils.parseObject(value.toString(), Object.class));
        }
        return new Materials(requirement, references.values().stream()
                .filter(FileEvidenceApi.Document::available).map(document -> new Attachment(document.referenceId(), document.name())).toList());
    }
    public OwnerScopedFileContentApi.Content content(OwnerScopedFileContentApi.Query query) {
        if (query == null || !ownerCode().equals(query.ownerCode())) throw denied();
        return contents.getObject().read(query);
    }
    @Override public void require(OwnerScopedFileReadPolicy.Query query) {
        if (!Objects.equals(query.tenantId(), TenantContextHolder.getRequiredTenantId())
                || !Objects.equals(query.actorId(), SecurityFrameworkUtils.getLoginUserId())) throw denied();
        var solution = reviews.sourceForReview(query.businessKey());
        var expected = references(solution, requirement(solution)).get(query.document().referenceId());
        if (expected == null || !expected.available() || !expected.equals(query.document())) throw denied();
    }
    private JsonNode requirement(SolutionDO solution) {
        Long revision = json(solution.getSectionData()).path("requirementInput").path("revisionId").asLong();
        if (revision <= 0) return json(null);
        var source = sources.selectBySolutionAndSource(solution.getId(), "REQUIREMENT", revision);
        if (source == null || !Objects.equals(source.getTenantId(), solution.getTenantId())) throw denied();
        // projectId is @JsonIgnore on the serialized revision; validate ownership against its immutable Owner row.
        var owner = requirements.selectRevision(new RequirementRevisionQuery(solution.getTenantId(), revision));
        if (owner == null || !Objects.equals(owner.getId(), revision)
                || !Objects.equals(owner.getTenantId(), solution.getTenantId())
                || !Objects.equals(owner.getProjectId(), solution.getProjectId())) throw denied();
        var snapshot = json(source.getSourceSnapshot());
        if (snapshot.path("revision").path("id").asLong() != revision
                || snapshot.path("revision").hasNonNull("projectId")
                    && snapshot.path("revision").path("projectId").asLong() != solution.getProjectId()) throw denied();
        return snapshot;
    }
    private Map<Long, FileEvidenceApi.Document> references(SolutionDO solution, JsonNode source) {
        Map<Long, FileEvidenceApi.Document> result = new LinkedHashMap<>();
        Long tenant = TenantContextHolder.getRequiredTenantId();
        for (var reference : json(solution.getScript()).path("references")) {
            var document = evidence.inspectDocument(tenant, reference.path("referenceId").asLong());
            if (document != null && "PLT".equals(document.ownerContext()) && "DELIVERY_MATERIAL".equals(document.objectType())
                    && ("SOL:solution:" + solution.getId()).equals(document.objectId()) && "IMPLEMENTATION_PLAN".equals(document.purposeCode())
                    && matches(document, reference)) result.put(document.referenceId(), document);
        }
        Set<Long> customerMaterials = new HashSet<>();
        String urls = customerRemark(solution.getRemark()).path("customerPlanUrl").asText("");
        var pattern = Pattern.compile("^/api/v1/pms/solutions/" + solution.getId() + "/customer-files/([0-9]+)(/[^?#]+)?$");
        for (String url : urls.split(",")) { var match = pattern.matcher(url); if (match.matches()) customerMaterials.add(Long.valueOf(match.group(1))); }
        if (!customerMaterials.isEmpty()) for (var material : materials.listByEntityAndType("SOL", "solution", solution.getId(), "IMPLEMENTATION_PLAN")) {
            if (!customerMaterials.contains(material.id()) || !"ACTIVE".equals(material.status()) || !"FILE".equals(material.materialKind())) continue;
            var referenceId = materials.fileReferenceId(new PlatformDeliveryMaterialApi.FileReferenceQuery(material.id(), "SOL", "solution", solution.getId()));
            if (referenceId == null) continue;
            var document = evidence.inspectDocument(tenant, referenceId);
            if (document != null && "PLT".equals(document.ownerContext()) && "DELIVERY_MATERIAL".equals(document.objectType())
                    && ("SOL:solution:" + solution.getId()).equals(document.objectId()) && "IMPLEMENTATION_PLAN".equals(document.purposeCode())
                    && Objects.equals(document.artifactId(), material.fileArtifactId()) && Objects.equals(document.versionNo(), material.fileVersionNo())
                    && Objects.equals(document.sha256(), material.fileSha256())) result.put(document.referenceId(), document);
        }
        String revision = source.path("revision").path("id").asText();
        for (var set : source.path("topologyFiles")) {
            var key = set.path("key");
            if (!"SOL".equals(key.path("ownerContext").asText()) || !"REQUIREMENT_ANALYSIS_REVISION".equals(key.path("objectType").asText())
                    || !revision.equals(key.path("objectId").asText()) || !"FORM_FIELD_ATTACHMENT/NETWORK_TOPOLOGY__ATTACHMENTS".equals(key.path("purposeCode").asText())) continue;
            for (var file : set.path("activeFacts")) {
                var document = evidence.inspectDocument(new FileEvidenceApi.ReferenceQuery(tenant, "SOL", "REQUIREMENT_ANALYSIS_REVISION",
                        revision, key.path("purposeCode").asText(), file.path("referenceKey").asText()));
                if (document != null && "SOL".equals(document.ownerContext()) && "REQUIREMENT_ANALYSIS_REVISION".equals(document.objectType())
                        && revision.equals(document.objectId()) && key.path("purposeCode").asText().equals(document.purposeCode())
                        && matches(document, file)) result.put(document.referenceId(), document);
            }
        }
        return result;
    }
    private static boolean matches(FileEvidenceApi.Document document, JsonNode file) {
        return Objects.equals(document.artifactId(), file.path("artifactId").asLong())
                && Objects.equals(document.versionNo(), file.path("versionNo").asInt())
                && Objects.equals(document.referenceKey(), file.path("referenceKey").asText())
                && Objects.equals(document.sha256(), file.path("sha256").asText());
    }
    private static JsonNode customerRemark(String value) {
        // A copied version keeps a plain provenance note; only an object envelope can name customer files.
        return json(value != null && value.stripLeading().startsWith("{") ? value : null);
    }
    private static JsonNode json(String value) { return JsonUtils.parseTree(value == null || value.isBlank() ? "{}" : value); }
    private static RuntimeException denied() { return exception(FORBIDDEN); }
}
