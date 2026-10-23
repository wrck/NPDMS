package cn.iocoder.yudao.module.pms.cutover.api.task;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceScopeFactApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolveQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolutionResult;
import cn.iocoder.yudao.module.pms.cutover.api.task.dto.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.task.query.*;
import cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskApplicationService;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.ImplementationReadinessEvaluationApi;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.ImplementationReadinessException;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessQuery;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import cn.iocoder.yudao.module.pms.cutover.api.task.dto.CutoverTaskIntakeCommand;
import cn.iocoder.yudao.module.pms.cutover.api.task.dto.CutoverTaskIntakeResult;
import cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskApplicationException;
import cn.iocoder.yudao.module.pms.cutover.service.task.command.CreateCutoverTaskCommand;
import cn.iocoder.yudao.module.pms.cutover.service.task.result.CutoverTaskCommandResult;
import java.util.Set;

/** 可信内部来源共用生产工作台；来源重复在锁定项目后返回原任务。 */
@Service
public class CutoverTaskIntakeApiImpl implements CutoverTaskIntakeApi {
    private final CutoverTaskApplicationService applicationService;
    private final CutoverIntakeSourceMapper sources;
    private final CutoverFrozenDeviceMapper taskDevices;
    private final ProjectBusinessContextApi projects;
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;
    private final DeviceScopeFactApi devices;
    private final ImplementationReadinessEvaluationApi readiness;
    public CutoverTaskIntakeApiImpl(CutoverTaskApplicationService application, CutoverIntakeSourceMapper sources,
            CutoverFrozenDeviceMapper taskDevices, ProjectBusinessContextApi projects, ProjectScopeApi scopes,
            PermissionApi permissions, DeviceScopeFactApi devices, ImplementationReadinessEvaluationApi readiness) {
        this.applicationService = application; this.sources = sources; this.taskDevices = taskDevices;
        this.projects = projects; this.scopes = scopes; this.permissions = permissions; this.devices = devices; this.readiness = readiness;
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public CutoverTaskIntakeResult create(CutoverTaskIntakeCommand command) {
        requireCommand(command);
        Long tenant = TenantContextHolder.getRequiredTenantId(), actor = SecurityFrameworkUtils.getLoginUserId();
        if (command == null || actor == null || !Objects.equals(actor, command.handlingEngineerUserId())
                || !permissions.hasAnyPermissions(actor, "pms:cutover-task:create")
                || !Set.of("ITR", "PROJECT_EVENT").contains(command.sourceType())) throw new SecurityException("割接接入身份无效");
        var scope = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder()
                .tenantId(tenant)
                .subjectUserId(actor)
                .anchorProjectId(command.projectId())
                .actionCode(ProjectScopeApi.ACTION_EDIT).build());
        if (!scope.fullProjectIds().contains(command.projectId())) throw new SecurityException("无权发起该项目割接");
        var projectQuery = ProjectBusinessContextApi.Query.builder()
                .tenantId(tenant)
                .actorId(actor)
                .projectId(command.projectId())
                .expectedScopeVersion(scope.treeVersion()).build();
        var project = projects.inspect(projectQuery);
        projects.lockAndRevalidate(ProjectBusinessContextApi.Revalidation.builder()
                .query(projectQuery)
                .expectedProjectVersion(project.projectVersion()).build());
        var existing = sources.find(new CutoverIntakeSourceQuery(tenant, command.sourceType(), command.sourceSystem(),
                command.sourceBusinessNo(), command.businessEventId()));
        // 自动配置不属于调用方载荷；重放交给同一应用命令的原载荷摘要校验，不能与任务当前投影比较。
        if (existing != null && command.configurationCode() == null) return createTask(command);
        if (existing != null) {
            var serials = taskDevices.byTask(new CutoverTaskDeviceListQuery(tenant, existing.getId())).stream()
                    .map(row -> DeviceScopeResolveQuery.comparisonKey(row.getSerialNumberSnapshot())).sorted().toList();
            var requested = command.serialNumbers() == null ? List.<String>of() : command.serialNumbers().stream()
                    .map(DeviceScopeResolveQuery::comparisonKey).sorted().toList();
            if (!Objects.equals(existing.getProjectId(), command.projectId()) || !serials.equals(requested)
                    || !Objects.equals(existing.getCutoverType(), command.cutoverType())
                    || !Objects.equals(existing.getNetworkMode(), command.networkMode())
                    || !Objects.equals(existing.getConfigurationCode(), command.configurationCode())
                    || (!("PROJECT_EVENT".equals(command.sourceType()) && command.taskName() == null)
                        && !Objects.equals(existing.getTaskName(), command.taskName()))
                    || !Objects.equals(existing.getBackground(), command.background())
                    || !Objects.equals(existing.getScheduledTime(), command.scheduledTime()))
                throw new CutoverTaskIntakeException(CutoverTaskIntakeException.Code.SOURCE_IDENTITY_CONFLICT, "来源身份已存在且内容不同");
            return new CutoverTaskIntakeResult(existing.getId(), existing.getTaskNo(), existing.getCurrentStage(),
                    existing.getTaskStatus(), existing.getVersion(), true);
        }
        if ("PROJECT_EVENT".equals(command.sourceType())) {
            var fact = devices.resolveBySerials(new DeviceScopeResolveQuery(tenant, command.projectId(), command.serialNumbers()));
            if (fact.decision() != DeviceScopeResolutionResult.Decision.RESOLVED) throw new IllegalArgumentException("项目设备范围无效");
            try {
                readiness.evaluate(new ImplementationReadinessQuery(tenant, command.projectId(), fact.fact().devices().stream()
                        .map(device -> new ImplementationReadinessQuery.ExpectedDevice(device.deviceId(), device.serialNumber(),
                                device.projectAssignmentVersion())).toList()));
            } catch (ImplementationReadinessException failure) {
                var code = switch (failure.getCode()) {
                    case SNAPSHOT_NOT_FOUND -> CutoverTaskIntakeException.Code.READINESS_NOT_READY;
                    case TENANT_CONTEXT_MISMATCH -> CutoverTaskIntakeException.Code.DATA_SCOPE_FORBIDDEN;
                    case PROVIDER_UNAVAILABLE, OWNER_DATA_CORRUPTED -> CutoverTaskIntakeException.Code.OWNER_PROVIDER_UNAVAILABLE;
                    case INVALID_REQUEST, DUPLICATE_DEVICE -> CutoverTaskIntakeException.Code.INVALID_REQUEST;
                };
                throw new CutoverTaskIntakeException(code, failure.getMessage(), failure);
            }
        }
        return createTask(command);
    }

    private static final Set<String> INTERNAL_SOURCES = Set.of("ITR", "PROJECT_EVENT");


        private CutoverTaskIntakeResult createTask(CutoverTaskIntakeCommand command) {
        requireCommand(command);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String idempotencyKey = "ITR".equals(command.sourceType())
                ? command.sourceSystem() + ":" + command.sourceBusinessNo()
                : command.businessEventId();
        try {
            CutoverTaskCommandResult result = applicationService.create(new CreateCutoverTaskCommand(
                    tenantId, command.handlingEngineerUserId(), idempotencyKey, command.correlationId(),
                    command.sourceType(), command.projectId(), command.serialNumbers(), command.configurationCode(),
                    command.taskName(),
                    command.background(), command.cutoverType(), command.networkMode(), command.scheduledTime(),
                    command.sourceSystem(), command.sourceBusinessNo(), command.businessEventId(), null));
            return new CutoverTaskIntakeResult(result.taskId(), result.taskNo(), result.currentStage(),
                    result.taskStatus(), result.version(), result.replayed());
        } catch (CutoverTaskApplicationException exception) {
            throw map(exception);
        }
    }

    private static void requireCommand(CutoverTaskIntakeCommand command) {
        if (command == null || !INTERNAL_SOURCES.contains(command.sourceType())
                || command.handlingEngineerUserId() == null || command.handlingEngineerUserId() <= 0) {
            throw new CutoverTaskIntakeException(CutoverTaskIntakeException.Code.INVALID_REQUEST,
                    "内部接入命令非法");
        }
    }

    private static CutoverTaskIntakeException map(CutoverTaskApplicationException exception) {
        CutoverTaskIntakeException.Code code = switch (exception.code()) {
            case INVALID_REQUEST -> CutoverTaskIntakeException.Code.INVALID_REQUEST;
            case CONFIGURATION_CONFLICT -> CutoverTaskIntakeException.Code.CONFIGURATION_CONFLICT;
            case DATA_SCOPE_FORBIDDEN, NOT_FOUND, PROJECT_SCOPE_STALE, PROJECT_CONTEXT_STALE, DEVICE_SCOPE_STALE ->
                    CutoverTaskIntakeException.Code.DATA_SCOPE_FORBIDDEN;
            case ACTIVE_DEVICE_CONFLICT, STATE_CONFLICT, TASK_VERSION_CONFLICT, ASSESSMENT_VERSION_CONFLICT ->
                    CutoverTaskIntakeException.Code.ACTIVE_DEVICE_CONFLICT;
            case READINESS_NOT_READY, IMPLEMENTATION_READINESS_STALE ->
                    CutoverTaskIntakeException.Code.READINESS_NOT_READY;
            case CUSTOMER_CONTEXT_INVALID, CUSTOMER_SERVICE_LEVEL_STALE ->
                    CutoverTaskIntakeException.Code.CUSTOMER_CONTEXT_INVALID;
            case PROJ_PROVIDER_UNAVAILABLE, AST_PROVIDER_UNAVAILABLE, CUS_PROVIDER_UNAVAILABLE,
                    IMP_PROVIDER_UNAVAILABLE ->
                    CutoverTaskIntakeException.Code.OWNER_PROVIDER_UNAVAILABLE;
            case BUSINESS_GATE_INVALID -> CutoverTaskIntakeException.Code.INVALID_REQUEST;
            case IDEMPOTENCY_CONFLICT, IDEMPOTENCY_IN_PROGRESS ->
                    CutoverTaskIntakeException.Code.SOURCE_IDENTITY_CONFLICT;
        };
        return new CutoverTaskIntakeException(code, exception.getMessage(), exception);
    }
}
