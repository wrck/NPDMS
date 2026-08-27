package cn.iocoder.yudao.module.pms.engineering.api.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.dto.RequirementAnalysisFact;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.dto.RequirementAnalysisFactQuery;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.dto.RequirementAnalysisFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.dto.RequirementAnalysisFactVector;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.dto.RequirementAnalysisFileFact;
import cn.iocoder.yudao.module.pms.engineering.api.requirement.dto.RequirementAnalysisSectionFact;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.RequirementAnalysisSectionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisSectionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisProjectQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisRowQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisSectionListQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.FileActionCodes;
import cn.iocoder.yudao.module.pms.platform.api.file.FileArtifactApi;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionFact;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileArtifactVersionRevalidationQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.project.api.organization.ProjectOrganizationFactApi;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.ProjectOrganizationFact;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.ProjectOrganizationFactQuery;
import cn.iocoder.yudao.module.pms.project.api.organization.dto.ProjectOrganizationFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_FILE_FACT_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_PROJECT_FACT_INVALID;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;

@Service
@RequiredArgsConstructor
public class RequirementAnalysisFactApiImpl implements RequirementAnalysisFactApi {

    private static final String OWNER_CONTEXT = "SOL";
    private static final String OBJECT_TYPE = "REQUIREMENT_ANALYSIS_SECTION";
    private static final String PURPOSE_CODE = "SECTION_ATTACHMENT";
    private static final String COMPLETED = "COMPLETED";

    private final RequirementAnalysisRootMapper rootMapper;
    private final RequirementAnalysisSectionMapper sectionMapper;
    private final PermissionApi permissionApi;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectOrganizationFactApi organizationFactApi;
    private final FileArtifactApi fileArtifactApi;

    @Override
    @Transactional(readOnly = true)
    public RequirementAnalysisFact inspect(RequirementAnalysisFactQuery query) {
        requireQuery(query);
        TrustedActor actor = trustedActor();
        requireQueryPermission(actor);
        requireScope(projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                actor.tenantId(), actor.actorId(), query.projectId(), ProjectScopeApi.ACTION_VIEW)), query.projectId());
        ProjectOrganizationFact project = organizationFactApi.inspect(new ProjectOrganizationFactQuery(query.projectId()));
        requireProject(project, query.projectId());
        PreparationDO selected = query.preparationId() == null
                ? rootMapper.selectEffective(new RequirementAnalysisProjectQuery(actor.tenantId(), query.projectId()))
                : rootMapper.selectById(new RequirementAnalysisRowQuery(actor.tenantId(), query.preparationId()));
        if (selected == null && query.preparationId() == null) return null;
        requireCompleted(selected, query.projectId());
        PreparationDO effective = rootMapper.selectEffective(
                new RequirementAnalysisProjectQuery(actor.tenantId(), query.projectId()));
        List<RequirementAnalysisSectionDO> sections = orderedSections(sectionMapper.selectList(
                new RequirementAnalysisSectionListQuery(actor.tenantId(), selected.getId())));
        return fact(selected, effective, project.projectVersion(), sections, inspectFiles(sections));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequirementAnalysisFact lockAndRevalidate(RequirementAnalysisFactRevalidationQuery query) {
        requireQuery(query);
        TrustedActor actor = trustedActor();
        requireQueryPermission(actor);

        ProjectScopeResult inspectedScope = requireScope(projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                actor.tenantId(), actor.actorId(), query.projectId(), ProjectScopeApi.ACTION_VIEW)), query.projectId());
        requireScope(projectScopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(actor.tenantId(),
                actor.actorId(), query.projectId(), ProjectScopeApi.ACTION_VIEW, inspectedScope.treeVersion())),
                query.projectId());
        ProjectOrganizationFact project = organizationFactApi.lockAndRevalidate(
                new ProjectOrganizationFactRevalidationQuery(query.projectId(), query.expectedProjectVersion()));
        requireProject(project, query.projectId());

        PreparationDO selected = rootMapper.selectForUpdate(
                new RequirementAnalysisRowQuery(actor.tenantId(), query.preparationId()));
        requireCompleted(selected, query.projectId());
        List<RequirementAnalysisSectionDO> sections = orderedSections(sectionMapper.selectListForUpdate(
                new RequirementAnalysisSectionListQuery(actor.tenantId(), selected.getId())));
        PreparationDO effective = rootMapper.selectEffectiveForUpdate(
                new RequirementAnalysisProjectQuery(actor.tenantId(), query.projectId()));
        requireExpectedRoot(query, selected, project);

        RequirementAnalysisFact current = fact(selected, effective, project.projectVersion(), sections,
                revalidateFiles(sections));
        if (!Objects.equals(query.expectedFactVector(), current.factVector())) {
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        }
        return current;
    }

    private Map<FileKey, RequirementAnalysisFileFact> inspectFiles(List<RequirementAnalysisSectionDO> sections) {
        List<AttachmentTarget> targets = attachmentTargets(sections);
        Map<FileKey, RequirementAnalysisFileFact> facts = new HashMap<>();
        for (AttachmentTarget target : targets) {
            FileArtifactVersionFact inspected = fileArtifactApi.inspect(fileQuery(target));
            facts.put(target.key(), requireExact(target, inspected));
        }
        return facts;
    }

    private Map<FileKey, RequirementAnalysisFileFact> revalidateFiles(List<RequirementAnalysisSectionDO> sections) {
        List<AttachmentTarget> targets = attachmentTargets(sections);
        Map<FileKey, RequirementAnalysisFileFact> facts = new HashMap<>();
        for (AttachmentTarget target : targets) {
            AttachmentSnapshot frozen = target.snapshot();
            FileArtifactVersionFact locked = fileArtifactApi.lockAndRevalidate(new FileArtifactVersionRevalidationQuery(
                    frozen.artifactId(), frozen.versionNo(), OWNER_CONTEXT, OBJECT_TYPE,
                    String.valueOf(target.sectionId()), PURPOSE_CODE, frozen.referenceKey(), FileActionCodes.READ,
                    frozen.fileFactVersion(), frozen.scopeVersion()));
            facts.put(target.key(), requireExact(target, locked));
        }
        return facts;
    }

    private List<AttachmentTarget> attachmentTargets(List<RequirementAnalysisSectionDO> sections) {
        List<AttachmentTarget> targets = new ArrayList<>();
        for (RequirementAnalysisSectionDO section : sections) {
            for (AttachmentSnapshot snapshot : parseAttachments(section.getAttachmentReferenceSnapshot())) {
                targets.add(new AttachmentTarget(section.getId(), snapshot));
            }
        }
        targets.sort(Comparator.comparing((AttachmentTarget target) -> target.snapshot().artifactId())
                .thenComparing(target -> target.snapshot().versionNo())
                .thenComparing(AttachmentTarget::sectionId)
                .thenComparing(target -> target.snapshot().referenceKey()));
        return targets;
    }

    private FileArtifactVersionQuery fileQuery(AttachmentTarget target) {
        AttachmentSnapshot frozen = target.snapshot();
        return new FileArtifactVersionQuery(frozen.artifactId(), frozen.versionNo(), OWNER_CONTEXT, OBJECT_TYPE,
                String.valueOf(target.sectionId()), PURPOSE_CODE, frozen.referenceKey(), FileActionCodes.READ);
    }

    private RequirementAnalysisFileFact requireExact(AttachmentTarget target, FileArtifactVersionFact current) {
        AttachmentSnapshot frozen = target.snapshot();
        if (current == null || !Objects.equals(current.artifactId(), frozen.artifactId())
                || !Objects.equals(current.versionNo(), frozen.versionNo())
                || !Objects.equals(current.referenceKey(), frozen.referenceKey())
                || !Objects.equals(current.fileFactVersion(), frozen.fileFactVersion())
                || !Objects.equals(current.scopeVersion(), frozen.scopeVersion())) {
            throw exception(REQUIREMENT_ANALYSIS_FILE_FACT_INVALID);
        }
        return new RequirementAnalysisFileFact(current.artifactId(), current.versionNo(), current.referenceKey(),
                current.fileFactVersion().artifactVersion(), current.fileFactVersion().referenceVersion(),
                current.fileFactVersion().availabilityVersion(), current.scopeVersion());
    }

    private RequirementAnalysisFact fact(PreparationDO selected, PreparationDO effective, Integer projectVersion,
                                         List<RequirementAnalysisSectionDO> sections,
                                         Map<FileKey, RequirementAnalysisFileFact> filesByKey) {
        List<RequirementAnalysisSectionFact> sectionFacts = sections.stream()
                .map(section -> sectionFact(section, filesByKey)).toList();
        List<RequirementAnalysisFileFact> fileFacts = sectionFacts.stream()
                .flatMap(section -> section.fileFacts().stream()).toList();
        RequirementAnalysisFactVector vector = new RequirementAnalysisFactVector(selected.getId(),
                selected.getBusinessVersion(), selected.getContentVersion(), selected.getTemplateRevisionId(), sectionFacts);
        boolean currentEffective = effective != null && Objects.equals(effective.getId(), selected.getId());
        return new RequirementAnalysisFact(selected.getProjectId(), selected.getId(), selected.getBusinessVersion(),
                selected.getStatusCode(), selected.getContentVersion(), projectVersion,
                selected.getTemplateRevisionId(), selected.getCompletedBy(), selected.getCompletedAt(), currentEffective,
                effective == null ? null : effective.getId(), effective == null ? null : effective.getBusinessVersion(),
                sectionFacts, fileFacts, vector);
    }

    private RequirementAnalysisSectionFact sectionFact(RequirementAnalysisSectionDO section,
                                                        Map<FileKey, RequirementAnalysisFileFact> filesByKey) {
        List<RequirementAnalysisFileFact> files = parseAttachments(section.getAttachmentReferenceSnapshot()).stream()
                .sorted(Comparator.comparing(AttachmentSnapshot::referenceKey))
                .map(snapshot -> filesByKey.get(new FileKey(section.getId(), snapshot.referenceKey())))
                .toList();
        if (files.stream().anyMatch(Objects::isNull)) throw exception(REQUIREMENT_ANALYSIS_FILE_FACT_INVALID);
        return new RequirementAnalysisSectionFact(section.getSectionCode(), section.getSectionName(),
                section.getSectionKindCode(), section.getFieldTypeCode(), Boolean.TRUE.equals(section.getRequiredFlag()),
                section.getSortOrder(), section.getSchemaSnapshot(), section.getValueSnapshot(), section.getVersion(), files);
    }

    private List<RequirementAnalysisSectionDO> orderedSections(List<RequirementAnalysisSectionDO> sections) {
        if (sections == null) throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        return sections.stream().sorted(Comparator.comparing(RequirementAnalysisSectionDO::getSectionCode)
                .thenComparing(RequirementAnalysisSectionDO::getId)).toList();
    }

    private List<AttachmentSnapshot> parseAttachments(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) return List.of();
        List<AttachmentSnapshot> parsed = JsonUtils.parseArray(snapshot, AttachmentSnapshot.class);
        if (parsed == null) throw exception(REQUIREMENT_ANALYSIS_FILE_FACT_INVALID);
        return parsed;
    }

    private void requireExpectedRoot(RequirementAnalysisFactRevalidationQuery query, PreparationDO selected,
                                     ProjectOrganizationFact project) {
        if (!Objects.equals(selected.getBusinessVersion(), query.expectedBusinessVersion())
                || !Objects.equals(selected.getContentVersion(), query.expectedContentVersion())
                || !Objects.equals(selected.getTemplateRevisionId(), query.expectedTemplateRevision())
                || !Objects.equals(project.projectVersion(), query.expectedProjectVersion())) {
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        }
    }

    private void requireCompleted(PreparationDO selected, Long projectId) {
        if (selected == null || !Objects.equals(selected.getProjectId(), projectId)
                || !COMPLETED.equals(selected.getStatusCode()) || selected.getBusinessVersion() == null
                || selected.getContentVersion() == null || selected.getTemplateRevisionId() == null
                || selected.getCompletedBy() == null || selected.getCompletedAt() == null) {
            throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
        }
    }

    private ProjectScopeResult requireScope(ProjectScopeResult scope, Long projectId) {
        if (scope == null || scope.treeVersion() == null || scope.treeVersion() < 0
                || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId)) {
            throw exception(REQUIREMENT_ANALYSIS_PROJECT_FACT_INVALID);
        }
        return scope;
    }

    private void requireProject(ProjectOrganizationFact project, Long projectId) {
        if (project == null || !Objects.equals(project.projectId(), projectId)
                || project.projectVersion() == null || project.projectVersion() < 0) {
            throw exception(REQUIREMENT_ANALYSIS_PROJECT_FACT_INVALID);
        }
    }

    private void requireQuery(RequirementAnalysisFactQuery query) {
        if (query == null) throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
    }

    private void requireQuery(RequirementAnalysisFactRevalidationQuery query) {
        if (query == null) throw exception(REQUIREMENT_ANALYSIS_FACT_NOT_AVAILABLE);
    }

    private TrustedActor trustedActor() {
        Long tenantId = TenantContextHolder.getTenantId();
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (tenantId == null || tenantId < 0 || actorId == null || actorId <= 0) {
            throw exception(REQUIREMENT_ANALYSIS_PROJECT_FACT_INVALID);
        }
        return new TrustedActor(tenantId, actorId);
    }

    private void requireQueryPermission(TrustedActor actor) {
        if (!permissionApi.hasAnyPermissions(actor.actorId(), "pms:requirement-analysis:query")) {
            throw exception(FORBIDDEN);
        }
    }

    private record AttachmentSnapshot(Long artifactId, Integer versionNo, String referenceKey,
                                      FileFactVersion fileFactVersion, Long scopeVersion) {
    }

    private record AttachmentTarget(Long sectionId, AttachmentSnapshot snapshot) {
        private FileKey key() {
            return new FileKey(sectionId, snapshot.referenceKey());
        }
    }

    private record FileKey(Long sectionId, String referenceKey) {
    }

    private record TrustedActor(Long tenantId, Long actorId) {
    }
}
