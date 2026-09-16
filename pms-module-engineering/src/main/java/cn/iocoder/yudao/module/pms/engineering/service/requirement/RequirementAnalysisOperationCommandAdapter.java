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
        if (command.input().has("execution") || command.input().has("tenantId")) throw exception(BAD_REQUEST, "UNTRUSTED_EXECUTION_INPUT");
        String key = "PROJECT_OP:" + DigestUtil.sha256Hex(code + ":" + command.nodeKind() + ":" + command.nodeId() + ":" + command.idempotencyKey());
        EntityVersionProvider.Revision result;
        if (code.endsWith(".CREATE")) {
            if (command.objectId() != null) throw exception(BAD_REQUEST, "CREATE_OBJECT_MUST_BE_ABSENT");
            result = commands.getObject().create(new RequirementAnalysisEntityCommands.Create(command.projectId(), command.execution()), actor(), key);
        } else {
            Long id;
            try { id = Long.valueOf(command.objectId()); } catch (RuntimeException invalid) { throw exception(BAD_REQUEST, "BUSINESS_OBJECT_REQUIRED"); }
            var current = access.getObject().read(id, actor());
            if (!Objects.equals(current.getProjectId(), command.projectId()) || !Objects.equals(current.getVersion(), command.expectedBusinessVersion()))
                throw exception(BAD_REQUEST, "BUSINESS_VERSION_CONFLICT");
            var ref = current.revisionRef();
            if (code.endsWith(".SAVE")) {
                var input = JsonUtils.convertObject(command.input(), RequirementAnalysisEntityCommands.Patch.class);
                result = commands.getObject().save(ref, command.expectedBusinessVersion(),
                        new RequirementAnalysisEntityCommands.Patch(input.values(), input.extensionDefinitionRevisionId(), input.expectedExtensionVersion(),
                                input.extensionValues(), command.execution()), actor(), key);
            } else {
                String reason = command.input().path("reason").asText(null);
                var action = new RequirementAnalysisEntityCommands.Action(reason, command.execution());
                result = code.endsWith(".COMPLETE") ? commands.getObject().complete(ref, command.expectedBusinessVersion(), action, actor(), key)
                        : commands.getObject().copy(ref, command.expectedBusinessVersion(), action, actor(), key);
            }
        }
        if (result == null || result.ref() == null || result.ref().revisionId() == null) throw new IllegalStateException("OWNER_RESULT_IDENTITY_INVALID");
        String id = result.ref().revisionId().toString();
        String fact = "SOL:REQUIREMENT_ANALYSIS_REVISION:" + id + ":" + result.version() + ":" + result.state().name();
        return new ProjectOperationResult("SOL", "REQUIREMENT_ANALYSIS", id, id, result.version(), fact,
                code.endsWith(".COMPLETE") ? "REQUIREMENT_ANALYSIS_COMPLETED" : "REQUIREMENT_ANALYSIS_DRAFT_SAVED",
                JsonUtils.parseTree(JsonUtils.toJsonString(result)), false);
    }
}
