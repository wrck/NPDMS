package cn.iocoder.yudao.module.pms.acceptance.service.acceptance;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptance.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptance.query.ProjectDeliverableIdLockQuery;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.*;
import cn.iocoder.yudao.module.pms.acceptance.dal.mysql.acceptancereport.query.DeliverableCurrentSourceLockQuery;
import cn.iocoder.yudao.module.pms.platform.api.delivery.DeliveryMaterialSource;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.platform.api.outbox.PlatformBusinessEventApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.BusinessEvent;
import cn.iocoder.yudao.module.pms.project.api.deliverable.ProjectDeliverableRuleApi;
import cn.iocoder.yudao.module.pms.project.api.runtime.ProjectRuleReevaluationRequested;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import java.util.*;

import static cn.iocoder.yudao.module.pms.acceptance.service.acceptance.ProjectDeliverableAccess.failure;
import static cn.iocoder.yudao.module.pms.acceptance.service.acceptance.ProjectDeliverableFilePolicyProvider.*;

@Service @RequiredArgsConstructor
public class ProjectDeliverableSubmissionService {
    private final AccProjectDeliverableMapper deliverables;
    private final ProjectDeliverableSubmissionMapper submissions;
    private final ProjectDeliverableSourceVersionMapper sources;
    private final ProjectDeliverableSourceAttachmentMapper attachments;
    private final ProjectDeliverableRuleApi rules;
    private final ProjectDeliverableAccess access;
    private final FileArtifactApi files;
    private final FileEvidenceApi fileEvidence;
    private final ProjectBusinessResultEvidenceApi results;
    private final PlatformBusinessEventApi outbox;
    private final ProjectDeliverableOwnerSources ownerSources;
    private final ProjectDocumentSourceRegistry documentSources;

    public record FileSelection(@NotNull @Positive Long artifactId, @NotNull @Positive Integer versionNo,
                                @NotBlank @Size(max = 64) String referenceKey) { }
    public record Submission(@NotNull @Positive Long planVersionId, @NotNull @PositiveOrZero Long expectedVersion,
                             @NotBlank String sourceType, @NotNull @Size(max = 100) List<@Valid FileSelection> files,
                             BusinessResultSource.Query businessResult) { }
    public record SourceEvidence(List<FileArtifactVersionFact> files, BusinessResultSource.Result businessResult,
                                 List<FileEvidenceApi.Document> businessFiles) {
        public SourceEvidence(List<FileArtifactVersionFact> files, BusinessResultSource.Result businessResult) { this(files, businessResult, List.of()); }
        public SourceEvidence {
            files = files == null ? List.of() : List.copyOf(files);
            businessFiles = businessFiles == null ? List.of() : List.copyOf(businessFiles);
        }
    }
    public record Evaluation(boolean satisfied, String reason, String evidence) { }
    public record History(Long id, Long sourceVersionId, Long planVersionId, String sourceType,
                          SourceEvidence source, String creator, java.time.LocalDateTime submittedAt) { }
    public record Detail(Long id, Long projectId, String code, String name, String status, Long version,
                         Long planVersionId, JsonNode configuration, boolean writable, String automaticSource, List<History> history) { }
    public record Submitted(Long submissionId, Long sourceVersionId, String status, Long version, Evaluation evaluation) { }

    public Detail detail(Long projectId, Long id) {
        var row = require(deliverables.selectById(id), projectId);
        var context = rules.read(projectId, row.getDeliverableCode());
        access.require(context, false, false);
        var history = submissions.selectHistory(new ProjectDeliverableSubmissionMapper.HistoryQuery(
                row.getTenantId(), projectId, id)).stream().map(value -> new History(value.getId(), value.getSourceVersionId(),
                value.getPlanVersionId(), value.getSourceType(), JsonUtils.parseObject(value.getSourceEvidence(), SourceEvidence.class),
                value.getCreator(), value.getCreateTime())).toList();
        return new Detail(id, projectId, row.getDeliverableCode(), row.getName(), row.getStatus(), row.getVersion(),
                context.planVersionId(), context.configuration(), access.writable(context),
                ownerSources.ownerType(row), history);
    }

    public List<BusinessResultSource.Descriptor> resultTypes(Long projectId, Long id) {
        detail(projectId, id);
        return results.types();
    }

    public BusinessResultInventorySource.InventoryPage candidates(Long projectId, Long id, BusinessResultSource.Type type, String after) {
        var detail = detail(projectId, id);
        if (!allowed(detail.configuration(), "BUSINESS_RESULT")) throw failure("模板未允许关联业务成果");
        return results.candidates(new BusinessResultInventorySource.InventoryQuery(TenantContextHolder.getRequiredTenantId(),
                projectId, type, false, null, after, 30));
    }

    @Transactional(rollbackFor = Exception.class)
    public Submitted submit(Long projectId, Long id, String requestKey, Submission request) {
        if (request == null || requestKey == null || requestKey.isBlank() || requestKey.length() > 128
                || !Set.of(DeliveryMaterialSource.UPLOAD.code(), DeliveryMaterialSource.BUSINESS_RESULT.code())
                        .contains(request.sourceType()) || request.files() == null)
            throw failure("交付件提交参数不完整");
        var observed = require(deliverables.selectById(id), projectId);
        var context = rules.lock(projectId, observed.getDeliverableCode());
        access.require(context, false, true);
        var row = require(deliverables.selectByIdForUpdate(new ProjectDeliverableIdLockQuery(observed.getTenantId(), id)), projectId);
        String payload = JsonUtils.toJsonString(request);
        var replay = submissions.selectRequest(row.getTenantId(), id, requestKey);
        if (replay != null) {
            if (!JsonUtils.parseTree(replay.getRequestPayload()).equals(JsonUtils.parseTree(payload))) throw failure("同一提交标识不能用于不同材料");
            var decision = JsonUtils.parseObject(replay.getDecisionEvidence(), Evaluation.class);
            // Original receipt is stable even when a later submission has superseded it.
            return new Submitted(replay.getId(), replay.getSourceVersionId(), decision.satisfied() ? "ACCEPTED" : "PENDING",
                    request.expectedVersion() + 1, decision);
        }
        Long actor = access.require(context, true, true);
        if (!Objects.equals(request.planVersionId(), context.planVersionId()) || !Objects.equals(request.expectedVersion(), row.getVersion())
                || !Objects.equals(observed.getDeliverableCode(), row.getDeliverableCode())) throw failure("交付件或项目计划已变化，请刷新后重新提交");
        if (!allowed(context.configuration(), request.sourceType())) throw failure("模板未允许此交付件来源");
        if (ownerSources.ownerType(row) != null) throw failure("该交付件由来源业务自动关联，请在验收报告或满意度业务中更新材料");
        var previous = sources.selectCurrentForUpdate(new DeliverableCurrentSourceLockQuery(row.getTenantId(), id));
        if (previous != null && !"ProjectDeliverableSubmission".equals(previous.getSourceObjectType()))
            throw failure("该交付件已有业务来源，请在来源业务中更新材料");
        var evidence = capture(row, request);
        int count = evidence.businessResult() == null ? evidence.files().size() : 1;
        int minimum = Math.max(Boolean.TRUE.equals(row.getRequired()) ? 1 : 0, minimum(context.configuration()));
        if (count < minimum) throw failure("有效材料数量不足，至少需要 " + minimum + " 项");
        Long submissionId = IdWorker.getId(), sourceId = IdWorker.getId();
        if (previous != null) {
            previous.setRelationStatus("SUPERSEDED"); previous.setUpdater(actor.toString());
            if (sources.updateById(previous) != 1) throw failure("交付件来源版本已变化");
        }
        var source = new ProjectDeliverableSourceVersionDO();
        source.setId(sourceId); source.setTenantId(row.getTenantId()); source.setDeliverableId(id);
        source.setSourceRequirementId("PM-03"); source.setSourceObjectType("ProjectDeliverableSubmission");
        source.setSourceObjectId(submissionId); source.setSourceVersion(1); source.setRelationStatus("CURRENT");
        source.setArchiveStatus("NOT_REQUIRED"); source.setArchiveRetryCount(0);
        source.setCreator(actor.toString()); source.setUpdater(actor.toString());
        if (sources.insert(source) != 1) throw failure("交付件来源保存失败");
        int sequence = 0;
        for (var fact : evidence.files()) {
            var file = new ProjectDeliverableSourceAttachmentDO();
            file.setId(IdWorker.getId()); file.setTenantId(row.getTenantId()); file.setDeliverableSourceVersionId(sourceId);
            file.setAttachmentSequence(++sequence); file.setFileArtifactId(fact.artifactId()); file.setFileVersionNo(fact.versionNo());
            file.setReferenceKey(fact.referenceKey()); file.setFileHash(fact.sha256()); file.setScopeVersion(fact.scopeVersion());
            file.setArtifactVersion(fact.fileFactVersion().artifactVersion()); file.setReferenceVersion(fact.fileFactVersion().referenceVersion());
            file.setAvailabilityVersion(fact.fileFactVersion().availabilityVersion()); file.setCreator(actor.toString()); file.setUpdater(actor.toString());
            if (attachments.insert(file) != 1) throw failure("交付件附件保存失败");
        }
        var confirmation = rules.evaluate(projectId, row.getDeliverableCode());
        var decision = new Evaluation(confirmation.satisfied(), confirmation.reason(), confirmation.evidence());
        row.setCurrentSourceVersionId(sourceId); row.setArchiveStatus("NOT_REQUIRED");
        // 乐观锁拦截器基于 @Version 在 UPDATE 时自增并回填，此处手动递增会使 WHERE version 落空
        row.setStatus(decision.satisfied() ? "ACCEPTED" : "PENDING"); row.setUpdater(actor.toString());
        if (deliverables.updateById(row) != 1) throw failure("交付件提交冲突");
        var submission = new ProjectDeliverableSubmissionDO();
        submission.setId(submissionId); submission.setTenantId(row.getTenantId()); submission.setProjectId(projectId);
        submission.setDeliverableId(id); submission.setPlanVersionId(context.planVersionId()); submission.setSourceVersionId(sourceId);
        submission.setRequestKey(requestKey); submission.setRequestPayload(payload);
        submission.setConfigurationSnapshot(JsonUtils.toJsonString(context.configuration())); submission.setSourceType(request.sourceType());
        submission.setSourceEvidence(JsonUtils.toJsonString(evidence)); submission.setDecisionEvidence(JsonUtils.toJsonString(decision));
        submission.setCreator(actor.toString());
        if (submissions.insert(submission) != 1) throw failure("交付件提交记录保存失败");
        wakeup(row, actor, "deliverable-submission:" + submissionId);
        return new Submitted(submissionId, sourceId, row.getStatus(), row.getVersion(), decision);
    }

    /** Used inside a stage/gate transaction; material validity is never inferred from cached status. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Evaluation revalidate(AccProjectDeliverableDO row) {
        var context = rules.lock(row.getProjectId(), row.getDeliverableCode());
        var source = sources.selectCurrentForUpdate(new DeliverableCurrentSourceLockQuery(row.getTenantId(), row.getId()));
        if (source == null || !Objects.equals(source.getId(), row.getCurrentSourceVersionId()))
            return outcome(row, false, "DELIVERABLE_SOURCE_MISSING", "{}");
        if (!"ProjectDeliverableSubmission".equals(source.getSourceObjectType())) {
            var evidence = ownerSources.revalidate(row, source);
            if (!evidence.valid()) return outcome(row, false, evidence.reason(), JsonUtils.toJsonString(evidence));
            int count = allowed(context.configuration(), "UPLOAD") ? evidence.fileCount()
                    : allowed(context.configuration(), "BUSINESS_RESULT") ? 1 : 0;
            if (count < Math.max(Boolean.TRUE.equals(row.getRequired()) ? 1 : 0, minimum(context.configuration())))
                return outcome(row, false, "DELIVERABLE_QUANTITY_NOT_MET", JsonUtils.toJsonString(evidence));
            var decision = rules.evaluate(row.getProjectId(), row.getDeliverableCode());
            return outcome(row, decision.satisfied(), decision.reason(), JsonUtils.toJsonString(Map.of("confirmation", decision, "source", evidence)));
        }
        var submission = submissions.selectSource(row.getTenantId(), source.getId());
        if (submission == null || !Objects.equals(submission.getDeliverableId(), row.getId())
                || !Objects.equals(submission.getProjectId(), row.getProjectId()))
            return outcome(row, false, "DELIVERABLE_SUBMISSION_UNAVAILABLE", "{}");
        if (!"BUSINESS_DOCUMENT".equals(submission.getSourceType()) && !allowed(context.configuration(), submission.getSourceType()))
            return outcome(row, false, "DELIVERABLE_SOURCE_NOT_ALLOWED", "{}");
        var evidence = JsonUtils.parseObject(submission.getSourceEvidence(), SourceEvidence.class);
        int count = 0;
        var fileFacts = new ArrayList<FileEvidenceApi.Fact>();
        if (!evidence.files().isEmpty() && !allowed(context.configuration(), "UPLOAD"))
            return outcome(row, false, "DELIVERABLE_SOURCE_NOT_ALLOWED", "{}");
        for (var file : evidence.businessFiles().stream().sorted(Comparator.comparing(FileEvidenceApi.Document::artifactId)).toList()) {
            var scope = documentSources.resolve(row.getTenantId(), file);
            if (scope == null || !Objects.equals(scope.projectId(), row.getProjectId())
                    || !ProjectDocumentSourceRegistry.matches(context.configuration(), scope.sourceCode()))
                return outcome(row, false, "DELIVERABLE_DOCUMENT_SCOPE_CHANGED", "{}");
            var fact = fileEvidence.lockAndRevalidate(new FileEvidenceApi.Query(row.getTenantId(), file.artifactId(), file.versionNo(),
                    file.ownerContext(), file.objectType(), file.objectId(), file.purposeCode(), file.referenceKey(), file.sha256()));
            fileFacts.add(fact);
            if (!fact.valid()) return outcome(row, false, fact.reason(), JsonUtils.toJsonString(fileFacts));
            count++;
        }
        for (var file : evidence.files().stream().sorted(Comparator.comparing(FileArtifactVersionFact::artifactId)).toList()) {
            var fact = fileEvidence.lockAndRevalidate(new FileEvidenceApi.Query(row.getTenantId(), file.artifactId(), file.versionNo(),
                    OWNER, TYPE, row.getId().toString(), PURPOSE, file.referenceKey(), file.sha256()));
            fileFacts.add(fact);
            if (!fact.valid()) return outcome(row, false, fact.reason(), JsonUtils.toJsonString(fileFacts));
            count++;
        }
        if (evidence.businessResult() != null) {
            var old = evidence.businessResult();
            var current = inspectResult(old.tenantId(), old.projectId(), old.type(), old.objectId(), old.resultId());
            if (current == null || !Objects.equals(old.businessRevision(), current.businessRevision())
                    || !Objects.equals(old.resultId(), current.resultId()) || !Objects.equals(old.formedAt(), current.formedAt()))
                return outcome(row, false, "DELIVERABLE_BUSINESS_RESULT_INVALID", "{}");
            count++;
        }
        if (count < Math.max(Boolean.TRUE.equals(row.getRequired()) ? 1 : 0, minimum(context.configuration())))
            return outcome(row, false, "DELIVERABLE_QUANTITY_NOT_MET", "{}");
        var decision = rules.evaluate(row.getProjectId(), row.getDeliverableCode());
        return outcome(row, decision.satisfied(), decision.reason(), JsonUtils.toJsonString(Map.of("confirmation", decision, "files", fileFacts)));
    }

    @Transactional(rollbackFor = Exception.class)
    public Evaluation refresh(Long projectId, Long id) {
        var observed = require(deliverables.selectById(id), projectId);
        var context = rules.lock(projectId, observed.getDeliverableCode());
        Long actor = access.require(context, true, true);
        var row = require(deliverables.selectByIdForUpdate(new ProjectDeliverableIdLockQuery(observed.getTenantId(), id)), projectId);
        var result = revalidate(row);
        wakeup(row, actor, "deliverable-refresh:" + id + ":" + row.getVersion());
        return result;
    }

    private SourceEvidence capture(AccProjectDeliverableDO row, Submission request) {
        if ("BUSINESS_RESULT".equals(request.sourceType())) {
            var query = request.businessResult();
            if (!request.files().isEmpty() || query == null || !Objects.equals(query.tenantId(), row.getTenantId())
                    || !Objects.equals(query.projectId(), row.getProjectId()) || query.objectId() == null || query.resultId() == null)
                throw failure("请选择当前项目的完整业务成果");
            var result = inspectResult(row.getTenantId(), row.getProjectId(), query.type(), query.objectId(), query.resultId());
            if (result == null) throw failure("业务成果尚未形成、已撤销或已被替换");
            return new SourceEvidence(List.of(), result);
        }
        if (request.businessResult() != null || request.files().isEmpty()) throw failure("请上传交付文件");
        var seen = new HashSet<String>(); var captured = new ArrayList<FileArtifactVersionFact>();
        for (var selected : request.files().stream().sorted(Comparator.comparing(FileSelection::artifactId)).toList()) {
            if (!seen.add(selected.artifactId() + ":" + selected.versionNo())) throw failure("不能重复提交同一文件版本");
            var query = new FileArtifactVersionQuery(selected.artifactId(), selected.versionNo(), OWNER, TYPE, row.getId().toString(),
                    PURPOSE, selected.referenceKey(), FileActionCodes.READ);
            var file = files.inspect(query);
            captured.add(files.lockAndRevalidate(new FileArtifactVersionRevalidationQuery(selected.artifactId(), selected.versionNo(),
                    OWNER, TYPE, row.getId().toString(), PURPOSE, selected.referenceKey(), FileActionCodes.READ,
                    file.fileFactVersion(), file.scopeVersion())));
        }
        return new SourceEvidence(captured, null);
    }

    /** Called only by the committed file-event consumer; never grants the caller a business write permission. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void collectDocument(AccProjectDeliverableDO observed, FileDocumentSourceProvider.Scope scope,
                                FileEvidenceApi.Document file, String eventId) {
        var context = rules.read(observed.getProjectId(), observed.getDeliverableCode());
        if (!"ACTIVE".equals(context.lifecycleStatus()) || !ProjectDocumentSourceRegistry.matches(context.configuration(), scope.sourceCode())) return;
        context = rules.lock(observed.getProjectId(), observed.getDeliverableCode());
        if (!"ACTIVE".equals(context.lifecycleStatus()) || !Objects.equals(scope.projectId(), context.projectId())
                || !ProjectDocumentSourceRegistry.matches(context.configuration(), scope.sourceCode())) return;
        var row = require(deliverables.selectByIdForUpdate(new ProjectDeliverableIdLockQuery(observed.getTenantId(), observed.getId())), observed.getProjectId());
        String key = "file-event:" + eventId;
        if (submissions.selectRequest(row.getTenantId(), row.getId(), key) != null) return;
        var previous = sources.selectCurrentForUpdate(new DeliverableCurrentSourceLockQuery(row.getTenantId(), row.getId()));
        // Existing native ACC report/result projections keep their own immutable lineage.
        if (previous != null && !"ProjectDeliverableSubmission".equals(previous.getSourceObjectType())) return;
        var oldSubmission = previous == null ? null : submissions.selectSource(row.getTenantId(), previous.getId());
        var old = oldSubmission == null ? new SourceEvidence(List.of(), null)
                : JsonUtils.parseObject(oldSubmission.getSourceEvidence(), SourceEvidence.class);
        if (old.businessResult() != null) return;
        var documents = new ArrayList<>(old.businessFiles().stream().filter(d -> !Objects.equals(d.referenceId(), file.referenceId())).toList());
        if (file.available()) documents.add(file);
        documents.sort(Comparator.comparing(FileEvidenceApi.Document::referenceId));
        if (documents.equals(old.businessFiles())) return;
        var evidence = new SourceEvidence(old.files(), null, documents);
        Long submissionId = IdWorker.getId(), sourceId = IdWorker.getId();
        if (previous != null) {
            previous.setRelationStatus("SUPERSEDED"); previous.setUpdater("file-collection");
            if (sources.updateById(previous) != 1) throw failure("归集来源版本冲突");
        }
        var source = new ProjectDeliverableSourceVersionDO();
        source.setId(sourceId); source.setTenantId(row.getTenantId()); source.setDeliverableId(row.getId());
        source.setSourceRequirementId("PM-03"); source.setSourceObjectType("ProjectDeliverableSubmission");
        source.setSourceObjectId(submissionId); source.setSourceVersion(1); source.setRelationStatus("CURRENT");
        source.setArchiveStatus("NOT_REQUIRED"); source.setArchiveRetryCount(0);
        source.setCreator("file-collection"); source.setUpdater("file-collection");
        if (sources.insert(source) != 1) throw failure("归集来源保存失败");
        var submission = new ProjectDeliverableSubmissionDO();
        submission.setId(submissionId); submission.setTenantId(row.getTenantId()); submission.setProjectId(row.getProjectId());
        submission.setDeliverableId(row.getId()); submission.setPlanVersionId(context.planVersionId()); submission.setSourceVersionId(sourceId);
        submission.setRequestKey(key); submission.setRequestPayload(JsonUtils.toJsonString(file));
        submission.setConfigurationSnapshot(JsonUtils.toJsonString(context.configuration())); submission.setSourceType("BUSINESS_DOCUMENT");
        submission.setSourceEvidence(JsonUtils.toJsonString(evidence));
        submission.setDecisionEvidence("{}"); submission.setCreator("file-collection");
        if (submissions.insert(submission) != 1) throw failure("归集历史保存失败");
        row.setCurrentSourceVersionId(sourceId); row.setArchiveStatus("NOT_REQUIRED");
        row.setUpdater("file-collection");
        if (deliverables.updateById(row) != 1) throw failure("归集交付件冲突");
        var decision = revalidate(row);
        // This row is still uncommitted. Freeze the actual decision before its original insertion commits.
        submission.setDecisionEvidence(JsonUtils.toJsonString(decision));
        if (submissions.updateById(submission) != 1) throw failure("归集判定保存失败");
        wakeup(row, null, key);
    }

    /** Called by the committed business-result delivery; automaticSources is the grant, allowedSources still gates revalidation. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void collectBusinessResult(AccProjectDeliverableDO observed, String sourceCode, BusinessResultChange change) {
        var context = rules.read(observed.getProjectId(), observed.getDeliverableCode());
        if (!"ACTIVE".equals(context.lifecycleStatus()) || !ProjectDocumentSourceRegistry.matches(context.configuration(), sourceCode)) return;
        context = rules.lock(observed.getProjectId(), observed.getDeliverableCode());
        if (!"ACTIVE".equals(context.lifecycleStatus()) || !Objects.equals(observed.getProjectId(), context.projectId())
                || !ProjectDocumentSourceRegistry.matches(context.configuration(), sourceCode)) return;
        var row = require(deliverables.selectByIdForUpdate(new ProjectDeliverableIdLockQuery(observed.getTenantId(), observed.getId())), observed.getProjectId());
        String key = "result-change:" + change.eventId();
        if (submissions.selectRequest(row.getTenantId(), row.getId(), key) != null) return;
        var previous = sources.selectCurrentForUpdate(new DeliverableCurrentSourceLockQuery(row.getTenantId(), row.getId()));
        // Existing native ACC report/result projections keep their own immutable lineage.
        if (previous != null && !"ProjectDeliverableSubmission".equals(previous.getSourceObjectType())) return;
        // A formed result is the deliverable's single current evidence slot; collected documents are superseded, not merged.
        var evidence = new SourceEvidence(List.of(), change.observation().result());
        Long submissionId = IdWorker.getId(), sourceId = IdWorker.getId();
        if (previous != null) {
            previous.setRelationStatus("SUPERSEDED"); previous.setUpdater("result-collection");
            if (sources.updateById(previous) != 1) throw failure("归集来源版本冲突");
        }
        var source = new ProjectDeliverableSourceVersionDO();
        source.setId(sourceId); source.setTenantId(row.getTenantId()); source.setDeliverableId(row.getId());
        source.setSourceRequirementId("PM-03"); source.setSourceObjectType("ProjectDeliverableSubmission");
        source.setSourceObjectId(submissionId); source.setSourceVersion(1); source.setRelationStatus("CURRENT");
        source.setArchiveStatus("NOT_REQUIRED"); source.setArchiveRetryCount(0);
        source.setCreator("result-collection"); source.setUpdater("result-collection");
        if (sources.insert(source) != 1) throw failure("归集来源保存失败");
        var submission = new ProjectDeliverableSubmissionDO();
        submission.setId(submissionId); submission.setTenantId(row.getTenantId()); submission.setProjectId(row.getProjectId());
        submission.setDeliverableId(row.getId()); submission.setPlanVersionId(context.planVersionId()); submission.setSourceVersionId(sourceId);
        submission.setRequestKey(key); submission.setRequestPayload(JsonUtils.toJsonString(change));
        submission.setConfigurationSnapshot(JsonUtils.toJsonString(context.configuration())); submission.setSourceType("BUSINESS_RESULT");
        submission.setSourceEvidence(JsonUtils.toJsonString(evidence));
        submission.setDecisionEvidence("{}"); submission.setCreator("result-collection");
        if (submissions.insert(submission) != 1) throw failure("归集历史保存失败");
        row.setCurrentSourceVersionId(sourceId); row.setArchiveStatus("NOT_REQUIRED");
        row.setUpdater("result-collection");
        if (deliverables.updateById(row) != 1) throw failure("归集交付件冲突");
        var decision = revalidate(row);
        // This row is still uncommitted. Freeze the actual decision before its original insertion commits.
        submission.setDecisionEvidence(JsonUtils.toJsonString(decision));
        if (submissions.updateById(submission) != 1) throw failure("归集判定保存失败");
        wakeup(row, null, key);
    }

    private BusinessResultSource.Result inspectResult(Long tenant, Long project, BusinessResultSource.Type type, String object, String result) {
        var descriptor = results.types().stream().filter(value -> value.type().equals(type)).findFirst().orElseThrow(() -> failure("不支持的业务成果类型"));
        var observation = results.lockAndInspect(new BusinessResultSource.Query(tenant, project, type, object, descriptor.exactLookup() ? result : null));
        var current = observation.result();
        return current != null && current.validity() == BusinessResultSource.Validity.CURRENT && result.equals(current.resultId()) ? current : null;
    }

    private Evaluation outcome(AccProjectDeliverableDO row, boolean satisfied, String reason, String evidence) {
        String next = satisfied ? "ACCEPTED" : "PENDING";
        if (!next.equals(row.getStatus())) {
            row.setStatus(next);
            if (deliverables.updateById(row) != 1) throw failure("交付件自动判定冲突");
            var eventId = "deliverable-evaluated:" + row.getId() + ":" + row.getVersion();
            outbox.append("ProjectDeliverable", row.getId().toString(), new BusinessEvent(eventId, "ProjectDeliverableEvaluated.v1",
                    JsonUtils.toJsonString(Map.of("eventId", eventId, "deliverableId", row.getId(), "sourceVersionId", Objects.toString(row.getCurrentSourceVersionId(), ""),
                            "projectId", row.getProjectId(), "status", next, "reason", reason, "evidence", evidence, "version", row.getVersion()))));
        }
        return new Evaluation(satisfied, reason, evidence);
    }

    private void wakeup(AccProjectDeliverableDO row, Long actor, String correlation) {
        var event = ProjectRuleReevaluationRequested.create(row.getTenantId(), row.getProjectId(), actor, correlation);
        outbox.append("Project", row.getProjectId().toString(), new BusinessEvent(event.eventId(), ProjectRuleReevaluationRequested.EVENT_TYPE, JsonUtils.toJsonString(event)));
    }
    private AccProjectDeliverableDO require(AccProjectDeliverableDO row, Long projectId) {
        if (row == null || !Objects.equals(row.getTenantId(), TenantContextHolder.getRequiredTenantId())
                || !Objects.equals(row.getProjectId(), projectId) || Boolean.TRUE.equals(row.getDeleted())) throw failure("项目交付件不存在");
        return row;
    }
    static boolean allowed(JsonNode configuration, String source) {
        for (var value : configuration.path("allowedSources")) if (value.isTextual() && source.equals(value.asText())) return true;
        return false;
    }
    private static int minimum(JsonNode configuration) {
        var value = configuration.path("minimumQuantity");
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 0) throw failure("冻结模板的交付数量规则无效");
        return value.asInt();
    }
}
