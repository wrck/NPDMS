package cn.iocoder.yudao.module.pms.engineering.service.preparation;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationSurveyPatchReqVO;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo.PreparationSurveyRespVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation.PreparationSurveyDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.PreparationSurveyMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationRowQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.preparation.query.PreparationInputInvalidationUpdate;
import cn.iocoder.yudao.module.pms.engineering.service.location.EngineeringLocationFactService;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectParticipantFactApi;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFact;
import cn.iocoder.yudao.module.pms.project.api.participant.dto.ProjectParticipantFactRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeResult;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/** PRE-02 / F-SOL-002. SOL authorization precedes AST work; all writes share the root transaction. */
@Service
@RequiredArgsConstructor
public class PreparationSurveyService {
    private final PreparationMapper preparationMapper;
    private final PreparationSurveyMapper surveyMapper;
    private final PreparationQueryService queryService;
    private final PreparationItemApplicationService itemApplicationService;
    private final EngineeringLocationFactService locationFactService;
    private final PermissionApi permissionApi;
    private final ProjectScopeApi scopeApi;
    private final ProjectParticipantFactApi participantApi;
    private final OperationAuditApi auditApi;
    private final TransactionTemplate transactionTemplate;

    public PreparationSurveyRespVO get(Long preparationId, PreparationItemApplicationService.Actor actor) {
        var preparation = queryService.getDetail(preparationId,
                new PreparationQueryService.Actor(actor.tenantId(), actor.actorId()));
        PreparationSurveyDO row = surveyMapper.selectByPreparation(new PreparationRowQuery(actor.tenantId(), preparationId));
        PreparationSurveyRespVO response = row == null ? new PreparationSurveyRespVO()
                : BeanUtils.toBean(row, PreparationSurveyRespVO.class);
        response.setPreparationId(preparationId);
        response.setVersion(preparation.getVersion());
        response.setAllowedActions(preparation.getAllowedActions().contains("SUBMIT")
                ? List.of("UPDATE_SURVEY") : List.of());
        return response;
    }

    public PreparationSurveyRespVO patch(Long preparationId, Integer expectedVersion,
            PreparationSurveyPatchReqVO request, PreparationItemApplicationService.Actor actor) {
        try {
            return transactionTemplate.execute(status -> patchLocked(preparationId, expectedVersion, request, actor));
        } catch (RuntimeException failure) {
            if (actor != null && actor.tenantId() != null && actor.actorId() != null) {
                auditApi.record(actor.tenantId(), actor.actorId(), actor.correlationId(), "PREPARATION_SURVEY_PATCH",
                        "Preparation", String.valueOf(preparationId), "REJECTED", Map.of("failure", failure.getClass().getSimpleName()));
            }
            throw failure;
        }
    }

    private PreparationSurveyRespVO patchLocked(Long preparationId, Integer expectedVersion,
            PreparationSurveyPatchReqVO request, PreparationItemApplicationService.Actor actor) {
        if (actor == null || actor.tenantId() == null || actor.tenantId() < 0 || actor.actorId() == null
                || actor.actorId() <= 0 || preparationId == null || preparationId <= 0
                || expectedVersion == null || expectedVersion < 0 || request == null
                || request.getExpectedProjectVersion() == null || request.getExpectedProjectVersion() < 0
                || request.getSurveyorUserId() != null && request.getSurveyorUserId() <= 0
                || Stream.of(request.getGrounding(), request.getConstructionResource(), request.getConclusion())
                    .anyMatch(value -> value != null && value.length() > 1000)) {
            throw exception(PREPARATION_COMMAND_INVALID);
        }
        if (!permissionApi.hasAnyPermissions(actor.actorId(), PreparationInitializationService.PERMISSION_MANAGE)) {
            throw exception(FORBIDDEN);
        }
        PreparationDO located = preparationMapper.selectById(new PreparationRowQuery(actor.tenantId(), preparationId));
        if (located == null) throw exception(PREPARATION_NOT_EXISTS);
        ProjectScopeResult current = scopeApi.resolveCurrent(new ProjectCurrentScopeQuery(actor.tenantId(),
                actor.actorId(), located.getProjectId(), ProjectScopeApi.ACTION_MANAGE));
        requireScope(current, located.getProjectId());
        requireScope(scopeApi.lockAndRevalidate(new ProjectScopeRevalidationQuery(actor.tenantId(), actor.actorId(),
                located.getProjectId(), ProjectScopeApi.ACTION_MANAGE, current.treeVersion())), located.getProjectId());
        ProjectParticipantFact manager = participantApi.lockAndRevalidate(new ProjectParticipantFactRevalidationQuery(
                located.getProjectId(), actor.actorId(), request.getExpectedProjectVersion(), "ACTIVE", null,
                Set.of(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)));
        if (manager == null || !Objects.equals(manager.projectId(), located.getProjectId())
                || !Objects.equals(manager.userId(), actor.actorId()) || !"ACTIVE".equals(manager.lifecycleStatus())
                || !manager.effectiveRoleCodes().contains(ProjectParticipantFactApi.ROLE_PROJECT_MANAGER)) {
            throw exception(PREPARATION_PROJECT_FACT_INVALID);
        }
        if (request.getSubmittedFields().contains("surveyorUserId") && request.getSurveyorUserId() != null) {
            itemApplicationService.validateCandidateLocked(located.getProjectId(), request.getExpectedProjectVersion(),
                    request.getSurveyorUserId());
        }
        PreparationDO root = preparationMapper.selectForUpdate(new PreparationRowQuery(actor.tenantId(), preparationId));
        if (root == null || !Objects.equals(root.getProjectId(), located.getProjectId())
                || !Integer.valueOf(1).equals(root.getCurrentMarker()) || !"DRAFT".equals(root.getStatusCode())
                || !Objects.equals(root.getVersion(), expectedVersion)) throw exception(PREPARATION_VERSION_NOT_MATCH);
        PreparationSurveyDO old = surveyMapper.selectByPreparation(new PreparationRowQuery(actor.tenantId(), preparationId));
        PreparationSurveyDO row = old == null ? new PreparationSurveyDO() : BeanUtils.toBean(old, PreparationSurveyDO.class);
        row.setTenantId(actor.tenantId()); row.setPreparationId(preparationId); row.setUpdater(String.valueOf(actor.actorId()));
        if (old == null) row.setCreator(String.valueOf(actor.actorId()));
        Set<String> fields = request.getSubmittedFields();
        if (fields.contains("surveyDate")) row.setSurveyDate(request.getSurveyDate());
        if (fields.contains("surveyorUserId")) row.setSurveyorUserId(request.getSurveyorUserId());
        if (fields.contains("grounding")) row.setGrounding(request.getGrounding());
        if (fields.contains("constructionResource")) row.setConstructionResource(request.getConstructionResource());
        if (fields.contains("conclusion")) row.setConclusion(request.getConclusion());
        if (request.getLocationCommand() != null) {
            String fallback = request.getLocationCommand().fallbackLocation();
            if (fallback != null && fallback.length() > 1000) throw exception(PREPARATION_COMMAND_INVALID);
            var fact = locationFactService.maintain(root.getProjectId(), "PREPARATION_SURVEY", preparationId,
                    expectedVersion + 1, fallback, request.getLocationCommand());
            if (fact == null || !("RESOLVED".equals(fact.resolutionStatus())
                    || "UNRESOLVED".equals(fact.resolutionStatus()) && fallback != null && !fallback.isBlank())) {
                throw exception(SITE_SURVEY_LOCATION_INVALID);
            }
            row.setLocation(fallback);
            row.setLocationResolutionStatus(fact.resolutionStatus());
            row.setAddressId(fact.addressId()); row.setAddressVersion(fact.addressVersion());
            row.setSiteId(fact.siteId()); row.setSiteVersion(fact.siteVersion());
            row.setSiteLocationId(fact.siteLocationId()); row.setSiteLocationVersion(fact.siteLocationVersion());
            row.setAddressSnapshot(fact.addressSnapshot()); row.setLocationSnapshot(fact.locationSnapshot());
        }
        if ((old == null ? surveyMapper.insert(row) : surveyMapper.update(row)) != 1
                || preparationMapper.invalidateReadinessIfMatch(new PreparationInputInvalidationUpdate(actor.tenantId(),
                root.getId(), root.getVersion(), root.getInputVersion(), root.getReadinessVersion(),
                String.valueOf(actor.actorId()))) != 1) throw exception(PREPARATION_VERSION_NOT_MATCH);
        auditApi.record(actor.tenantId(), actor.actorId(), actor.correlationId(), "PREPARATION_SURVEY_PATCH",
                "Preparation", String.valueOf(preparationId), "SUCCESS", Map.of("versionBefore", expectedVersion,
                        "versionAfter", expectedVersion + 1, "submittedFields", fields, "inputVersionAfter", root.getInputVersion() + 1));
        PreparationSurveyRespVO response = BeanUtils.toBean(row, PreparationSurveyRespVO.class);
        response.setVersion(expectedVersion + 1); response.setAllowedActions(List.of("UPDATE_SURVEY"));
        return response;
    }

    /** Called only by returnToDraft under its root transaction; never overwrites the source. */
    public void copy(Long tenantId, Long sourcePreparationId, Long targetPreparationId, Long actorId) {
        PreparationSurveyDO old = surveyMapper.selectByPreparation(new PreparationRowQuery(tenantId, sourcePreparationId));
        if (old == null) return;
        PreparationSurveyDO row = BeanUtils.toBean(old, PreparationSurveyDO.class);
        row.setTenantId(tenantId); row.setPreparationId(targetPreparationId);
        row.setCreator(String.valueOf(actorId)); row.setUpdater(String.valueOf(actorId));
        if (surveyMapper.insert(row) != 1) throw exception(PREPARATION_VERSION_NOT_MATCH);
    }

    private void requireScope(ProjectScopeResult scope, Long projectId) {
        if (scope == null || scope.treeVersion() == null || scope.fullProjectIds() == null
                || !scope.fullProjectIds().contains(projectId)) throw exception(PREPARATION_PROJECT_FACT_INVALID);
    }
}
