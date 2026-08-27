package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationCursorPageRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.RequirementAnalysisAttachmentRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.RequirementAnalysisCompareRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.RequirementAnalysisSectionRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.RequirementAnalysisVersionRespVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.RequirementAnalysisWorkspaceRespVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.RequirementAnalysisSectionDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisRootMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.RequirementAnalysisSectionMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisHistoryQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisProjectQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisRowQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.RequirementAnalysisSectionListQuery;
import cn.iocoder.yudao.module.pms.platform.api.file.dto.FileFactVersion;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_ANALYSIS_COMMAND_INVALID;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.REQUIREMENT_NOT_EXISTS;

@Service
@RequiredArgsConstructor
public class RequirementAnalysisQueryService {

    public static final String TYPE = "PRE_04_REQUIREMENT_ANALYSIS";
    public static final String TYPE_ALIAS = "PRE_04";
    public static final String PERMISSION_QUERY = "pms:requirement-analysis:query";
    public static final String PERMISSION_MANAGE = "pms:requirement-analysis:manage";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final RequirementAnalysisRootMapper rootMapper;
    private final RequirementAnalysisSectionMapper sectionMapper;
    private final PermissionApi permissionApi;
    private final ProjectScopeApi projectScopeApi;
    private final ProjectParticipantFactApi participantFactApi;

    public boolean owns(Long preparationId, Long tenantId) {
        return preparationId != null && preparationId > 0 && tenantId != null
                && rootMapper.selectById(new RequirementAnalysisRowQuery(tenantId, preparationId)) != null;
    }

    public RequirementAnalysisWorkspaceRespVO getWorkspace(Long projectId, Actor actor) {
        requireRead(actor, projectId);
        RequirementAnalysisProjectQuery query = new RequirementAnalysisProjectQuery(actor.tenantId(), projectId);
        PreparationDO effective = rootMapper.selectEffective(query);
        PreparationDO draft = rootMapper.selectDraft(query);
        boolean manager = isCurrentManager(actor, projectId, ProjectScopeApi.ACTION_MANAGE);
        RequirementAnalysisWorkspaceRespVO response = new RequirementAnalysisWorkspaceRespVO();
        response.setProjectId(projectId);
        response.setCurrentEffective(effective == null ? null : toVersion(effective, false, actor));
        response.setDraft(draft == null || !manager ? null : toVersion(draft, false, actor));
        List<String> actions = new ArrayList<>();
        if (manager && draft == null) actions.add(effective == null ? "CREATE_INITIAL_DRAFT" : "CREATE_DRAFT");
        response.setAllowedActions(List.copyOf(actions));
        return response;
    }

    public RequirementAnalysisVersionRespVO getDetail(Long preparationId, Actor actor) {
        PreparationDO root = rootMapper.selectById(new RequirementAnalysisRowQuery(actor.tenantId(), preparationId));
        requireVisible(root, actor);
        boolean manager = isCurrentManager(actor, root.getProjectId(), ProjectScopeApi.ACTION_MANAGE);
        if ("DRAFT".equals(root.getStatusCode()) && !manager) throw exception(FORBIDDEN);
        return toVersion(root, true, actor);
    }

    public PreparationCursorPageRespVO<RequirementAnalysisVersionRespVO> getHistory(
            Long projectId, PreparationPageReqVO request, Actor actor) {
        requireRead(actor, projectId);
        Cursor cursor = parseCursor(request == null ? null : request.getCursor());
        int size = pageSize(request == null ? null : request.getPageSize());
        List<PreparationDO> fetched = rootMapper.selectCompletedHistory(new RequirementAnalysisHistoryQuery(
                actor.tenantId(), projectId, cursor.businessVersion(), cursor.id(), size + 1));
        boolean hasMore = fetched.size() > size;
        List<PreparationDO> page = hasMore ? fetched.subList(0, size) : fetched;
        return new PreparationCursorPageRespVO<>(page.stream().map(row -> toVersion(row, false, actor)).toList(),
                hasMore ? cursor(page.getLast()) : null, hasMore);
    }

    public RequirementAnalysisCompareRespVO compare(Long sourceId, Long targetId, Actor actor) {
        PreparationDO source = rootMapper.selectById(new RequirementAnalysisRowQuery(actor.tenantId(), sourceId));
        PreparationDO target = rootMapper.selectById(new RequirementAnalysisRowQuery(actor.tenantId(), targetId));
        requireVisible(source, actor);
        requireVisible(target, actor);
        if (!Objects.equals(source.getProjectId(), target.getProjectId()) || source.getId().equals(target.getId())
                || !("COMPLETED".equals(source.getStatusCode()) || "DRAFT".equals(source.getStatusCode()))
                || !("COMPLETED".equals(target.getStatusCode()) || "DRAFT".equals(target.getStatusCode()))) {
            throw exception(REQUIREMENT_ANALYSIS_COMMAND_INVALID);
        }
        PreparationDO draft = "DRAFT".equals(source.getStatusCode()) ? source
                : "DRAFT".equals(target.getStatusCode()) ? target : null;
        PreparationDO completed = draft == source ? target : source;
        if (draft != null && (!Integer.valueOf(1).equals(draft.getDraftMarker())
                || !"COMPLETED".equals(completed.getStatusCode())
                || !Objects.equals(draft.getSourcePreparationId(), completed.getId()))) {
            throw exception(REQUIREMENT_ANALYSIS_COMMAND_INVALID);
        }
        if (("DRAFT".equals(source.getStatusCode()) || "DRAFT".equals(target.getStatusCode()))
                && !isCurrentManager(actor, source.getProjectId(), ProjectScopeApi.ACTION_MANAGE)) {
            throw exception(FORBIDDEN);
        }
        Map<String, RequirementAnalysisSectionDO> left = sections(source).stream()
                .collect(Collectors.toMap(RequirementAnalysisSectionDO::getSectionCode, Function.identity()));
        Map<String, RequirementAnalysisSectionDO> right = sections(target).stream()
                .collect(Collectors.toMap(RequirementAnalysisSectionDO::getSectionCode, Function.identity()));
        Map<String, Boolean> codes = new LinkedHashMap<>();
        left.keySet().forEach(code -> codes.put(code, true));
        right.keySet().forEach(code -> codes.put(code, true));
        List<RequirementAnalysisCompareRespVO.SectionDifference> differences = codes.keySet().stream().sorted()
                .map(code -> difference(code, left.get(code), right.get(code))).toList();
        RequirementAnalysisCompareRespVO response = new RequirementAnalysisCompareRespVO();
        response.setSourcePreparationId(source.getId());
        response.setSourceBusinessVersion(source.getBusinessVersion());
        response.setTargetPreparationId(target.getId());
        response.setTargetBusinessVersion(target.getBusinessVersion());
        response.setSections(differences);
        return response;
    }

    public RequirementAnalysisVersionRespVO toVersion(PreparationDO root, boolean includeSections, Actor actor) {
        boolean manager = isCurrentManager(actor, root.getProjectId(), ProjectScopeApi.ACTION_MANAGE);
        RequirementAnalysisVersionRespVO response = new RequirementAnalysisVersionRespVO();
        response.setPreparationId(root.getId());
        response.setProjectId(root.getProjectId());
        response.setBusinessVersion(root.getBusinessVersion());
        response.setSourcePreparationId(root.getSourcePreparationId());
        response.setStatus(root.getStatusCode());
        response.setCurrentDraft(Integer.valueOf(1).equals(root.getDraftMarker()));
        response.setCurrentEffective(Integer.valueOf(1).equals(root.getEffectiveMarker()));
        response.setContentVersion(root.getContentVersion());
        response.setVersion(root.getVersion());
        response.setTemplateId(root.getTemplateId());
        response.setTemplateRevisionId(root.getTemplateRevisionId());
        response.setCompletedBy(root.getCompletedBy());
        response.setCompletedAt(root.getCompletedAt());
        response.setCreatedAt(root.getCreateTime());
        List<String> actions = new ArrayList<>();
        if (manager && Boolean.TRUE.equals(response.getCurrentDraft()) && "DRAFT".equals(root.getStatusCode())) {
            actions.add("EDIT");
            actions.add("COMPLETE");
        }
        response.setAllowedActions(List.copyOf(actions));
        response.setSections(includeSections ? sections(root).stream()
                .map(section -> toSection(section, manager && Boolean.TRUE.equals(response.getCurrentDraft())))
                .toList() : List.of());
        return response;
    }

    private RequirementAnalysisSectionRespVO toSection(RequirementAnalysisSectionDO row, boolean editable) {
        RequirementAnalysisSectionRespVO response = new RequirementAnalysisSectionRespVO();
        response.setSectionId(row.getId());
        response.setSourceSectionId(row.getSourceSectionId());
        response.setSectionCode(row.getSectionCode());
        response.setSectionName(row.getSectionName());
        response.setSectionKind(row.getSectionKindCode());
        response.setFieldType(row.getFieldTypeCode());
        response.setRequired(row.getRequiredFlag());
        response.setDictionaryType(row.getDictionaryType());
        response.setSortOrder(row.getSortOrder());
        response.setSchemaSnapshot(row.getSchemaSnapshot());
        response.setValueSnapshot(row.getValueSnapshot());
        response.setAttachments(parseAttachments(row.getAttachmentReferenceSnapshot()).stream()
                .map(this::toAttachment).toList());
        response.setVersion(row.getVersion());
        response.setAllowedActions(editable ? List.of("EDIT", "ATTACH", "REPLACE", "DETACH") : List.of());
        return response;
    }

    private RequirementAnalysisAttachmentRespVO toAttachment(AttachmentFact fact) {
        RequirementAnalysisAttachmentRespVO response = new RequirementAnalysisAttachmentRespVO();
        response.setArtifactId(fact.artifactId());
        response.setVersionNo(fact.versionNo());
        response.setReferenceKey(fact.referenceKey());
        response.setFileFactVersion(fact.fileFactVersion());
        response.setScopeVersion(fact.scopeVersion());
        return response;
    }

    private List<RequirementAnalysisSectionDO> sections(PreparationDO root) {
        return sectionMapper.selectList(new RequirementAnalysisSectionListQuery(root.getTenantId(), root.getId()));
    }

    private List<AttachmentFact> parseAttachments(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) return List.of();
        List<AttachmentFact> facts = JsonUtils.parseArray(snapshot, AttachmentFact.class);
        return facts == null ? List.of() : facts;
    }

    private RequirementAnalysisCompareRespVO.SectionDifference difference(
            String code, RequirementAnalysisSectionDO left, RequirementAnalysisSectionDO right) {
        if (left == null) return new RequirementAnalysisCompareRespVO.SectionDifference(code, "ADDED", true, true);
        if (right == null) return new RequirementAnalysisCompareRespVO.SectionDifference(code, "REMOVED", true, true);
        boolean content = !Objects.equals(left.getValueSnapshot(), right.getValueSnapshot());
        boolean files = !Objects.equals(left.getAttachmentReferenceSnapshot(), right.getAttachmentReferenceSnapshot());
        return new RequirementAnalysisCompareRespVO.SectionDifference(
                code, content || files ? "CHANGED" : "UNCHANGED", content, files);
    }

    private void requireVisible(PreparationDO root, Actor actor) {
        if (root == null) throw exception(REQUIREMENT_NOT_EXISTS);
        requireRead(actor, root.getProjectId());
    }

    private void requireRead(Actor actor, Long projectId) {
        requireActor(actor);
        if (!permissionApi.hasAnyPermissions(actor.actorId(), PERMISSION_QUERY)) {
            throw exception(FORBIDDEN);
        }
        var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                actor.tenantId(), actor.actorId(), projectId, ProjectScopeApi.ACTION_VIEW));
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId)) {
            throw exception(FORBIDDEN);
        }
    }

    private boolean isCurrentManager(Actor actor, Long projectId, String action) {
        try {
            if (!permissionApi.hasAnyPermissions(actor.actorId(), PERMISSION_MANAGE)) return false;
            var scope = projectScopeApi.resolveCurrent(new ProjectCurrentScopeQuery(
                    actor.tenantId(), actor.actorId(), projectId, action));
            if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(projectId)) {
                return false;
            }
            ProjectParticipantFact fact = participantFactApi.inspect(new ProjectParticipantFactQuery(
                    projectId, actor.actorId(), Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER),
                    LocalDateTime.now()));
            return fact != null && Objects.equals(fact.projectId(), projectId)
                    && Objects.equals(fact.userId(), actor.actorId()) && "ACTIVE".equals(fact.lifecycleStatus())
                    && fact.effectiveRoleCodes().contains(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER);
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    private Cursor parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return new Cursor(null, null);
        String[] parts = cursor.split(":", -1);
        try {
            if (parts.length != 2) throw new IllegalArgumentException();
            int version = Integer.parseInt(parts[0]);
            long id = Long.parseLong(parts[1]);
            if (version <= 0 || id <= 0) throw new IllegalArgumentException();
            return new Cursor(version, id);
        } catch (IllegalArgumentException failure) {
            throw exception(REQUIREMENT_ANALYSIS_COMMAND_INVALID);
        }
    }

    private int pageSize(Integer requested) {
        if (requested == null) return DEFAULT_PAGE_SIZE;
        if (requested < 1 || requested > MAX_PAGE_SIZE) throw exception(REQUIREMENT_ANALYSIS_COMMAND_INVALID);
        return requested;
    }

    private String cursor(PreparationDO row) {
        return row.getBusinessVersion() + ":" + row.getId();
    }

    private void requireActor(Actor actor) {
        if (actor == null || actor.tenantId() == null || actor.tenantId() < 0
                || actor.actorId() == null || actor.actorId() <= 0) throw exception(FORBIDDEN);
    }

    private record Cursor(Integer businessVersion, Long id) {
    }

    public record AttachmentFact(Long artifactId, Integer versionNo, String referenceKey,
                                 FileFactVersion fileFactVersion, Long scopeVersion) {
    }

    public record Actor(Long tenantId, Long actorId, String correlationId) {
    }
}
