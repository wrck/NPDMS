package cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.engineering.api.arrival.dto.ArrivalScopeWatermark;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrivalacceptance.ArrivalAcceptanceDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.arrivalacceptance.ArrivalAcceptanceMapper;
import cn.iocoder.yudao.module.pms.engineering.domain.arrivalacceptance.ArrivalAcceptanceStateMachine;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeliveryScopePort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.DeviceScopeFactPort;
import cn.iocoder.yudao.module.pms.engineering.service.arrivalacceptance.port.ProjectQualificationPort;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 到货签收应用核心。COM/AST生产Provider形成前不注册Spring Bean，只允许显式组装测试替身。
 */
public final class ArrivalAcceptanceApplicationService {

    private final ArrivalAcceptanceMapper acceptanceMapper;
    private final ProjectQualificationPort projectQualificationPort;
    private final DeliveryScopePort deliveryScopePort;
    private final DeviceScopeFactPort deviceScopeFactPort;

    public ArrivalAcceptanceApplicationService(ArrivalAcceptanceMapper acceptanceMapper,
                                               ProjectQualificationPort projectQualificationPort,
                                               DeliveryScopePort deliveryScopePort,
                                               DeviceScopeFactPort deviceScopeFactPort) {
        this.acceptanceMapper = acceptanceMapper;
        this.projectQualificationPort = projectQualificationPort;
        this.deliveryScopePort = deliveryScopePort;
        this.deviceScopeFactPort = deviceScopeFactPort;
    }

    public ArrivalAcceptanceDO createDraft(CreateDraftCommand command) {
        requireCommand(command);
        ProjectQualificationPort.ProjectQualificationFact project = projectQualificationPort.inspect(
                command.tenantId(), command.projectId(), command.actorUserId());
        requireProject(project, command.projectId());

        DeliveryScopePort.AssignedScope deliveryScope =
                deliveryScopePort.inspectAssignedScope(command.projectId());
        requireDeliveryScope(deliveryScope, command.projectId());
        Set<String> serialNumbers = collectSerialNumbers(deliveryScope.lines());
        DeviceScopeFactPort.DeviceScopeFact deviceScope = deviceScopeFactPort.resolveBySerials(
                command.tenantId(), command.projectId(), serialNumbers);
        requireDeviceScope(deviceScope, command.projectId(), serialNumbers);

        ArrivalAcceptanceDO row = new ArrivalAcceptanceDO();
        row.setTenantId(command.tenantId());
        row.setProjectId(command.projectId());
        row.setBatchCode(command.batchCode().trim());
        row.setLogisticsNo(command.logisticsNo().trim());
        row.setArrivedAt(command.arrivedAt());
        row.setSignerSnapshot(JsonUtils.toJsonString(new SignerSnapshot(command.signerName().trim())));
        row.setStatus(ArrivalAcceptanceStateMachine.DRAFT);
        row.setProjectVersion(project.projectVersion());
        row.setProjectParticipantFactVersion(project.factVersion());
        row.setProjectScopeVersion(project.scopeVersion());
        row.setDeliveryScopeVersion(deliveryScope.scopeVersion());
        row.setExpectedScopeSnapshot(JsonUtils.toJsonString(new ExpectedScopeSnapshot(
                orderedLines(deliveryScope.lines()), orderedDevices(deviceScope.devices()))));
        row.setScopeWatermark(JsonUtils.toJsonString(new ArrivalScopeWatermark(
                deliveryScope.scopeVersion(), assignmentVersions(deviceScope.devices()))));
        row.setMigrationResolutionStatus("NOT_APPLICABLE");
        row.setVersion(0);
        row.setCreator(String.valueOf(command.actorUserId()));
        row.setUpdater(String.valueOf(command.actorUserId()));
        if (acceptanceMapper.insert(row) != 1 || row.getId() == null) {
            throw new IllegalStateException("arrival acceptance draft creation failed");
        }
        return row;
    }

    private static void requireCommand(CreateDraftCommand command) {
        if (command == null || command.tenantId() == null || command.tenantId() < 0
                || command.projectId() == null || command.projectId() <= 0
                || command.actorUserId() == null || command.actorUserId() <= 0
                || blank(command.batchCode()) || blank(command.logisticsNo())
                || command.arrivedAt() == null || blank(command.signerName())) {
            throw new IllegalArgumentException("invalid arrival acceptance draft command");
        }
    }

    private static void requireProject(ProjectQualificationPort.ProjectQualificationFact project,
                                       Long projectId) {
        if (project == null || !projectId.equals(project.projectId())) {
            throw new IllegalStateException("project qualification fact is unavailable or mismatched");
        }
    }

    private static void requireDeliveryScope(DeliveryScopePort.AssignedScope scope, Long projectId) {
        if (scope == null || !projectId.equals(scope.projectId())) {
            throw new IllegalStateException("assigned delivery scope is unavailable or mismatched");
        }
    }

    private static Set<String> collectSerialNumbers(List<DeliveryScopePort.AssignedLine> lines) {
        Set<String> serialNumbers = new HashSet<>();
        for (DeliveryScopePort.AssignedLine line : lines) {
            for (String serialNumber : line.serialNumbers()) {
                if (!serialNumbers.add(serialNumber)) {
                    throw new IllegalStateException("assigned serial number is duplicated");
                }
            }
        }
        return Set.copyOf(serialNumbers);
    }

    private static void requireDeviceScope(DeviceScopeFactPort.DeviceScopeFact scope, Long projectId,
                                           Set<String> expectedSerialNumbers) {
        if (scope == null || !projectId.equals(scope.projectId())) {
            throw new IllegalStateException("device scope fact is unavailable or mismatched");
        }
        Set<String> actualSerialNumbers = new HashSet<>();
        Set<Long> deviceIds = new HashSet<>();
        for (DeviceScopeFactPort.DeviceFact device : scope.devices()) {
            if (!projectId.equals(device.currentProjectId())
                    || !actualSerialNumbers.add(device.serialNumber()) || !deviceIds.add(device.deviceId())) {
                throw new IllegalStateException("device scope contains foreign or duplicate device");
            }
        }
        if (!actualSerialNumbers.equals(expectedSerialNumbers)) {
            throw new IllegalStateException("device scope does not resolve every assigned serial number");
        }
    }

    private static List<DeliveryScopePort.AssignedLine> orderedLines(
            List<DeliveryScopePort.AssignedLine> lines) {
        return lines.stream().sorted(Comparator.comparing(DeliveryScopePort.AssignedLine::orderLineId)).toList();
    }

    private static List<DeviceScopeFactPort.DeviceFact> orderedDevices(
            List<DeviceScopeFactPort.DeviceFact> devices) {
        return devices.stream().sorted(Comparator.comparing(DeviceScopeFactPort.DeviceFact::deviceId)).toList();
    }

    private static Map<Long, Long> assignmentVersions(List<DeviceScopeFactPort.DeviceFact> devices) {
        Map<Long, Long> versions = new LinkedHashMap<>();
        orderedDevices(devices).forEach(device ->
                versions.put(device.deviceId(), device.projectAssignmentVersion()));
        return Map.copyOf(versions);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record CreateDraftCommand(Long tenantId, Long projectId, Long actorUserId,
                                     String batchCode, String logisticsNo,
                                     LocalDateTime arrivedAt, String signerName) {
    }

    private record SignerSnapshot(String signerName) {
    }

    private record ExpectedScopeSnapshot(List<DeliveryScopePort.AssignedLine> deliveryLines,
                                         List<DeviceScopeFactPort.DeviceFact> devices) {
    }
}
