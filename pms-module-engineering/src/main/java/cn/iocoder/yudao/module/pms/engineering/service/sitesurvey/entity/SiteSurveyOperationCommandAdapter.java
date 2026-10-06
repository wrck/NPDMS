package cn.iocoder.yudao.module.pms.engineering.service.sitesurvey.entity;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.sitesurvey.entity.vo.SiteSurveyEntitySaveReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.SiteSurveyEntityMapper;
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
    private final ObjectProvider<SiteSurveyEntityCommands> services;
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
        if (!code.endsWith(".CREATE")) {
            Long id;
            try { id=Long.valueOf(command.objectId()); } catch(RuntimeException invalid) { throw exception(BAD_REQUEST,"BUSINESS_OBJECT_REQUIRED"); }
            var identity=mappers.getObject().selectOperationIdentity(new cn.iocoder.yudao.module.pms.engineering.dal.mysql.sitesurvey.entity.query.SiteSurveyOperationIdentityQuery(TenantContextHolder.getRequiredTenantId(),id));
            if (identity==null || !TenantContextHolder.getRequiredTenantId().equals(identity.getTenantId()) || !id.equals(identity.getId())
                    || !command.projectId().equals(identity.getProjectId())) throw exception(FORBIDDEN);
        }
        var scope = scopes.getObject().resolveCurrent(new ProjectCurrentScopeQuery(TenantContextHolder.getRequiredTenantId(), actor,
                command.projectId(), ProjectScopeApi.ACTION_MANAGE));
        if (scope == null || scope.fullProjectIds() == null || !scope.fullProjectIds().contains(command.projectId())) throw exception(FORBIDDEN);
    }
    @Override public ProjectOperationResult invoke(String code, ProjectOperationCommand command) {
        authorizeReplay(code, command);
        var service = services.getObject();
        try {
            ProjectOperationInput.object(command.input());
            if (!code.endsWith(".CREATE") && !code.endsWith(".UPDATE")) ProjectOperationInput.fields(command.input(), Set.of());
        } catch (IllegalArgumentException invalid) { throw exception(BAD_REQUEST, "BUSINESS_INPUT_INVALID"); }
        Long id = null;
        if (!code.endsWith(".CREATE")) {
            try { id = Long.valueOf(command.objectId()); } catch (RuntimeException invalid) { throw exception(BAD_REQUEST, "BUSINESS_OBJECT_REQUIRED"); }

        } else if (command.objectId() != null) throw exception(BAD_REQUEST, "CREATE_OBJECT_MUST_BE_ABSENT");
        cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt receipt;
        if (code.endsWith(".CREATE") || code.endsWith(".UPDATE")) {
            SiteSurveyEntitySaveReqVO input;
            try { input = ProjectOperationInput.read(JsonUtils.getObjectMapper(), command.input(), SiteSurveyEntitySaveReqVO.class); }
            catch (IllegalArgumentException invalid) { throw exception(BAD_REQUEST, "BUSINESS_INPUT_INVALID"); }
            if (input == null || input.getProjectId() != null && !command.projectId().equals(input.getProjectId())
                    || input.getId() != null && !Objects.equals(id, input.getId())) throw exception(BAD_REQUEST, "BUSINESS_OBJECT_MISMATCH");
            if (input.getVersion() != null && !Objects.equals(input.getVersion(), command.expectedBusinessVersion()))
                throw exception(BAD_REQUEST, "BUSINESS_VERSION_CONFLICT");
            input.setProjectId(command.projectId()); input.setId(id); input.setExecution(command.execution());
            input.setVersion(command.expectedBusinessVersion());
            if (!validators.getObject().validate(input).isEmpty()) throw exception(BAD_REQUEST, "BUSINESS_INPUT_INVALID");
            receipt=service.executeReceipt(code.endsWith(".CREATE") ? "create" : "save",id,code.endsWith(".CREATE") ? null : command.expectedBusinessVersion(),input,
                    command.execution(),command.idempotencyKey(),cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.OperationEntryKind.PROJECT_NODE);
        } else {
            String operation=code.substring(code.lastIndexOf('.')+1).toLowerCase(java.util.Locale.ROOT);
            receipt=service.executeReceipt(operation,id,command.expectedBusinessVersion(),null,command.execution(),command.idempotencyKey(),
                    cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.OperationEntryKind.PROJECT_NODE);
        }
        var nativeResult=SiteSurveyBusinessApplicationService.result(receipt);
        if (!command.projectId().toString().equals(nativeResult.projectId())) throw new IllegalStateException("OWNER_RESULT_IDENTITY_INVALID");
        id=Long.valueOf(nativeResult.id());
        boolean deleted=nativeResult.deleted();Long version=nativeResult.version();String state=nativeResult.state();
        String result = switch (code) {
            case "SOL.SITE_SURVEY.CONFIRM" -> "SURVEY_CONFIRMED";
            case "SOL.SITE_SURVEY.ARCHIVE" -> "SURVEY_ARCHIVED";
            case "SOL.SITE_SURVEY.REJECT" -> "SURVEY_REJECTED";
            case "SOL.SITE_SURVEY.DELETE" -> "SURVEY_DELETED";
            default -> "SURVEY_DRAFT_SAVED";
        };
        var payload=(tools.jackson.databind.node.ObjectNode) JsonUtils.parseTree(JsonUtils.toJsonString(nativeResult));
        payload.set("operationReceipt",JsonUtils.parseTree(JsonUtils.toJsonString(receipt)));
        return new ProjectOperationResult("SOL", "SITE_SURVEY", id.toString(), null, version,
                "SOL:SITE_SURVEY:" + id + ":" + version + ":" + state, result,
                payload, false);
    }
}
