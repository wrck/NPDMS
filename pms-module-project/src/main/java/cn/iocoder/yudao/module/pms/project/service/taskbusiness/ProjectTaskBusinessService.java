package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewQueryApi;
import cn.iocoder.yudao.module.pms.platform.api.businessview.BusinessViewRevision;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider;
import cn.iocoder.yudao.module.pms.project.api.taskbusiness.TaskBusinessObjectProvider.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.*;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.taskbusiness.ProjectTaskBusinessLinkDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectTaskExecutionContractMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.CurrentTaskExecutionContractLockQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.ProjectTaskBusinessLinkMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskbusiness.query.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.ProjectTaskRuntimeMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.taskworkbench.query.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.pms.project.service.taskbusiness.TaskBusinessErrors.failure;

/** Approved template-upgrade relationship foundation; Owner remains the sole business-body writer. */
@Service
@RequiredArgsConstructor
public class ProjectTaskBusinessService {
    private final TaskBusinessAccess access;
    private final TaskBusinessProviderRegistry registry;
    private final ProjectTaskBusinessLinkMapper linkMapper;
    private final ProjectTaskRuntimeMapper taskMapper;
    private final ProjectTaskExecutionContractMapper contractMapper;
    private final PlatformCommandExecutionApi commands;
    private final OperationAuditApi audit;
    private final BusinessViewQueryApi businessViews;

    public TaskBusinessContext getContext(Long taskId, Long tenantId, Long actorId, String correlationId) {
        var task = access.read(taskId, tenantId, actorId);
        var ctx = new Context(tenantId, actorId, task.getProjectId(), taskId, correlationId);
        var contract = linkMapper.selectCurrentContract(query(ctx));
        TaskBusinessBinding binding = null;
        try {
            requireContract(contract, ctx);
            binding = TaskBusinessBinding.parse(contract);
            var provider = registry.require(binding.ownerContext(), binding.objectType());
            var rows = linkMapper.selectActive(query(ctx));
            var facts = inspectRows(ctx, contract, rows, provider, false);
            String reason = binding.unavailableReason();
            boolean writable = reason == null && !"READ_ONLY_AGGREGATE".equals(binding.instanceResolutionStrategy())
                    && access.writable(task, access.project(task.getProjectId()), ctx);
            Set<String> actions = new LinkedHashSet<>();
            if (writable) {
                var candidates = provider.candidates(ctx);
                if (candidates == null) throw failure("OWNER_FACT_INVALID");
                if (candidates.stream().map(f -> validateFact(f, null)).anyMatch(f -> f.allowedActions().contains("LINK")))
                    actions.add("LINK");
                if (facts.links().stream().anyMatch(f -> f.allowedActions().contains("UNLINK"))) actions.add("UNLINK");
            }
            Set<String> ownerActions = Set.copyOf(provider.inspectContext(ctx));
            BusinessViewRevision view = null;
            if (reason == null) {
                if (!ownerActions.contains("QUERY")) throw failure("OWNER_CONTEXT_FORBIDDEN");
                view = historicalView(binding);
            }
            return context(ctx, contract, binding, facts.links(), actions, reason, facts.factVersion(), ownerActions, view);
        } catch (RuntimeException ex) {
            return context(ctx, contract, binding, List.of(), Set.of(), safeError(ex), null, Set.of(), null);
        }
    }

    public List<BusinessObjectFact> getCandidates(Long taskId, Long tenantId, Long actorId, String correlationId) {
        var value = read(taskId, tenantId, actorId, correlationId);
        requireBinding(value.binding());
        List<BusinessObjectFact> candidates = value.provider().candidates(value.context());
        if (candidates == null) throw failure("OWNER_FACT_INVALID");
        return candidates.stream().map(f -> validateFact(f, null)).toList();
    }

    public List<TaskBusinessLinkFact> inspectLinkedFacts(Long taskId, Long tenantId, Long actorId, String correlationId) {
        return inspectLinkedFactsSnapshot(taskId, tenantId, actorId, correlationId).links();
    }

    public TaskBusinessLinkedFacts inspectLinkedFactsSnapshot(Long taskId, Long tenantId, Long actorId, String correlationId) {
        var value = read(taskId, tenantId, actorId, correlationId);
        return inspectRows(value.context(), value.contract(), linkMapper.selectActive(query(value.context())), value.provider(), false);
    }

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public TaskBusinessLinkedFacts lockAndRevalidateLinkedFacts(Long taskId, Long tenantId, Long actorId,
                                                               String correlationId, String expectedFactVersion) {
        requireTransaction();
        if (blank(expectedFactVersion)) throw failure("FACT_VERSION_REQUIRED");
        var task = access.read(taskId, tenantId, actorId);
        var ctx = new Context(tenantId, actorId, task.getProjectId(), taskId, correlationId);
        var locked = lock(ctx);
        if (!"ACTIVE".equals(locked.project().getLifecycleStatus())) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        var binding = TaskBusinessBinding.parse(locked.contract());
        var provider = registry.require(binding.ownerContext(), binding.objectType());
        var rows = linkMapper.selectActiveForUpdate(query(ctx));
        var before = inspectRows(ctx, locked.contract(), rows, provider, false);
        if (!expectedFactVersion.equals(before.factVersion())) throw failure("FACT_VERSION_CONFLICT");
        var result = inspectRows(ctx, locked.contract(), rows, provider, true);
        if (!expectedFactVersion.equals(result.factVersion())) throw failure("FACT_VERSION_CONFLICT");
        return result;
    }

    public record LinkCommand(Long taskId, String objectId, Integer expectedTaskVersion,
                              Integer expectedContractVersion, String idempotencyKey) {}
    public record UnlinkCommand(Long taskId, Long linkId, Integer expectedTaskVersion,
                                Integer expectedContractVersion, String idempotencyKey) {}
    public record LinkCommandResult(Long taskId, Long linkId, Integer taskVersion,
                                    Integer contractVersion, boolean active) {}

    public LinkCommandResult link(LinkCommand command, Long tenantId, Long actorId, String correlationId) {
        if (command == null || blank(command.objectId()) || command.objectId().length() > 128)
            throw failure("COMMAND_INVALID");
        return execute(command.taskId(), tenantId, actorId, correlationId, command.expectedTaskVersion(),
                command.expectedContractVersion(), command.idempotencyKey(), "LINK", JsonUtils.toJsonString(command),
                ctx -> mutate(ctx, command.objectId(), null, command.expectedTaskVersion(), command.expectedContractVersion()));
    }

    public LinkCommandResult unlink(UnlinkCommand command, Long tenantId, Long actorId, String correlationId) {
        if (command == null || command.linkId() == null || command.linkId() <= 0) throw failure("COMMAND_INVALID");
        return execute(command.taskId(), tenantId, actorId, correlationId, command.expectedTaskVersion(),
                command.expectedContractVersion(), command.idempotencyKey(), "UNLINK", JsonUtils.toJsonString(command),
                ctx -> mutate(ctx, null, command.linkId(), command.expectedTaskVersion(), command.expectedContractVersion()));
    }

    private LinkCommandResult execute(Long taskId, Long tenantId, Long actorId, String correlationId,
            Integer taskVersion, Integer contractVersion, String key, String operation, String payload,
            java.util.function.Function<Context, LinkCommandResult> action) {
        if (taskVersion == null || taskVersion < 0 || contractVersion == null || contractVersion <= 0
                || blank(key) || key.length() > 128 || blank(correlationId)) throw failure("COMMAND_INVALID");
        var task = access.read(taskId, tenantId, actorId);
        var ctx = new Context(tenantId, actorId, task.getProjectId(), taskId, correlationId);
        // Replays also require current actor write authority; do not leak previous command results after revocation.
        if (!access.writable(task, access.project(task.getProjectId()), ctx)) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        try {
            var execution = commands.execute(new PlatformCommandExecutionApi.IdempotencyScope(tenantId,
                    "PROJECT_TASK_BUSINESS_" + operation, actorId, key), sha256(payload), LinkCommandResult.class,
                    () -> { requireTransaction(); return action.apply(ctx); },
                    result -> new PlatformCommandExecutionApi.SuccessFacts("PROJECT_TASK_BUSINESS_" + operation,
                            "ProjectTaskBusinessLink", result.linkId().toString(), correlationId,
                            JsonUtils.toJsonString(result), null, null));
            if (execution.decision() == PlatformCommandExecutionApi.Decision.CONFLICT) throw exception(PMS_IDEMPOTENCY_KEY_CONFLICT);
            if (execution.decision() == PlatformCommandExecutionApi.Decision.IN_PROGRESS || execution.response() == null)
                throw exception(PMS_IDEMPOTENCY_IN_PROGRESS);
            return execution.response();
        } catch (RuntimeException ex) {
            audit.record(tenantId, actorId, correlationId, "PROJECT_TASK_BUSINESS_" + operation,
                    "ProjectTask", taskId.toString(), "REJECTED", Map.of("failureCode", safeError(ex)));
            throw ex;
        }
    }

    private LinkCommandResult mutate(Context ctx, String objectId, Long linkId, Integer taskVersion, Integer contractVersion) {
        var locked = lock(ctx);
        if (!access.writable(locked.task(), locked.project(), ctx)) throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        if (!Objects.equals(taskVersion, locked.task().getVersion())
                || !Objects.equals(contractVersion, locked.contract().getContractVersion()))
            throw exception(PROJECT_TASK_VERSION_CONFLICT);
        var binding = TaskBusinessBinding.parse(locked.contract());
        requireBinding(binding);
        if ("READ_ONLY_AGGREGATE".equals(binding.instanceResolutionStrategy())) throw failure("READ_ONLY_BINDING");
        var provider = registry.require(binding.ownerContext(), binding.objectType());
        var rows = linkMapper.selectActiveForUpdate(query(ctx));
        rows.forEach(row -> requireLink(row, ctx, locked.contract()));
        ProjectTaskBusinessLinkDO existing = linkId == null ? rows.stream()
                .filter(row -> row.getObjectId().equals(objectId)).findFirst().orElse(null)
                : rows.stream().filter(row -> row.getId().equals(linkId)).findFirst().orElse(null);
        if (linkId != null && existing == null) throw failure("LINK_NOT_ACTIVE");
        String target = linkId == null ? objectId : existing.getObjectId();
        String action = linkId == null ? "LINK" : "UNLINK";
        BusinessObjectFact inspected = validateFact(provider.inspect(ctx, target), target);
        requireAction(inspected, action);
        BusinessObjectFact fact = validateFact(provider.lockAndRevalidate(ctx, target, inspected.factVersion()), target);
        if (!inspected.factVersion().equals(fact.factVersion())) throw failure("FACT_VERSION_CONFLICT");
        requireAction(fact, action);
        if (linkId == null && existing != null)
            return new LinkCommandResult(ctx.taskId(), existing.getId(), taskVersion, contractVersion, true);
        LocalDateTime now = LocalDateTime.now();
        Long resultId;
        if (linkId == null) {
            var row = new ProjectTaskBusinessLinkDO();
            resultId = IdWorker.getId();
            row.setId(resultId); row.setTenantId(ctx.tenantId()); row.setProjectId(ctx.projectId()); row.setTaskId(ctx.taskId());
            row.setExecutionContractId(locked.contract().getId()); row.setContractVersion(contractVersion);
            row.setOwnerContext(binding.ownerContext()); row.setObjectType(binding.objectType()); row.setObjectId(target);
            row.setFactVersion(fact.factVersion()); row.setLinkedBy(ctx.actorId()); row.setLinkedAt(now); row.setVersion(0);
            row.setCreator(ctx.actorId().toString()); row.setUpdater(ctx.actorId().toString());
            row.setCreateTime(now); row.setUpdateTime(now);
            if (linkMapper.insertLink(row) != 1) throw failure("LINK_WRITE_FAILED");
        } else {
            resultId = linkId;
            if (linkMapper.unlinkIfMatch(new TaskBusinessUnlinkUpdate(ctx.tenantId(), ctx.projectId(), ctx.taskId(), linkId,
                    existing.getVersion(), ctx.actorId(), now)) != 1) throw exception(PROJECT_TASK_VERSION_CONFLICT);
        }
        if (taskMapper.incrementTaskVersionIfMatch(new TaskVersionUpdate(ctx.tenantId(), ctx.taskId(), taskVersion,
                ctx.actorId().toString())) != 1) throw exception(PROJECT_TASK_VERSION_CONFLICT);
        return new LinkCommandResult(ctx.taskId(), resultId, taskVersion + 1, contractVersion, linkId == null);
    }

    private ReadState read(Long taskId, Long tenantId, Long actorId, String correlationId) {
        var task = access.read(taskId, tenantId, actorId);
        var ctx = new Context(tenantId, actorId, task.getProjectId(), taskId, correlationId);
        var contract = linkMapper.selectCurrentContract(query(ctx));
        requireContract(contract, ctx);
        var binding = TaskBusinessBinding.parse(contract);
        return new ReadState(ctx, contract, binding, registry.require(binding.ownerContext(), binding.objectType()));
    }

    private Locked lock(Context ctx) {
        requireTransaction();
        var project = taskMapper.selectProjectForCommandForUpdate(new ProjectTaskProjectLockQuery(ctx.tenantId(), ctx.projectId()));
        var task = taskMapper.selectTaskForAssignmentForUpdate(new TaskAssignmentCommandQuery(ctx.tenantId(), ctx.projectId(), ctx.taskId()));
        if (project == null || task == null || !Objects.equals(project.getTenantId(), ctx.tenantId())
                || !Objects.equals(project.getId(), ctx.projectId()) || !Objects.equals(task.getTenantId(), ctx.tenantId())
                || !Objects.equals(task.getProjectId(), ctx.projectId()) || !Objects.equals(task.getId(), ctx.taskId()))
            throw exception(PROJECT_TASK_SCOPE_FORBIDDEN);
        var contract = contractMapper.selectCurrentByTaskIdForUpdate(new CurrentTaskExecutionContractLockQuery(ctx.tenantId(), ctx.taskId()));
        requireContract(contract, ctx);
        return new Locked(project, task, contract);
    }

    private TaskBusinessLinkedFacts inspectRows(Context ctx, ProjectTaskExecutionContractDO contract,
            List<ProjectTaskBusinessLinkDO> rows, TaskBusinessObjectProvider provider, boolean lockOwner) {
        var facts = new ArrayList<TaskBusinessLinkFact>();
        var digest = new ArrayList<Object>();
        digest.add(List.of(contract.getId(), contract.getContractVersion()));
        for (var row : rows.stream().sorted(Comparator.comparing(ProjectTaskBusinessLinkDO::getId)).toList()) {
            requireLink(row, ctx, contract);
            var fact = validateFact(provider.inspect(ctx, row.getObjectId()), row.getObjectId());
            if (lockOwner) {
                var locked = validateFact(provider.lockAndRevalidate(ctx, row.getObjectId(), fact.factVersion()), row.getObjectId());
                if (!fact.factVersion().equals(locked.factVersion())) throw failure("FACT_VERSION_CONFLICT");
                fact = locked;
            }
            facts.add(new TaskBusinessLinkFact(row.getId(), fact.objectId(), fact.displayName(), fact.factVersion(),
                    fact.completionFacts(), fact.artifacts(), fact.allowedActions()));
            digest.add(List.of(row.getId(), row.getVersion(), fact.objectId(), fact.factVersion()));
        }
        return new TaskBusinessLinkedFacts(sha256(JsonUtils.toJsonString(digest)), facts);
    }

    private void requireContract(ProjectTaskExecutionContractDO contract, Context ctx) {
        if (contract == null) throw failure("CONTRACT_NOT_FOUND");
        if (!Objects.equals(contract.getTenantId(), ctx.tenantId()) || !Objects.equals(contract.getProjectTaskId(), ctx.taskId())
                || contract.getEffectiveTo() != null || contract.getId() == null || contract.getContractVersion() == null
                || contract.getContractVersion() <= 0) throw failure("CONTRACT_IDENTITY_MISMATCH");
    }
    private void requireLink(ProjectTaskBusinessLinkDO row, Context ctx, ProjectTaskExecutionContractDO contract) {
        if (!Objects.equals(row.getTenantId(), ctx.tenantId()) || !Objects.equals(row.getProjectId(), ctx.projectId())
                || !Objects.equals(row.getTaskId(), ctx.taskId()) || row.getUnlinkedAt() != null
                || !Objects.equals(row.getExecutionContractId(), contract.getId())
                || !Objects.equals(row.getContractVersion(), contract.getContractVersion())
                || !Objects.equals(row.getOwnerContext(), contract.getTargetContextCode())
                || !Objects.equals(row.getObjectType(), contract.getTargetObjectType())) throw failure("LINK_CONTRACT_MISMATCH");
    }
    private BusinessObjectFact validateFact(BusinessObjectFact fact, String expectedId) {
        if (fact == null || blank(fact.objectId()) || fact.objectId().length() > 128 || blank(fact.displayName())
                || blank(fact.factVersion()) || fact.factVersion().length() > 256
                || expectedId != null && !expectedId.equals(fact.objectId())) throw failure("OWNER_FACT_INVALID");
        for (var artifact : fact.artifacts()) {
            if (blank(artifact.artifactId()) || blank(artifact.referenceKey()) || blank(artifact.sourceVersion())
                    || artifact.referenceKey().contains("://") || artifact.referenceKey().startsWith("data:")
                    || artifact.referenceKey().startsWith("blob:") || artifact.referenceKey().startsWith("//"))
                throw failure("OWNER_ARTIFACT_INVALID");
        }
        return fact;
    }
    private void requireAction(BusinessObjectFact fact, String action) {
        if (!fact.allowedActions().contains(action)) throw failure("OWNER_ACTION_FORBIDDEN");
    }
    private void requireBinding(TaskBusinessBinding binding) {
        if (binding.unavailableReason() != null) throw failure(binding.unavailableReason());
    }
    private TaskBusinessLinksQuery query(Context ctx) { return new TaskBusinessLinksQuery(ctx.tenantId(), ctx.projectId(), ctx.taskId()); }
    private TaskBusinessContext context(Context ctx, ProjectTaskExecutionContractDO c, TaskBusinessBinding b,
            List<TaskBusinessLinkFact> links, Set<String> actions, String error, String factVersion,
            Set<String> ownerActions, BusinessViewRevision businessView) {
        return new TaskBusinessContext(ctx.taskId(), ctx.projectId(), c == null ? null : c.getId(),
                c == null ? null : c.getContractVersion(), c == null ? null : c.getTargetContextCode(),
                c == null ? null : c.getTargetObjectType(), c == null ? null : c.getComponentKey(),
                b == null ? null : b.businessViewRevisionId(), b == null ? null : b.instanceResolutionStrategy(),
                links, actions, error, factVersion, ownerActions, businessView);
    }
    private BusinessViewRevision historicalView(TaskBusinessBinding binding) {
        var view = businessViews.getRevision(new BusinessViewQueryApi.Query(binding.businessViewRevisionId(),
                BusinessViewQueryApi.Purpose.HISTORICAL_REFERENCE));
        if (view == null || !Objects.equals(view.id(), binding.businessViewRevisionId())
                || !Objects.equals(view.ownerContext(), binding.ownerContext())
                || !Objects.equals(view.entityType(), binding.objectType())
                || !Objects.equals(view.componentKey(), binding.componentKey())) throw failure("VIEW_IDENTITY_MISMATCH");
        return new BusinessViewRevision(view.id(), view.entityType(), view.viewKey(), view.revisionNo(), view.ownerContext(),
                view.viewSource(), view.componentKey(), view.componentVersion(), view.dynamicFormRevisionId(),
                view.contextSchema(), view.supportedActions(), view.queryProviderKey(), view.commandProviderKey(),
                view.permissionProviderKey(), view.publishedAt(), view.disabledAt(), view.version(), view.status(), Set.of());
    }
    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw failure("TRANSACTION_REQUIRED");
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String safeError(RuntimeException ex) {
        if (ex instanceof ServiceException && ex.getMessage() != null && ex.getMessage().startsWith("TASK_BUSINESS_"))
            return ex.getMessage().substring("TASK_BUSINESS_".length());
        return "OWNER_FACT_UNAVAILABLE";
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private record ReadState(Context context, ProjectTaskExecutionContractDO contract, TaskBusinessBinding binding,
                             TaskBusinessObjectProvider provider) {}
    private record Locked(ProjectMasterDO project, ProjectTaskInstanceDO task, ProjectTaskExecutionContractDO contract) {}
}
