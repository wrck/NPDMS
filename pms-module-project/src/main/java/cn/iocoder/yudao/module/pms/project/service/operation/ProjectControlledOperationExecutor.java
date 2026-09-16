package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.ProjectNodeExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.workbinding.dto.ProjectBusinessExecutionSelection;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.service.projecttemplate.ProjectBusinessOperationRegistry;
import cn.iocoder.yudao.module.pms.project.service.taskbusiness.ProjectOperationContextResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/** All controlled writes join this transaction. POST never runs in an after-commit callback. */
@Service
@RequiredArgsConstructor
public class ProjectControlledOperationExecutor {
    private final ProjectOperationContextResolver contexts;
    private final ProjectNodeExecutionApi executions;
    private final ProjectMasterMapper projects;
    private final ProjectBusinessOperationRegistry registry;
    private final ProjectOperationAdapters adapters;
    private final List<ProjectBusinessOperationAccessProvider> accessProviders;
    private final ProjectOperationRuleEvaluator evaluator;
    private final PlatformCommandExecutionApi idempotency;
    private final ObjectProvider<ProjectOperationResultSink> sinks;

    @FunctionalInterface public interface Work { ProjectOperationResult invoke(ProjectOperationCommand command) throws Throwable; }

    @Transactional(rollbackFor = Exception.class)
    public ProjectOperationResult execute(String code, int version, ProjectOperationCommand request, Work work) {
        validate(request);
        Long tenant = TenantContextHolder.getRequiredTenantId(), actor = SecurityFrameworkUtils.getLoginUserId();
        if (actor == null || actor <= 0) throw exception(FORBIDDEN);
        var descriptor = registry.find(code, version);
        if (descriptor == null) throw exception(BAD_REQUEST, "OPERATION_NOT_DEPLOYED");
        var adapter = adapters.require(code, version);
        // Visibility and current Owner authorization apply to replays too. State/round checks apply only to new work.
        contexts.resolve(request.projectId(), request.nodeKind(), request.nodeId(), null);
        adapter.authorizeReplay(code, request);
        String digest = DigestUtil.sha256Hex(JsonUtils.toJsonString(request));
        String scope = "PROJECT_OPERATION:" + code + ":v" + version;
        String correlation = UUID.randomUUID().toString();
        var result = idempotency.execute(new PlatformCommandExecutionApi.IdempotencyScope(tenant, scope, actor, request.idempotencyKey()),
                digest, ProjectOperationResult.class, () -> perform(code, version, request, descriptor, work, tenant, actor, correlation),
                response -> new PlatformCommandExecutionApi.SuccessFacts(code, response.objectType(), response.objectId(), correlation,
                        JsonUtils.toJsonString(java.util.Map.of("resultCode", response.resultCode(),
                                "businessFactVersion", response.businessFactVersion(), "nodeKind", request.nodeKind(),
                                "nodeId", request.nodeId())), null, null));
        if (result.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw exception(BAD_REQUEST, "OPERATION_KEY_CONFLICT");
        if (result.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS) throw exception(BAD_REQUEST, "OPERATION_IN_PROGRESS");
        if (result.response() == null) throw new IllegalStateException("OPERATION_RESULT_MISSING");
        return result.decision() == PlatformCommandExecutionApi.Decision.REPLAY_COMPLETED ? result.response().asReplay() : result.response();
    }

    private ProjectOperationResult perform(String code, int version, ProjectOperationCommand request,
            ProjectBusinessOperationDescriptor descriptor, Work work, Long tenant, Long actor, String correlation) {
        if (!registry.runtimeAvailable(code, version)) throw exception(BAD_REQUEST, "OPERATION_RUNTIME_UNAVAILABLE");
        var project = projects.selectByIdForUpdate(request.projectId());
        if (project == null || !tenant.equals(project.getTenantId())) throw exception(FORBIDDEN);
        var context = contexts.resolve(request.projectId(), request.nodeKind(), request.nodeId(), request.execution());
        if (context.reason() != null || !context.permitted()) throw exception(BAD_REQUEST,
                context.reason() == null ? "NODE_OPERATION_FORBIDDEN" : context.reason());
        var binding = context.binding();
        if (binding == null || !descriptor.ownerContext().equals(binding.getTargetContextCode())
                || !descriptor.objectType().equals(binding.getTargetObjectType()) || binding.getOperationContract() == null)
            throw exception(BAD_REQUEST, "OPERATION_NOT_BOUND");
        var contract = FrozenOperationContract.read(binding.getOperationContract());
        var selected = contract.declaration().operations().stream().filter(o -> code.equals(o.operationCode())
                && Integer.valueOf(version).equals(o.operationVersion())).toList();
        if (selected.size() != 1) throw exception(BAD_REQUEST, "OPERATION_NOT_BOUND");
        if (request.execution().task() != null) executions.lockAndRevalidate(request.execution().task());
        else executions.lockAndRevalidateStage(request.execution().stage());
        var providers = accessProviders.stream().filter(p -> descriptor.ownerContext().equals(p.ownerContext())
                && descriptor.objectType().equals(p.objectType())).toList();
        if (providers.size() != 1) throw exception(BAD_REQUEST, "OWNER_OPERATION_PROVIDER_NOT_UNIQUE");
        var access = providers.getFirst().inspect(new ProjectBusinessOperationAccessProvider.Context(tenant, actor,
                request.projectId(), request.objectId()));
        if (access == null || !access.permittedOperations().contains(code)) throw exception(FORBIDDEN);
        if (request.objectId() != null && (request.expectedBusinessVersion() == null || request.expectedBusinessVersion() < 0
                || request.expectedObjectFactVersion() == null || !request.expectedObjectFactVersion().equals(access.objectFactVersion())))
            throw exception(BAD_REQUEST, "BUSINESS_VERSION_CONFLICT");
        String reference = "operation:" + code + ":v" + version + ":plan:" + context.round().getPlanVersionId()
                + ":execution:" + context.round().getId();
        var pre = evaluator.evaluate(reference + ":PRE", contract, selected.getFirst().pre(), project);
        if (!pre.permits()) throw exception(BAD_REQUEST, pre.reason() == null ? "OPERATION_PRE_NOT_MATCHED" : pre.reason());
        var current = request;
        if (request.execution().stage() != null) {
            var started = executions.beginStageHandling(request.execution().stage(), actor);
            current = request.withExecution(new ProjectBusinessExecutionSelection(null, started));
        }
        var frame = new ProjectVerifiedOperationScope.Frame(tenant, actor, request.projectId(), descriptor.ownerContext(),
                descriptor.objectType(), code, request.objectId(), current.execution());
        try (var ignored = ProjectVerifiedOperationScope.open(frame)) {
            ProjectOperationResult response;
            try { response = work.invoke(current); }
            catch (RuntimeException | Error failure) { throw failure; }
            catch (Throwable failure) { throw new IllegalStateException("OWNER_OPERATION_FAILED", failure); }
            if (response == null || !descriptor.ownerContext().equals(response.ownerContext())
                    || !descriptor.objectType().equals(response.objectType()) || response.objectId() == null
                    || response.businessFactVersion() == null || response.resultCode() == null)
                throw new IllegalStateException("OWNER_RESULT_IDENTITY_INVALID");
            // Owner writes may update project facts. Do not evaluate POST against the pre-command object.
            var after = projects.selectById(request.projectId());
            var post = evaluator.evaluate(reference + ":POST", contract, selected.getFirst().post(), after);
            if (!post.permits()) throw exception(BAD_REQUEST, post.reason() == null ? "OPERATION_POST_NOT_MATCHED" : post.reason());
            if (!response.replayed()) sinks.getObject().append(code, version, current, response, tenant, actor, correlation);
            return response;
        }
    }

    public static void validate(ProjectOperationCommand command) {
        if (command == null || command.projectId() == null || command.projectId() <= 0
                || command.nodeId() == null || command.nodeId() <= 0 || !Set.of("TASK", "STAGE").contains(String.valueOf(command.nodeKind()))
                || command.execution() == null || (command.execution().task() == null) == (command.execution().stage() == null)
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank() || command.idempotencyKey().length() > 128
                || command.input() == null || !command.input().isObject()
                || command.objectId() != null && (command.objectId().isBlank() || command.objectId().length() > 128))
            throw exception(BAD_REQUEST, "OPERATION_COMMAND_INVALID");
        var task = command.execution().task(); var stage = command.execution().stage();
        if (task != null ? !"TASK".equals(command.nodeKind()) || !Objects.equals(task.projectId(), command.projectId())
                || !Objects.equals(task.taskId(), command.nodeId()) : !"STAGE".equals(command.nodeKind())
                || !Objects.equals(stage.projectId(), command.projectId()) || !Objects.equals(stage.stageId(), command.nodeId()))
            throw exception(BAD_REQUEST, "OPERATION_CONTEXT_MISMATCH");
    }
}
