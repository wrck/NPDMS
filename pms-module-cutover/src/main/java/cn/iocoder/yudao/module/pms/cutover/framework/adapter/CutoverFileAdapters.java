package cn.iocoder.yudao.module.pms.cutover.framework.adapter;

import cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanStorage;
import cn.iocoder.yudao.module.pms.platform.api.file.*;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.CutoverTaskMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.checklist.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure.CutoverClosureMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.plan.query.CutoverPlanChildrenQuery;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverProjectScopePort;
import cn.iocoder.yudao.module.pms.cutover.service.approval.CutoverRoleApprovalAudience;
import cn.iocoder.yudao.module.pms.cutover.service.checklist.port.CutoverChecklistFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import java.util.*;

@Configuration(proxyBeanMethods = false)
public class CutoverFileAdapters {
    @Bean
    CutoverFilePolicy cutoverChecklistFilePolicy(CutoverTaskMapper tasks, CutoverChecklistMapper lists,
            CutoverChecklistItemMapper items, CutoverClosureMapper closures, CutoverProjectScopePort scopes, PermissionApi permissions,
            CutoverRoleApprovalAudience approvalAudience, CutoverPlanRevisionMapper plans) {
        return new CutoverFilePolicy("CUTOVER_CHECKLIST_ITEM", tasks, lists, items, closures, scopes, permissions, approvalAudience, plans);
    }
    @Bean
    CutoverFilePolicy cutoverPlanFilePolicy(CutoverTaskMapper tasks, CutoverChecklistMapper lists,
            CutoverChecklistItemMapper items, CutoverClosureMapper closures, CutoverProjectScopePort scopes, PermissionApi permissions,
            CutoverRoleApprovalAudience approvalAudience, CutoverPlanRevisionMapper plans) {
        return new CutoverFilePolicy("CUTOVER_PLAN", tasks, lists, items, closures, scopes, permissions, approvalAudience, plans);
    }
    @Bean
    CutoverFilePolicy cutoverClosureFilePolicy(CutoverTaskMapper tasks, CutoverChecklistMapper lists,
            CutoverChecklistItemMapper items, CutoverClosureMapper closures, CutoverProjectScopePort scopes, PermissionApi permissions,
            CutoverRoleApprovalAudience approvalAudience, CutoverPlanRevisionMapper plans) {
        return new CutoverFilePolicy("CUTOVER_CLOSURE", tasks, lists, items, closures, scopes, permissions, approvalAudience, plans);
    }

    @Bean
    CutoverChecklistFilePort cutoverChecklistFilePort(@Lazy FileArtifactApi api) {
        return query -> {
            requireIdentity(query.tenantId(), query.actorId());
            var handle = query.handle();
            if (!Objects.equals(query.expectedScopeVersion(), handle.scopeVersion())) throw new IllegalArgumentException("附件项目范围已变化");
            var version = handle.fileFactVersion();
            var fact = api.lockAndRevalidate(new FileArtifactVersionRevalidationQuery(handle.artifactId(), handle.versionNo(),
                    "CUT", "CUTOVER_CHECKLIST_ITEM", query.checklistItemId().toString(), "MANUAL_EVIDENCE", handle.referenceKey(), FileActionCodes.READ,
                    new FileFactVersion(version.artifactVersion(), version.referenceVersion(), version.availabilityVersion()), handle.scopeVersion()));
            available(fact);
            var actual = fact.fileFactVersion();
            return CutoverChecklistFilePort.FileFact.builder()
                    .artifactId(fact.artifactId())
                    .versionNo(fact.versionNo())
                    .referenceKey(fact.referenceKey())
                    .fileFactVersion(new CutoverChecklistFilePort.FileFactVersion(actual.artifactVersion(), actual.referenceVersion(), actual.availabilityVersion()))
                    .scopeVersion(fact.scopeVersion())
                    .sha256(fact.sha256())
                    .build();
        };
    }

    @Bean
    CutoverClosureFilePort cutoverClosureFilePort(FileArtifactApi api) {
        return new CutoverClosureFilePort() {
            public FileFact inspect(FileExpectation expected) { return resolve(expected, false); }
            public FileFact lockAndRevalidate(FileExpectation expected) { return resolve(expected, true); }
            private FileFact resolve(FileExpectation value, boolean lock) {
                requireIdentity(value.tenantId(), value.actorId());
                var version = value.fileFactVersion();
                var query = new FileArtifactVersionRevalidationQuery(value.artifactId(), value.versionNo(), "CUT", "CUTOVER_CLOSURE",
                        value.closureId().toString(), value.purposeCode().name(), value.referenceKey(), FileActionCodes.READ,
                        new cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion(version.artifactVersion(), version.referenceVersion(), version.availabilityVersion()),
                        value.scopeVersion());
                var fact = lock ? api.lockAndRevalidate(query) : api.inspect(query.toInspectionQuery());
                available(fact);
                if (!Objects.equals(fact.sha256(), value.sha256())) throw new IllegalArgumentException("闭环附件内容版本不一致");
                var actual = fact.fileFactVersion();
                return FileFact.builder()
                        .artifactId(fact.artifactId())
                        .versionNo(fact.versionNo())
                        .referenceKey(fact.referenceKey())
                        .fileFactVersion(new FileFactVersion(actual.artifactVersion(), actual.referenceVersion(), actual.availabilityVersion()))
                        .scopeVersion(fact.scopeVersion())
                        .sha256(fact.sha256())
                        .build();
            }
        };
    }

    @Bean
    CutoverPlanFilePort cutoverPlanFilePort(FileArtifactApi api, FileEvidenceApi evidence, NativeGeneratedFileApi generated,
            CutoverTaskMapper tasks, CutoverPlanRevisionMapper plans, CutoverPlanStepMapper steps, CutoverSupportArrangementMapper supports, CutoverPlanStorage planStorage) {
        return new CutoverPlanFilePort() {
            public FileFact inspect(CutoverPlanFilePort.FileQuery query) {
                return resolve(query, false);
            }
            public FileFact lockAndRevalidate(CutoverPlanFilePort.FileQuery query) {
                return resolve(query, true);
            }
            private FileFact resolve(CutoverPlanFilePort.FileQuery expected, boolean lock) {
                requireIdentity(expected.tenantId(), expected.actorId());
                var handle = expected.handle();
                var document = evidence.inspectDocumentByArtifact(expected.tenantId(), handle.artifactId(), handle.versionNo());
                if (document == null || !"CUT".equals(document.ownerContext()) || !"CUTOVER_PLAN".equals(document.objectType())
                        || !"FULL_PLAN".equals(document.purposeCode()) || !Objects.equals(handle.referenceKey(), document.referenceKey())) {
                    throw new IllegalArgumentException("方案附件业务归属不匹配");
                }
                var task = tasks.selectById(Long.valueOf(document.objectId()));
                if (task == null || !Objects.equals(task.getTenantId(), expected.tenantId()) || !Objects.equals(task.getProjectId(), expected.projectId())) {
                    throw new IllegalArgumentException("方案附件不属于当前授权项目");
                }
                var version = handle.fileFactVersion();
                var query = new FileArtifactVersionRevalidationQuery(handle.artifactId(), handle.versionNo(), "CUT", "CUTOVER_PLAN",
                        document.objectId(), "FULL_PLAN", handle.referenceKey(), FileActionCodes.READ,
                        new cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion(version.artifactVersion(), version.referenceVersion(), version.availabilityVersion()),
                        handle.scopeVersion());
                return fact(lock ? api.lockAndRevalidate(query) : api.inspect(query.toInspectionQuery()));
            }
            public FileFact downloadDraft(CutoverPlanFilePort.DraftQuery query) {
                Long tenant = query.tenantId();
                Long actor = query.actorId();
                Long project = query.projectId();
                Long revisionId = query.planRevisionId();
                requireIdentity(tenant, actor);
                var plan = plans.selectById(revisionId);
                if (plan == null || !Objects.equals(plan.getTenantId(), tenant) || !"DRAFT".equals(plan.getStatusCode())) throw new IllegalArgumentException("方案草稿不可用");
                var task = tasks.selectById(plan.getCutoverTaskId());
                if (task == null || !Objects.equals(task.getTenantId(), tenant) || !Objects.equals(task.getProjectId(), project)) throw new IllegalArgumentException("方案项目不一致");
                if (plan.getFileArtifactId() != null) return inspect(CutoverPlanFilePort.FileQuery.builder()
                        .tenantId(tenant)
                        .actorId(actor)
                        .projectId(project)
                        .handle(FileHandle.builder()
                                .artifactId(plan.getFileArtifactId())
                                .versionNo(plan.getFileVersionNo())
                                .referenceKey(plan.getFileReferenceKey())
                                .fileFactVersion(CutoverPlanStorage.fileVersion(plan))
                                .scopeVersion(plan.getFileScopeVersion())
                                .build())
                        .build());
                byte[] bytes = cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanDocumentRenderer.render(
                        cn.iocoder.yudao.module.pms.cutover.service.plan.CutoverPlanDocumentRenderer.RenderContext.builder()
                                .task(task).plan(plan).content(planStorage.content(plan))
                                .steps(steps.selectListByPlan(new CutoverPlanChildrenQuery(tenant, revisionId)))
                                .supports(supports.selectListByPlan(new CutoverPlanChildrenQuery(tenant, revisionId)))
                                .fileName(file -> {
                            var reference = evidence.inspectDocumentByArtifact(tenant, Long.valueOf(file.path("artifactId").asText()), file.path("versionNo").asInt());
                            if (reference == null || !reference.available() || !"CUT".equals(reference.ownerContext())
                                    || !"CUTOVER_PLAN".equals(reference.objectType()) || !task.getId().toString().equals(reference.objectId())
                                    || !Objects.equals(reference.referenceKey(), file.path("referenceKey").asText()))
                                throw new IllegalArgumentException("方案引用附件版本不可用");
                            return reference.name();
                        }).build());
                var registered = generated.create(new NativeGeneratedFileCommand(tenant, actor, "cutover-plan:" + revisionId + ":" + plan.getVersion(),
                        "CUT", "CUTOVER_PLAN", task.getId(), task.getVersion(), "FULL_PLAN", "CUTOVER_PLAN", "割接方案-v" + plan.getRevisionNo() + ".docx",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes));
                var document = evidence.inspectDocument(tenant, registered.referenceId());
                return fact(api.inspect(new FileArtifactVersionQuery(registered.artifactId(), registered.versionNo(), "CUT", "CUTOVER_PLAN",
                        task.getId().toString(), "FULL_PLAN", document.referenceKey(), FileActionCodes.READ)));
            }
            private FileFact fact(FileArtifactVersionFact value) {
                available(value);
                var v = value.fileFactVersion();
                return FileFact.builder()
                        .artifactId(value.artifactId())
                        .versionNo(value.versionNo())
                        .referenceKey(value.referenceKey())
                        .fileFactVersion(new FileFactVersion(v.artifactVersion(), v.referenceVersion(), v.availabilityVersion()))
                        .scopeVersion(value.scopeVersion())
                        .sha256(value.sha256())
                        .build();
            }
        };
    }
    private static void requireIdentity(Long tenant, Long actor) {
        CutoverOwnerAdapters.requireTenant(tenant);
        if (!Objects.equals(actor, CutoverOwnerAdapters.actor())) throw new SecurityException("附件访问用户不一致");
    }
    private static void available(FileArtifactVersionFact fact) {
        if (fact == null || !"AVAILABLE".equals(fact.availabilityStatus()) || !"ACTIVE".equals(fact.referenceStatus())) throw new IllegalArgumentException("附件版本尚不可用");
    }
}
