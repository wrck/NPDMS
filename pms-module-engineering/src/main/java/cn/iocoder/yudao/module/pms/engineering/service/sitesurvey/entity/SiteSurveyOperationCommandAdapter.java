package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.vo.SiteSurveyEntitySaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyEntityMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyEntityTaskObjectQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** Typed adapter of existing commands; state and form rules remain inside SiteSurveyEntityService. */
@Component
@RequiredArgsConstructor
public class SiteSurveyOperationCommandAdapter implements ProjectBusinessOperationCommandAdapter {
    private static final Set<String> CODES = Set.of("SOL.SITE_SURVEY.CREATE", "SOL.SITE_SURVEY.UPDATE", "SOL.SITE_SURVEY.DELETE",
            "SOL.SITE_SURVEY.CONFIRM", "SOL.SITE_SURVEY.REJECT", "SOL.SITE_SURVEY.ARCHIVE");
    private final ObjectProvider<SiteSurveyEntityService> services;
    private final ObjectProvider<SiteSurveyEntityMapper> mappers;
    private final ObjectProvider<ProjectScopeApi> scopes;
    private final ObjectProvider<PermissionApi> permissions;
    private final ObjectProvider<Validator> validators;
    @Override public boolean supports(String code, int version) { return version == 1 && CODES.contains(code); }
    @Override public void authorizeReplay(String code, ProjectOperationCommand command) {
        if (!supports(code, 1)) throw exception(BAD_REQUEST, "OPERATION_NOT_REGISTERED");
        Long actor = SecurityFrameworkUtils.getLoginUserId();
        String action = code.endsWith(".CREATE") ? "create" : code.endsWith(".DELETE") ? "delete" : "update";
        if (actor == null || !permissions.getObject().hasAnyPermissions(actor, "pms:sol-site-survey:" + action)) throw exception(FORBIDDEN);
        var scope = scopes.getObject().resolveCurrent(new ProjectCurrentScopeQuery(TenantContextHolder.getRequiredTenantId(), actor,
                command.projectId(), ProjectScopeApi.ACTION_MANAGE));
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(command.projectId())) throw exception(FORBIDDEN);
    }
    @Override public ProjectOperationResult invoke(String code, ProjectOperationCommand command) {
        authorizeReplay(code, command);
        var service = services.getObject(); var mapper = mappers.getObject();
        if (command.input().has("execution") || command.input().has("tenantId")) throw exception(BAD_REQUEST, "UNTRUSTED_EXECUTION_INPUT");
        Long id = null;
        if (!code.endsWith(".CREATE")) {
            try { id = Long.valueOf(command.objectId()); } catch (RuntimeException invalid) { throw exception(BAD_REQUEST, "BUSINESS_OBJECT_REQUIRED"); }
            var row = mapper.selectTaskObjectForUpdate(new SiteSurveyEntityTaskObjectQuery(TenantContextHolder.getRequiredTenantId(), command.projectId(), id));
            if (row == null || !Objects.equals(row.getProjectId(), command.projectId())
                    || !Objects.equals(row.getVersion(), command.expectedBusinessVersion())) throw exception(BAD_REQUEST, "BUSINESS_VERSION_CONFLICT");
        } else if (command.objectId() != null) throw exception(BAD_REQUEST, "CREATE_OBJECT_MUST_BE_ABSENT");
        if (code.endsWith(".CREATE") || code.endsWith(".UPDATE")) {
            var input = JsonUtils.convertObject(command.input(), SiteSurveyEntitySaveReqVO.class);
            if (input == null || input.getProjectId() != null && !command.projectId().equals(input.getProjectId())
                    || input.getId() != null && !Objects.equals(id, input.getId())) throw exception(BAD_REQUEST, "BUSINESS_OBJECT_MISMATCH");
            input.setProjectId(command.projectId()); input.setId(id); input.setExecution(command.execution());
            input.setVersion(command.expectedBusinessVersion());
            if (!validators.getObject().validate(input).isEmpty()) throw exception(BAD_REQUEST, "BUSINESS_INPUT_INVALID");
            if (code.endsWith(".CREATE")) id = service.createSiteSurveyEntity(input); else service.updateSiteSurveyEntity(input);
        } else switch (code) {
            case "SOL.SITE_SURVEY.DELETE" -> service.deleteSiteSurveyEntity(id, command.execution());
            case "SOL.SITE_SURVEY.CONFIRM" -> service.confirmSiteSurveyEntity(id, command.execution());
            case "SOL.SITE_SURVEY.REJECT" -> service.rejectSiteSurveyEntity(id, command.execution());
            case "SOL.SITE_SURVEY.ARCHIVE" -> service.archiveSiteSurveyEntity(id, command.execution());
            default -> throw exception(BAD_REQUEST, "OPERATION_NOT_REGISTERED");
        }
        boolean deleted = code.endsWith(".DELETE");
        var row = deleted ? null : mapper.selectById(id);
        if (!deleted && (row == null || !command.projectId().equals(row.getProjectId()))) throw new IllegalStateException("OWNER_RESULT_IDENTITY_INVALID");
        Integer version = deleted ? command.expectedBusinessVersion() : row.getVersion();
        String state = deleted ? "DELETED" : String.valueOf(row.getStatus());
        String result = switch (code) {
            case "SOL.SITE_SURVEY.CONFIRM" -> "SURVEY_CONFIRMED";
            case "SOL.SITE_SURVEY.ARCHIVE" -> "SURVEY_ARCHIVED";
            case "SOL.SITE_SURVEY.REJECT" -> "SURVEY_REJECTED";
            case "SOL.SITE_SURVEY.DELETE" -> "SURVEY_DELETED";
            default -> "SURVEY_DRAFT_SAVED";
        };
        return new ProjectOperationResult("SOL", "SITE_SURVEY", id.toString(), null, version,
                "SOL:SITE_SURVEY:" + id + ":" + version + ":" + state, result,
                JsonUtils.parseTree(JsonUtils.toJsonString(Map.of("id", id, "version", version, "deleted", deleted))), false);
    }
}
