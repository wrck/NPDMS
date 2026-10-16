package cn.iocoder.yudao.module.pms.engineering.service.solutionreview;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.solution.SolutionSourceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.solution.SolutionSourceMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.RequirementAnalysisMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement.query.*;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.engineering.service.solution.SolutionCustomerDocumentService;
import tools.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

/** SCH-01 submission boundary. Drafts and previously frozen approvals are not rewritten. */
@Service
@RequiredArgsConstructor
public class SolutionSubmissionValidator {
    private final RequirementAnalysisMapper requirements;
    private final SolutionSourceMapper sources;
    private final FileEvidenceApi files;
    private final SolutionCustomerDocumentService customerDocuments;

    public void validateAndFreezeSources(SolutionDO solution) {
        if (!"IMPLEMENTATION".equals(solution.getSolutionType())) return;
        // The existing complete-customer-document branch has no online chapters.
        // Only a real, owner-validated native document can select that branch.
        if (SolutionCustomerDocumentService.hasNativePointer(solution.getRemark())
                && "yes".equals(json(solution.getRemark()).path("hasCustomerPlan").asText())) {
            customerDocuments.validatePointer(solution.getId(), solution.getRemark());
            return;
        }
        var missing = new ArrayList<String>();
        if (!content(solution.getBackground())) missing.add("项目背景");
        if (!content(solution.getTarget())) missing.add("项目目标");
        var topology = json(solution.getTopology());
        if (!content(topology.path("asIsUrl").asText())
                || "yes".equals(topology.path("toBeChanged").asText()) && !content(topology.path("toBeUrl").asText())) missing.add("网络拓扑");
        boolean deployed = false;
        for (var row : topology.path("deploy")) {
            if (content(row.path("deviceName").asText()) && content(row.path("location").asText())) deployed = true;
        }
        if (!deployed) missing.add("部署方案（设备及部署位置）");
        var references = json(solution.getScript()).path("references");
        if (!references.isArray() || references.isEmpty()) missing.add("配置脚本文件");
        if (!missing.isEmpty()) throw new IllegalArgumentException("实施方案缺少必填项：" + String.join("、", missing));

        Long tenant = TenantContextHolder.getRequiredTenantId();
        for (var reference : references) {
            var document = files.inspectDocument(tenant, reference.path("referenceId").asLong());
            if (document == null || !document.available() || !"PLT".equals(document.ownerContext())
                    || !"DELIVERY_MATERIAL".equals(document.objectType())
                    || !("SOL:solution:" + solution.getId()).equals(document.objectId())
                    || !"IMPLEMENTATION_PLAN".equals(document.purposeCode())
                    || !Set.of("network", "businessModule", "ha", "full").contains(reference.path("section").asText())
                    || !Objects.equals(document.referenceKey(), reference.path("referenceKey").asText())
                    || !document.referenceKey().startsWith("solution-script-" + reference.path("section").asText() + "-")
                    || !Objects.equals(String.valueOf(document.artifactId()), reference.path("artifactId").asText())
                    || !Objects.equals(document.versionNo(), reference.path("versionNo").asInt())
                    || !Objects.equals(document.sha256(), reference.path("sha256").asText()))
                throw new IllegalArgumentException("配置脚本文件不存在、已失效或不属于当前方案版本，请重新上传");
            var fact = files.lockAndRevalidate(new FileEvidenceApi.Query(tenant, document.artifactId(), document.versionNo(),
                    document.ownerContext(), document.objectType(), document.objectId(), document.purposeCode(), document.referenceKey(), document.sha256()));
            if (fact == null || !fact.valid()) throw new IllegalArgumentException("配置脚本文件已变化，请重新上传后提交");
        }

        var input = json(solution.getSectionData()).path("requirementInput");
        Long revisionId = input.path("revisionId").asLong();
        var revision = requirements.selectRevision(new RequirementRevisionQuery(tenant, revisionId));
        if (revision == null || !Objects.equals(revision.getProjectId(), solution.getProjectId()))
            throw new IllegalArgumentException("请引用本项目已完成的需求分析版本");
        // Match the requirement writer's current-entity -> revision lock order.
        var current = requirements.lockCurrent(new RequirementEntityQuery(tenant, revision.getEntityId()));
        revision = requirements.lockRevision(new RequirementRevisionQuery(tenant, revisionId));
        // A locking read sees the latest committed effective revision, even after the initial snapshot read.
        var effective = requirements.lockEffective(new RequirementProjectQuery(tenant, solution.getProjectId()));
        if (current == null || revision == null || !"FROZEN".equals(revision.getRevisionState()) || effective == null
                || !"FROZEN".equals(effective.getRevisionState()))
            throw new IllegalArgumentException("PRE-04 尚未形成有效的已完成需求分析版本");
        if (!Objects.equals(revisionId, effective.getId())
                && (!String.valueOf(effective.getId()).equals(input.path("acknowledgedRevisionId").asText())
                || !content(input.path("differenceReason").asText())))
            throw new IllegalArgumentException("需求输入已变化，请刷新引用或保留现值并说明差异");
        var source = new SolutionSourceDO();
        source.setSolutionId(solution.getId()); source.setSourceType("REQUIREMENT");
        source.setSourceId(revisionId); source.setSourceCode("PRE-04:r" + revision.getRevisionNo());
        source.setSourceSnapshot(JsonUtils.toJsonString(Map.of("revision", revision, "effectiveRevisionId", effective.getId(),
                "referencedAt", LocalDateTime.now(), "differenceReason", input.path("differenceReason").asText())));
        // A running review is handled idempotently before this boundary; never replace an existing source snapshot.
        if (sources.selectBySolutionAndSource(solution.getId(), "REQUIREMENT", revisionId) == null) sources.insert(source);
    }

    private static JsonNode json(String value) {
        try { return JsonUtils.parseTree(value == null || value.isBlank() ? "{}" : value); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("方案章节数据格式无效", invalid); }
    }
    private static boolean content(String value) {
        return value != null && !value.replaceAll("<[^>]*>", "").replace("&nbsp;", " ").replace("\u00a0", " ").isBlank();
    }
}
