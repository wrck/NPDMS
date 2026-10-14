package cn.iocoder.yudao.module.pms.cutover.api.task;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceScopeFactApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolveQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolutionResult;
import cn.iocoder.yudao.module.pms.cutover.api.task.dto.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.*;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.*;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.CutoverRuntimeTaskApplicationService;
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

/** 可信内部来源共用生产工作台；来源重复在锁定项目后返回原任务。 */
@Service
public class CutoverRuntimeTaskIntakeApiImpl implements CutoverTaskIntakeApi {
    private final CutoverTaskIntakeApiImpl delegate;
    private final CutoverIntakeSourceMapper sources;
    private final CutoverFrozenDeviceMapper taskDevices;
    private final ProjectBusinessContextApi projects;
    private final ProjectScopeApi scopes;
    private final PermissionApi permissions;
    private final DeviceScopeFactApi devices;
    private final ImplementationReadinessEvaluationApi readiness;
    public CutoverRuntimeTaskIntakeApiImpl(CutoverRuntimeTaskApplicationService application, CutoverIntakeSourceMapper sources,
            CutoverFrozenDeviceMapper taskDevices, ProjectBusinessContextApi projects, ProjectScopeApi scopes,
            PermissionApi permissions, DeviceScopeFactApi devices, ImplementationReadinessEvaluationApi readiness) {
        this.delegate = new CutoverTaskIntakeApiImpl(application); this.sources = sources; this.taskDevices = taskDevices;
        this.projects = projects; this.scopes = scopes; this.permissions = permissions; this.devices = devices; this.readiness = readiness;
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public CutoverTaskIntakeResult create(CutoverTaskIntakeCommand command) {
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
        if (existing != null) {
            var serials = taskDevices.byTask(new CutoverTaskDeviceListQuery(tenant, existing.getId())).stream()
                    .map(row -> DeviceScopeResolveQuery.comparisonKey(row.getSerialNumberSnapshot())).sorted().toList();
            var requested = command.serialNumbers() == null ? List.<String>of() : command.serialNumbers().stream()
                    .map(DeviceScopeResolveQuery::comparisonKey).sorted().toList();
            if (!Objects.equals(existing.getProjectId(), command.projectId()) || !serials.equals(requested)
                    || !Objects.equals(existing.getCutoverType(), command.cutoverType())
                    || !Objects.equals(existing.getNetworkMode(), command.networkMode())
                    || !Objects.equals(existing.getConfigurationCode(), command.configurationCode())
                    || !Objects.equals(existing.getTaskName(), command.taskName())
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
        return delegate.create(command);
    }
}
