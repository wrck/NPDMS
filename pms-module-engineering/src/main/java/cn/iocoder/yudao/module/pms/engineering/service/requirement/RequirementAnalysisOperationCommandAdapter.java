package cn.iocoder.yudao.module.pms.engineering.service.requirement;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityActor;
import cn.iocoder.yudao.module.pms.platform.api.entity.EntityVersionProvider;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import java.util.Objects;
import java.util.Set;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Component
@RequiredArgsConstructor
public class RequirementAnalysisOperationCommandAdapter implements ProjectBusinessOperationCommandAdapter {
    private static final Set<String> CODES = Set.of("SOL.REQUIREMENT_ANALYSIS.CREATE", "SOL.REQUIREMENT_ANALYSIS.SAVE",
            "SOL.REQUIREMENT_ANALYSIS.COMPLETE", "SOL.REQUIREMENT_ANALYSIS.COPY");
    private final ObjectProvider<RequirementAnalysisEntityCommands> commands;
    private final ObjectProvider<RequirementAnalysisAccess> access;
    @Override public boolean supports(String code, int version) { return version == 1 && CODES.contains(code); }
    private EntityActor actor() { return new EntityActor(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), null); }
    @Override public void authorizeReplay(String code, ProjectOperationCommand command) {
        if (!supports(code, 1) || !access.getObject().isManager(command.projectId(), actor())) throw exception(FORBIDDEN);
        access.getObject().requireRead(command.projectId(), actor(), false);
    }
    @Override public ProjectOperationResult invoke(String code, ProjectOperationCommand command) {
        authorizeReplay(code, command);
        try {
            ProjectOperationInput.object(command.input());
            if (code.endsWith(".CREATE")) ProjectOperationInput.fields(command.input(), Set.of());
        } catch (IllegalArgumentException invalid) { throw exception(BAD_REQUEST, "BUSINESS_INPUT_INVALID"); }
        String key = "PROJECT_OP:" + DigestUtil.sha256Hex(code + ":" + command.nodeKind() + ":" + command.nodeId() + ":" + command.idempotencyKey());
        // Reuse the existing command key for Owner audit, form callbacks and committed-event correlation.
        var actor = new EntityActor(TenantContextHolder.getRequiredTenantId(), SecurityFrameworkUtils.getLoginUserId(), key);
        cn.iocoder.yudao.module.pms.platform.api.businessmodel.operation.BusinessOperationReceipt receipt;
        if (code.endsWith(".CREATE")) {
            if (command.objectId() != null) throw exception(BAD_REQUEST, "CREATE_OBJECT_MUST_BE_ABSENT");
            receipt = commands.getObject().executeReceipt("create",null,null,new RequirementAnalysisEntityCommands.Create(command.projectId(), command.execution()), actor, key);
        } else {
            Long id;
            try { id = Long.valueOf(command.objectId()); } catch (RuntimeException invalid) { throw exception(BAD_REQUEST, "BUSINESS_OBJECT_REQUIRED"); }
            var current = access.getObject().read(id, actor);
            if (!Objects.equals(current.getProjectId(), command.projectId())
                || !Objects.equals(current.getVersion(), command.expectedBusinessVersion() == null ? null : command.expectedBusinessVersion().longValue()))
                throw exception(BAD_REQUEST, "BUSINESS_VERSION_CONFLICT");
            var ref = current.revisionRef();
            if (code.endsWith(".SAVE")) {
                RequirementAnalysisEntityCommands.Patch input;
                try { input = ProjectOperationInput.read(JsonUtils.getObjectMapper(), command.input(), RequirementAnalysisEntityCommands.Patch.class); }
                catch (IllegalArgumentException invalid) { throw exception(BAD_REQUEST, "BUSINESS_INPUT_INVALID"); }
                receipt = commands.getObject().executeReceipt("save",ref, command.expectedBusinessVersion().intValue(),
                        new RequirementAnalysisEntityCommands.Patch(input.values(), input.extensionDefinitionRevisionId(), input.expectedExtensionVersion(),
                                input.extensionValues(), command.execution()), actor, key);
            } else {
                String reason;
                try {
                    ProjectOperationInput.fields(command.input(), Set.of("reason"));
                    reason = ProjectOperationInput.optionalText(command.input(), "reason");
                } catch (IllegalArgumentException invalid) { throw exception(BAD_REQUEST, "BUSINESS_INPUT_INVALID"); }
                var action = new RequirementAnalysisEntityCommands.Action(reason, command.execution());
                receipt = commands.getObject().executeReceipt(code.endsWith(".COMPLETE") ? "complete" : "copy",
                        ref,command.expectedBusinessVersion().intValue(),action,actor,key);
            }
        }
        var result = RequirementAnalysisEntityCommands.nativeRevision(receipt);
        if (result == null || result.ref() == null || result.ref().revisionId() == null) throw new IllegalStateException("OWNER_RESULT_IDENTITY_INVALID");
        String id = result.ref().revisionId().toString();
        String fact = "SOL:REQUIREMENT_ANALYSIS_REVISION:" + id + ":" + result.version() + ":" + result.state().name();
        var response=(tools.jackson.databind.node.ObjectNode) JsonUtils.parseTree(JsonUtils.toJsonString(result));
        response.set("operationReceipt",JsonUtils.parseTree(JsonUtils.toJsonString(receipt)));
        return new ProjectOperationResult("SOL", "REQUIREMENT_ANALYSIS", id, id, (long) result.version(), fact,
                code.endsWith(".COMPLETE") ? "REQUIREMENT_ANALYSIS_COMPLETED" : "REQUIREMENT_ANALYSIS_DRAFT_SAVED",
                response, false);
    }
}
