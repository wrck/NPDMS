package cn.iocoder.yudao.module.pms.cutover.service.task;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.asset.api.device.ProjectDeviceShipmentApi;
import cn.iocoder.yudao.module.pms.cutover.service.task.context.CutoverTaskContext;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverCustomerLevelPort;
import cn.iocoder.yudao.module.pms.cutover.service.task.port.CutoverOwnerFactException;
import cn.iocoder.yudao.module.pms.cutover.service.task.view.CutoverTaskViews;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectBusinessContextApi;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.pms.cutover.service.task.CutoverTaskApplicationException.Code.*;

/** 详情式新建的只读选择资料；创建仍走原应用服务的锁定、幂等及来源重验。 */
@Service
@RequiredArgsConstructor
public class CutoverTaskCreationQueryService {
    private final ProjectBusinessContextApi projects;
    private final ProjectScopeApi scopes;
    private final ProjectDeviceShipmentApi shipments;
    private final DeviceQueryApi devices;
    private final CutoverCustomerLevelPort customerLevels;
    private final CutoverTaskQueryService tasks;

    public List<ProjectBusinessContextApi.Option> projects(ProjectOptionsQuery query) {
        requireKeyword(query.keyword());
        var context = query.context();
        var editable = scopes.resolveAllCurrent(new ProjectAllScopeQuery(
                context.tenantId(), context.actorId(), ProjectScopeApi.ACTION_EDIT));
        if (editable.isEmpty()) return List.of();
        return projects.searchOptions(ProjectBusinessContextApi.OptionsQuery.builder()
                .tenantId(context.tenantId()).actorId(context.actorId()).keyword(query.keyword()).build())
                .stream().filter(option -> editable.contains(option.id())).toList();
    }

    public ProjectContext project(ProjectQuery query) {
        var ownerQuery = authorizedProject(query);
        var presentation = projects.presentation(ownerQuery);
        var project = projects.inspect(ownerQuery);
        String customerLevel = null;
        if (project.customerId() != null) {
            try {
                var fact = customerLevels.inspect(project.customerId());
                if (fact != null) customerLevel = fact.serviceLevelCode();
            } catch (CutoverOwnerFactException exception) {
                if (exception.code() != CutoverOwnerFactException.Code.PROVIDER_UNAVAILABLE) throw exception;
            }
        }
        return new ProjectContext(presentation, customerLevel);
    }

    public ProjectDeviceShipmentApi.DevicePage devices(DevicePageQuery query) {
        requireKeyword(query.keyword());
        authorizedProject(new ProjectQuery(query.context(), query.projectId()));
        return shipments.pageDevices(ProjectDeviceShipmentApi.DeviceQuery.builder()
                .tenantId(query.context().tenantId()).actorId(query.context().actorId()).projectId(query.projectId())
                .keyword(query.keyword()).pageNo(query.pageNo()).pageSize(query.pageSize()).build());
    }

    public ResolvedDevices resolve(ResolveQuery query) {
        var serials = CutoverTaskQueryService.normalizeSerials(query.serialNumbers());
        var resolved = tasks.resolveCreateContext(query.context(), serials);
        var returnedSerials = resolved.candidates().stream().flatMap(candidate -> candidate.devices().stream())
                .map(device -> device.serialNumber().trim().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
        if (!returnedSerials.equals(serials.stream().map(serial -> serial.toUpperCase(Locale.ROOT)).collect(Collectors.toSet())))
            throw new CutoverTaskApplicationException(DATA_SCOPE_FORBIDDEN, "部分设备不在可创建项目范围内，未添加任何设备");
        var summaries = new ArrayList<ProjectDeviceShipmentApi.Device>();
        for (var candidate : resolved.candidates()) {
            for (var device : candidate.devices()) {
                var summary = devices.getDevice(device.deviceId());
                if (summary == null || !Objects.equals(summary.projectId(), candidate.project().projectId())
                        || !Objects.equals(summary.projectAssignmentVersion(), device.projectAssignmentVersion()))
                    throw new CutoverTaskApplicationException(DEVICE_SCOPE_STALE, "设备归属已变化，请重新查询序列号");
                if (summary.shipmentTime() == null)
                    throw new CutoverTaskApplicationException(INVALID_REQUEST, "设备尚无发货记录：" + device.serialNumber());
                summaries.add(new ProjectDeviceShipmentApi.Device(device.deviceId(), device.serialNumber(),
                        summary.productCode(), summary.productModel(), summary.conpVersion()));
            }
        }
        return new ResolvedDevices(resolved, List.copyOf(summaries));
    }

    private ProjectBusinessContextApi.Query authorizedProject(ProjectQuery query) {
        if (query.projectId() == null || query.projectId() <= 0)
            throw new CutoverTaskApplicationException(INVALID_REQUEST, "请选择项目");
        var context = query.context();
        var scope = scopes.resolveCurrent(ProjectCurrentScopeQuery.builder().tenantId(context.tenantId())
                .subjectUserId(context.actorId()).anchorProjectId(query.projectId())
                .actionCode(ProjectScopeApi.ACTION_EDIT).build());
        if (scope == null || !scope.fullProjectIds().contains(query.projectId()))
            throw new CutoverTaskApplicationException(DATA_SCOPE_FORBIDDEN, "项目不在可创建割接任务的范围内");
        return ProjectBusinessContextApi.Query.builder().tenantId(context.tenantId()).actorId(context.actorId())
                .projectId(query.projectId()).expectedScopeVersion(scope.treeVersion()).build();
    }

    private static void requireKeyword(String keyword) {
        if (keyword != null && keyword.length() > 128)
            throw new CutoverTaskApplicationException(INVALID_REQUEST, "查询条件不能超过128字");
    }

    public record ProjectOptionsQuery(CutoverTaskContext context, String keyword) {}
    public record ProjectQuery(CutoverTaskContext context, Long projectId) {}
    @lombok.Builder
    public record DevicePageQuery(CutoverTaskContext context, Long projectId, String keyword, int pageNo, int pageSize) {}
    public record ResolveQuery(CutoverTaskContext context, List<String> serialNumbers) {}
    public record ProjectContext(ProjectBusinessContextApi.Presentation project, String customerServiceLevel) {}
    public record ResolvedDevices(CutoverTaskViews.CreateContextData context, List<ProjectDeviceShipmentApi.Device> devices) {}
}
