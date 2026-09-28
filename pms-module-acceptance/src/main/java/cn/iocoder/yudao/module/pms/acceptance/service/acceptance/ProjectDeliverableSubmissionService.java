package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.BusinessContractException;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenMaterialView;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmitCommand;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmitOutcome;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenSubmissionView;
import cn.iocoder.yudao.module.pms.platform.api.delivery.PlatformDeliveryRequirementApi.TemplateFrozenView;
import cn.iocoder.yudao.module.pms.platform.api.file.FileDocumentSourceProvider;
import cn.iocoder.yudao.module.pms.platform.api.file.FileEvidenceApi;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import cn.iocoder.yudao.module.pms.project.api.runtime.ProjectRuleReevaluationRequested;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultChange;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultInventorySource;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.ProjectBusinessResultEvidenceApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static cn.iocoder.yudao.module.pms.acceptance.service.acceptance.ProjectDeliverableAccess.failure;
import static cn.iocoder.yudao.module.pms.acceptance.service.acceptance.ProjectDeliverableRequirementResolver.accStatus;
import static cn.iocoder.yudao.module.pms.acceptance.service.acceptance.ProjectDeliverableRequirementResolver.allowed;

/**
 * 统一交付件承接主路径（P06R I2）：存储与提交台账在平台 plt_delivery_*，本服务只做编排——
 * 手工提交（UPLOAD/BUSINESS_RESULT）、文档/成果归集（BUSINESS_DOCUMENT/BUSINESS_RESULT）、
 * 权限与数据范围、乐观并发与来源语义守卫。判定与数量下限由平台 refreshStatus 经
 * {@link ProjectDeliverableRequirementResolver} 承接，判定证据回填提交台账。
 */
@Service
@RequiredArgsConstructor
public class ProjectDeliverableSubmissionService {

    private final PlatformDeliveryRequirementApi platform;
    private final ProjectDeliverableRuleApi rules;
    private final ProjectDeliverableAccess access;
    private final FileEvidenceApi fileEvidence;
    private final ProjectBusinessResultEvidenceApi results;
    private final PlatformBusinessEventApi outbox;
    private final ProjectDeliverableOwnerSources ownerSources;
    private final ProjectDeliverableRequirementResolver resolver;

    public record FileSelection(@NotNull @Positive Long referenceId) { }
    public record Submission(@NotNull @Positive Long planVersionId, @NotNull @PositiveOrZero Long expectedVersion,
                             @NotBlank String sourceType, @NotNull @Size(max = 100) List<@Valid FileSelection> files,
                             BusinessResultSource.Query businessResult) { }
    public record Evaluation(boolean satisfied, String reason, String evidence) { }
    public record MaterialLine(Long id, String materialKind, Long referenceId, Long artifactId,
                               Integer versionNo, String sha256, String fileName,
                               String businessObjectType, String businessObjectId) { }
    public record History(Long id, Long planVersionId, String sourceType, List<MaterialLine> materials,
                          String creator, java.time.LocalDateTime submittedAt) { }
    public record Detail(Long id, Long projectId, String code, String name, String status, Long version,
                         Long planVersionId, JsonNode configuration, boolean writable, String automaticSource,
                         List<History> history) { }
    public record Submitted(Long submissionId, Long sourceVersionId, String status, Long version, Evaluation evaluation) { }

    public Detail detail(Long projectId, Long id) {
        var view = require(platform.findById(id), projectId);
        var context = rules.read(projectId, view.deliverableCode());
        access.require(context, false, false);
        var materialIndex = materialIndex(view.id());
        var history = platform.listSubmissions(id).stream()
                .map(submission -> new History(submission.id(), planVersionIdFrom(submission.requestPayloadJson()),
                        submission.sourceType(),
                        submission.materialIds().stream().map(materialIndex::get).filter(Objects::nonNull)
                                .sorted(java.util.Comparator.comparing(MaterialLine::id)).toList(),
                        null, submission.createTime()))
                .toList();
        return new Detail(id, projectId, view.deliverableCode(), view.name(), accStatus(view.status()), asLong(view.version()),
                context.planVersionId(), context.configuration(), access.writable(context),
                ownerSources.ownerType(view), history);
    }

    public List<BusinessResultSource.Descriptor> resultTypes(Long projectId, Long id) {
        detail(projectId, id);
        return results.types();
    }

    public BusinessResultInventorySource.InventoryPage candidates(Long projectId, Long id,
                                                                  BusinessResultSource.Type type, String after) {
        var detail = detail(projectId, id);
        if (!allowed(detail.configuration(), "BUSINESS_RESULT")) throw failure("模板未允许关联业务成果");
        return results.candidates(new BusinessResultInventorySource.InventoryQuery(
                TenantContextHolder.getRequiredTenantId(), projectId, type, false, null, after, 30));
    }

    @Transactional(rollbackFor = Exception.class)
    public Submitted submit(Long projectId, Long id, String requestKey, Submission request) {
        if (request == null || requestKey == null || requestKey.isBlank() || requestKey.length() > 128
                || !Set.of(PlatformDeliveryRequirementApi.SOURCE_UPLOAD,
                        PlatformDeliveryRequirementApi.SOURCE_BUSINESS_RESULT).contains(request.sourceType())
                || request.files() == null) {
            throw failure("交付件提交参数不完整");
        }
        var observed = require(platform.findById(id), projectId);
        var context = rules.lock(projectId, observed.deliverableCode());
        access.require(context, false, true);
        var locked = require(platform.lockById(id), projectId);
        String payload = JsonUtils.toJsonString(request);
        // 幂等重放先于写校验：原回执稳定，即使其后已被更新的提交取代。
        var replay = platform.findSubmissionByRequestKey(id, requestKey);
        if (replay.isPresent()) {
            if (!JsonUtils.parseTree(replay.get().requestPayloadJson() == null ? "" : replay.get().requestPayloadJson())
                    .equals(JsonUtils.parseTree(payload))) {
                throw failure("同一提交标识不能用于不同材料");
            }
            return replayOutcome(replay.get(), locked);
        }
        Long actor = access.require(context, true, true);
        if (!Objects.equals(request.planVersionId(), context.planVersionId())
                || !Objects.equals(request.expectedVersion(), asLong(locked.version()))) {
            throw failure("交付件或项目计划已变化，请刷新后重新提交");
        }
        if (!allowed(context.configuration(), request.sourceType())) throw failure("模板未允许此交付件来源");
        if (ownerSources.ownerType(locked) != null) {
            throw failure("该交付件由来源业务自动关联，请在验收报告或满意度业务中更新材料");
        }
        var current = platform.findCurrentSubmission(id);
        if (current.isPresent()
                && PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION.equals(current.get().sourceType())) {
            throw failure("该交付件已有业务来源，请在来源业务中更新材料");
        }
        List<Long> materialIds;
        TemplateFrozenSubmitOutcome outcome;
        try {
            materialIds = capture(locked, projectId, request);
            if (materialIds.size() < locked.minimumQuantity()) {
                throw failure("有效材料数量不足，至少需要 " + locked.minimumQuantity() + " 项");
            }
            outcome = platform.submitTemplateFrozen(new TemplateFrozenSubmitCommand(id, requestKey, materialIds,
                    request.sourceType(), payload, null));
        } catch (BusinessContractException e) {
            throw failure(e.getMessage());
        }
        Evaluation evaluation;
        if (outcome.replay()) {
            // 并发窗口内同键提交已落台账：返回现行要求状态与已冻结判定证据。
            evaluation = new Evaluation(!PlatformDeliveryRequirementApi.STATUS_OPEN.equals(outcome.requirementStatus()),
                    replayReason(outcome.decisionEvidenceJson()),
                    outcome.decisionEvidenceJson() == null ? "{}" : outcome.decisionEvidenceJson());
        } else {
            var confirmation = rules.evaluate(projectId, observed.deliverableCode());
            String evidenceJson = JsonUtils.toJsonString(Map.of("confirmation", confirmation));
            platform.updateSubmissionDecision(outcome.submissionId(), evidenceJson);
            evaluation = new Evaluation(!PlatformDeliveryRequirementApi.STATUS_OPEN.equals(outcome.requirementStatus()),
                    confirmation.reason(), evidenceJson);
        }
        wakeup(locked, actor, "deliverable-submission:" + outcome.submissionId());
        Long version = platform.lockById(id).map(v -> asLong(v.version())).orElse(asLong(locked.version()));
        return new Submitted(outcome.submissionId(), outcome.submissionId(),
                accStatus(outcome.requirementStatus()), version, evaluation);
    }

    /**
     * 门禁/收敛路径的材料级重验（调用方已持项目图锁）：先由平台做材料锁级收敛（失效材料撤回、状态重算），
     * 再按判定阶梯给原因——无现行提交、数量不足、其余交 {@link ProjectDeliverableRequirementResolver}
     * 判定并把证据冻结回提交台账。满足与否以收敛后要求状态为准。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Evaluation revalidate(TemplateFrozenView view) {
        var convergence = platform.revalidateConvergence(view.id());
        String after = convergence.requirement().status();
        boolean satisfied = !PlatformDeliveryRequirementApi.STATUS_OPEN.equals(after);
        var current = platform.findCurrentSubmission(view.id());
        String reason;
        String evidenceJson = "{}";
        if (current.isEmpty()) {
            reason = "DELIVERABLE_SOURCE_MISSING";
        } else if (current.get().materialIds().size() < convergence.requirement().minimumQuantity()) {
            reason = "DELIVERABLE_QUANTITY_NOT_MET";
        } else {
            var resolution = resolver.evaluate(view.id(), view.projectId(), view.deliverableCode(),
                    current.get().materialIds().size());
            reason = resolution.reason();
            evidenceJson = resolution.evidenceJson();
            platform.updateSubmissionDecision(current.get().id(), evidenceJson);
        }
        return new Evaluation(satisfied, reason, evidenceJson);
    }

    @Transactional(rollbackFor = Exception.class)
    public Evaluation refresh(Long projectId, Long id) {
        var observed = require(platform.findById(id), projectId);
        var context = rules.lock(projectId, observed.deliverableCode());
        Long actor = access.require(context, true, true);
        String before = observed.status();
        var convergence = platform.revalidateConvergence(id);
        String after = convergence.requirement().status();
        var confirmation = rules.evaluate(projectId, observed.deliverableCode());
        String evidenceJson = JsonUtils.toJsonString(Map.of("confirmation", confirmation));
        if (!Objects.equals(before, after)) {
            Long submissionId = platform.findCurrentSubmission(id).map(TemplateFrozenSubmissionView::id).orElse(null);
            appendEvaluated(observed, submissionId, after, confirmation.reason(), evidenceJson,
                    asLong(convergence.requirement().version()));
        }
        wakeup(convergence.requirement(), actor, "deliverable-refresh:" + id + ":" + convergence.requirement().version());
        return new Evaluation(!PlatformDeliveryRequirementApi.STATUS_OPEN.equals(after), confirmation.reason(),
                evidenceJson);
    }

    /** Called only by the committed file-event consumer; never grants the caller a business write permission. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void collectDocument(TemplateFrozenView observed, FileDocumentSourceProvider.Scope scope,
                                FileEvidenceApi.Document file, String eventId) {
        var context = rules.read(observed.projectId(), observed.deliverableCode());
        if (!"ACTIVE".equals(context.lifecycleStatus())
                || !ProjectDocumentSourceRegistry.matches(context.configuration(), scope.sourceCode())) return;
        context = rules.lock(observed.projectId(), observed.deliverableCode());
        if (!"ACTIVE".equals(context.lifecycleStatus()) || !Objects.equals(scope.projectId(), context.projectId())
                || !ProjectDocumentSourceRegistry.matches(context.configuration(), scope.sourceCode())) return;
        var view = require(platform.lockById(observed.id()), observed.projectId());
        String key = "file-event:" + eventId;
        if (platform.findSubmissionByRequestKey(observed.id(), key).isPresent()) return;
        var current = platform.findCurrentSubmission(observed.id());
        // 投影来源保持自身不可变链路；业务成果槽位不被文档归集覆盖。
        if (current.isPresent()
                && (PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION.equals(current.get().sourceType())
                        || PlatformDeliveryRequirementApi.SOURCE_BUSINESS_RESULT.equals(current.get().sourceType()))) {
            return;
        }
        // keep-set：人工上传材料全保留；归集文档材料保留除本次变化引用外的部分。
        var materials = platform.listMaterials(observed.id());
        Set<Long> keep = new LinkedHashSet<>();
        for (var material : materials) {
            if (!PlatformDeliveryRequirementApi.MATERIAL_KIND_FILE.equals(material.materialKind())
                    || !isActiveAndCurrent(material, current)) {
                continue;
            }
            if (material.businessObjectType() == null || material.businessObjectType().isBlank()
                    || !Objects.equals(material.fileReferenceId(), file.referenceId())) {
                keep.add(material.id());
            }
        }
        Long docMaterialId = materials.stream()
                .filter(material -> PlatformDeliveryRequirementApi.MATERIAL_KIND_FILE.equals(material.materialKind())
                        && scope.sourceCode().equals(material.businessObjectType())
                        && Objects.equals(material.fileReferenceId(), file.referenceId())
                        && PlatformDeliveryRequirementApi.MATERIAL_STATUS_ACTIVE.equals(material.status()))
                .findFirst().map(TemplateFrozenMaterialView::id).orElse(null);
        if (docMaterialId == null && file.available()) {
            docMaterialId = platform.registerTemplateFrozenDocument(observed.id(), scope.sourceCode(),
                    file.referenceId(), null);
        }
        if (docMaterialId != null) keep.add(docMaterialId);
        var currentIds = current.<Set<Long>>map(value -> new LinkedHashSet<>(value.materialIds()))
                .orElseGet(LinkedHashSet::new);
        if (keep.equals(currentIds)) return;
        String before = view.status();
        var outcome = platform.submitTemplateFrozen(new TemplateFrozenSubmitCommand(observed.id(), key,
                List.copyOf(keep), PlatformDeliveryRequirementApi.SOURCE_BUSINESS_DOCUMENT,
                JsonUtils.toJsonString(file), null));
        notifyOutcome(view, before, outcome, key);
    }

    /** Called by the committed business-result delivery; automaticSources is the grant, allowedSources still gates revalidation. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void collectBusinessResult(TemplateFrozenView observed, String sourceCode, BusinessResultChange change) {
        var context = rules.read(observed.projectId(), observed.deliverableCode());
        if (!"ACTIVE".equals(context.lifecycleStatus())
                || !ProjectDocumentSourceRegistry.matches(context.configuration(), sourceCode)) return;
        context = rules.lock(observed.projectId(), observed.deliverableCode());
        if (!"ACTIVE".equals(context.lifecycleStatus()) || !Objects.equals(observed.projectId(), context.projectId())
                || !ProjectDocumentSourceRegistry.matches(context.configuration(), sourceCode)) return;
        var view = require(platform.lockById(observed.id()), observed.projectId());
        String key = "result-change:" + change.eventId();
        if (platform.findSubmissionByRequestKey(observed.id(), key).isPresent()) return;
        var current = platform.findCurrentSubmission(observed.id());
        // 投影来源保持自身不可变链路。
        if (current.isPresent()
                && PlatformDeliveryRequirementApi.SOURCE_AUTO_PROJECTION.equals(current.get().sourceType())) {
            return;
        }
        // 形成的成果是交付件唯一现行证据槽；归集整体置换（不与文档/上传合并）。
        var result = change.observation().result();
        if (result == null || result.validity() != BusinessResultSource.Validity.CURRENT) return;
        Long materialId = platform.registerTemplateFrozenBusinessResult(observed.id(),
                ProjectDeliverableBusinessResultEvidenceProvider.BUSINESS_OBJECT_TYPE,
                ProjectDeliverableBusinessResultEvidenceProvider.compositeObjectId(result.type(), result.objectId(),
                        result.resultId(), result.formedAt()),
                revisionNo(result.businessRevision()), null);
        String before = view.status();
        var outcome = platform.submitTemplateFrozen(new TemplateFrozenSubmitCommand(observed.id(), key,
                List.of(materialId), PlatformDeliveryRequirementApi.SOURCE_BUSINESS_RESULT,
                JsonUtils.toJsonString(change), null));
        notifyOutcome(view, before, outcome, key);
    }

    // ———— 内部 ————

    private List<Long> capture(TemplateFrozenView locked, Long projectId, Submission request) {
        if (PlatformDeliveryRequirementApi.SOURCE_BUSINESS_RESULT.equals(request.sourceType())) {
            var query = request.businessResult();
            Long tenantId = TenantContextHolder.getRequiredTenantId();
            if (!request.files().isEmpty() || query == null || !Objects.equals(query.tenantId(), tenantId)
                    || !Objects.equals(query.projectId(), projectId)
                    || query.objectId() == null || query.resultId() == null) {
                throw failure("请选择当前项目的完整业务成果");
            }
            var descriptor = results.types().stream().filter(value -> value.type().equals(query.type())).findFirst()
                    .orElseThrow(() -> failure("不支持的业务成果类型"));
            var observation = results.lockAndInspect(new BusinessResultSource.Query(tenantId, projectId, query.type(),
                    query.objectId(), descriptor.exactLookup() ? query.resultId() : null));
            var result = observation.result();
            if (result == null || result.validity() != BusinessResultSource.Validity.CURRENT
                    || !query.resultId().equals(result.resultId())) {
                throw failure("业务成果尚未形成、已撤销或已被替换");
            }
            Long materialId = platform.registerTemplateFrozenBusinessResult(locked.id(),
                    ProjectDeliverableBusinessResultEvidenceProvider.BUSINESS_OBJECT_TYPE,
                    ProjectDeliverableBusinessResultEvidenceProvider.compositeObjectId(result.type(), result.objectId(),
                            result.resultId(), result.formedAt()),
                    revisionNo(result.businessRevision()), null);
            return List.of(materialId);
        }
        if (request.businessResult() != null || request.files().isEmpty()) throw failure("请上传交付文件");
        var seen = new LinkedHashSet<Long>();
        var captured = new ArrayList<Long>();
        for (var selected : request.files()) {
            if (!seen.add(selected.referenceId())) throw failure("不能重复提交同一文件版本");
            var document = fileEvidence.inspectDocument(TenantContextHolder.getRequiredTenantId(), selected.referenceId());
            if (document == null || !document.available()) {
                throw failure("文件引用不存在或不可用: " + selected.referenceId());
            }
            if (!anchorMatches(document, locked, projectId)) throw failure("文件归属与交付件不一致");
            captured.add(platform.registerTemplateFrozenFile(locked.id(), selected.referenceId(), null,
                    PlatformDeliveryRequirementApi.MATERIAL_SOURCE_UPLOAD));
        }
        return captured;
    }

    /** 上传锚：新统一锚（PLT/DELIVERY_MATERIAL/ACC:project_deliverable:{projectId}，purposeCode=交付件编码）
     * 或迁移保留的旧交付件附件锚（ACC/PROJECT_DELIVERABLE/{要求ID}）。 */
    private static boolean anchorMatches(FileEvidenceApi.Document document, TemplateFrozenView locked, Long projectId) {
        boolean unified = PlatformDeliveryRequirementApi.MATERIAL_FILE_OWNER_CONTEXT.equals(document.ownerContext())
                && PlatformDeliveryRequirementApi.MATERIAL_FILE_OBJECT_TYPE.equals(document.objectType())
                && (PlatformDeliveryRequirementApi.TEMPLATE_OWNER_MODULE + ":"
                        + PlatformDeliveryRequirementApi.TEMPLATE_ENTITY_TYPE + ":" + projectId)
                        .equals(document.objectId())
                && locked.deliverableCode().equals(document.purposeCode());
        boolean legacy = PlatformDeliveryRequirementApi.LEGACY_OWNER_CONTEXT.equals(document.ownerContext())
                && PlatformDeliveryRequirementApi.LEGACY_OBJECT_TYPE.equals(document.objectType())
                && String.valueOf(locked.id()).equals(document.objectId())
                && PlatformDeliveryRequirementApi.LEGACY_PURPOSE_CODE.equals(document.purposeCode());
        return unified || legacy;
    }

    private boolean isActiveAndCurrent(TemplateFrozenMaterialView material,
                                       Optional<TemplateFrozenSubmissionView> current) {
        return PlatformDeliveryRequirementApi.MATERIAL_STATUS_ACTIVE.equals(material.status())
                && (current.isEmpty() || current.get().materialIds().contains(material.id()));
    }

    private Submitted replayOutcome(TemplateFrozenSubmissionView replay, TemplateFrozenView locked) {
        return new Submitted(replay.id(), replay.id(), accStatus(locked.status()), asLong(locked.version()),
                new Evaluation(!PlatformDeliveryRequirementApi.STATUS_OPEN.equals(locked.status()),
                        replayReason(replay.decisionEvidenceJson()),
                        replay.decisionEvidenceJson() == null ? "{}" : replay.decisionEvidenceJson()));
    }

    private static String replayReason(String decisionJson) {
        var tree = JsonUtils.parseTree(decisionJson == null || decisionJson.isBlank() ? "{}" : decisionJson);
        String reason = tree.path("confirmation").path("reason").asText(null);
        return reason != null ? reason : tree.path("reason").asText("");
    }

    private static Long asLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    private static Long revisionNo(String businessRevision) {
        return businessRevision == null || businessRevision.isBlank() ? null
                : BusinessResultSource.nativeId(businessRevision);
    }

    private Map<Long, MaterialLine> materialIndex(Long requirementId) {
        var index = new java.util.LinkedHashMap<Long, MaterialLine>();
        for (var material : platform.listMaterials(requirementId)) {
            index.put(material.id(), new MaterialLine(material.id(), material.materialKind(),
                    material.fileReferenceId(), material.fileArtifactId(), material.fileVersionNo(),
                    material.fileSha256(), material.fileName(), material.businessObjectType(),
                    material.businessObjectId()));
        }
        return index;
    }

    private static Long planVersionIdFrom(String payloadJson) {
        try {
            long value = JsonUtils.parseTree(payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson)
                    .path("planVersionId").asLong(0);
            return value > 0 ? value : null;
        } catch (Exception invalid) {
            return null;
        }
    }

    private TemplateFrozenView require(Optional<TemplateFrozenView> view, Long projectId) {
        var value = view.orElse(null);
        if (value == null || !Objects.equals(value.projectId(), projectId)) throw failure("项目交付件不存在");
        return value;
    }

    /** 归集路径的状态收敛留痕：要求状态变化时补记评估事件，并唤醒项目规则重评。 */
    private void notifyOutcome(TemplateFrozenView view, String before, TemplateFrozenSubmitOutcome outcome,
                               String correlation) {
        String after = outcome.requirementStatus();
        if (!Objects.equals(before, after)) {
            var confirmation = rules.evaluate(view.projectId(), view.deliverableCode());
            String evidenceJson = JsonUtils.toJsonString(Map.of("confirmation", confirmation));
            Long version = platform.lockById(view.id()).map(v -> asLong(v.version())).orElse(null);
            appendEvaluated(view, outcome.submissionId(), after, confirmation.reason(), evidenceJson, version);
        }
        wakeup(view, null, correlation);
    }

    private void appendEvaluated(TemplateFrozenView view, Long submissionId, String status, String reason,
                                 String evidence, Long version) {
        String eventId = "deliverable-evaluated:" + view.id() + ":" + version;
        outbox.append("ProjectDeliverable", view.id().toString(), new BusinessEvent(eventId,
                "ProjectDeliverableEvaluated.v1", JsonUtils.toJsonString(Map.of(
                "eventId", eventId, "deliverableId", view.id(),
                "sourceVersionId", Objects.toString(submissionId, ""), "projectId", view.projectId(),
                "status", status, "reason", reason, "evidence", evidence, "version", version))));
    }

    private void wakeup(TemplateFrozenView view, Long actor, String correlation) {
        var event = ProjectRuleReevaluationRequested.create(TenantContextHolder.getRequiredTenantId(),
                view.projectId(), actor, correlation);
        outbox.append("Project", view.projectId().toString(), new BusinessEvent(event.eventId(),
                ProjectRuleReevaluationRequested.EVENT_TYPE, JsonUtils.toJsonString(event)));
    }
}
