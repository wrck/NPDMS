package cn.iocoder.yudao.module.pms.engineering.service.installation;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceQueryApi;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.ProjectDeviceInstallationQuery;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.installation.InstallationDO;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.InstallationMapper;
import cn.iocoder.yudao.module.pms.engineering.dal.mysql.installation.query.ProjectInstallationAddressQuery;
import cn.iocoder.yudao.module.pms.project.api.workbinding.operation.BusinessOperationResultEvent;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.*;

/** The latest completed record identifies a current, whole-project installation result. */
@Component
@RequiredArgsConstructor
public class InstallationBusinessResultSource implements BusinessResultChangeSource, BusinessResultInventorySource {
    public static final Type TYPE = new Type("IMP", "INSTALLATION", "INSTALLATION_COMPLETED");
    private final InstallationMapper installations;
    private final DeviceQueryApi devices;

    @Override public Descriptor descriptor() { return new Descriptor(TYPE, true, true, false); }
    @Override public Query changeQuery(BusinessOperationResultEvent event) {
        if (!TYPE.ownerContext().equals(event.ownerContext()) || !TYPE.entityType().equals(event.objectType())
                || event.revisionId() != null) throw new IllegalArgumentException("RESULT_EVENT_TYPE_INVALID");
        return new Query(event.tenantId(), event.projectId(), TYPE, event.objectId(), null);
    }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public boolean declaresFormation(BusinessOperationResultEvent event) {
        if (!TYPE.resultType().equals(event.resultCode())) return false;
        var observation = read(changeQuery(event));
        return observation.result() != null && observation.result().validity() == Validity.CURRENT;
    }
    @Override @Transactional
    public Observation inspect(Query query) { return read(query); }
    @Override @Transactional(propagation = Propagation.MANDATORY)
    public Observation lockAndInspect(Query query) { return read(query); }

    private Observation read(Query query) {
        scope(query.tenantId(), query.projectId(), query.type());
        Long id = BusinessResultSource.nativeId(query.objectId() == null ? query.resultId() : query.objectId());
        if (id == null || query.resultId() != null && !Objects.equals(id, BusinessResultSource.nativeId(query.resultId())))
            throw new IllegalArgumentException("RESULT_IDENTITY_MISMATCH");
        var row = installations.selectById(id);
        if (row == null) return Observation.absent(Status.NOT_FORMED, "INSTALLATION_RESULT_NOT_FORMED");
        if (!Objects.equals(row.getTenantId(), query.tenantId()) || !Objects.equals(row.getProjectId(), query.projectId()))
            throw new IllegalArgumentException("RESULT_OWNER_SCOPE_MISMATCH");
        var inventory = devices.lockProjectInstallationDevices(new ProjectDeviceInstallationQuery(query.tenantId(), query.projectId()));
        var records = installations.selectProjectAddressRecordsForUpdate(new ProjectInstallationAddressQuery(query.tenantId(), query.projectId()));
        Map<Long, InstallationDO> current = new HashMap<>();
        for (var record : records) if (record.getEquipmentId() != null && Integer.valueOf(2).equals(record.getStatus())) current.putIfAbsent(record.getEquipmentId(), record);
        if (inventory.isEmpty()) return Observation.absent(Status.NOT_FORMED, "PROJECT_DEVICE_INSTALLATION_ADDRESS_MISSING");
        List<InstallationDO> completed = new ArrayList<>();
        for (var device : inventory) {
            var record = current.get(device.deviceId());
            if (record == null || !Integer.valueOf(2).equals(record.getStatus()) || record.getEffectiveFrom() == null
                    || record.getEffectiveTo() != null || record.getInstallLocation() == null || record.getInstallLocation().isBlank()
                    || record.getVersion() == null)
                return Observation.absent(Status.NOT_FORMED, "PROJECT_DEVICE_INSTALLATION_ADDRESS_MISSING");
            completed.add(record);
        }
        completed.sort(Comparator.comparing(InstallationDO::getId));
        var latest = completed.getLast();
        if (!Integer.valueOf(2).equals(row.getStatus()) || row.getEffectiveFrom() == null)
            return Observation.absent(Status.NOT_FORMED, "INSTALLATION_RESULT_NOT_FORMED");
        String version = cn.hutool.crypto.digest.DigestUtil.sha256Hex(completed.stream()
                .map(record -> record.getEquipmentId() + ":" + record.getId() + ":" + record.getVersion())
                .collect(java.util.stream.Collectors.joining("|")));
        return Observation.available(new Result(query.tenantId(), query.projectId(), TYPE, id.toString(), id.toString(),
                null, version, Objects.equals(latest.getId(), id) ? Validity.CURRENT : Validity.NOT_CURRENT, row.getEffectiveFrom()));
    }
    @Override @Transactional
    public InventoryPage inventory(InventoryQuery query) {
        scope(query.tenantId(), query.projectId(), query.type());
        if (query.historical()) throw new IllegalArgumentException("RESULT_HISTORY_UNSUPPORTED");
        var objects = BusinessResultInventorySource.nativeObjects(query);
        var after = BusinessResultSource.nativeId(query.after());
        var deviceIds = devices.lockProjectInstallationDevices(new ProjectDeviceInstallationQuery(query.tenantId(), query.projectId()))
                .stream().map(device -> device.deviceId()).collect(java.util.stream.Collectors.toSet());
        var records = installations.selectProjectAddressRecordsForUpdate(new ProjectInstallationAddressQuery(query.tenantId(), query.projectId()));
        var latest = records.stream().filter(row -> deviceIds.contains(row.getEquipmentId()) && Integer.valueOf(2).equals(row.getStatus()) && row.getEffectiveFrom() != null)
                .max(Comparator.comparing(InstallationDO::getId)).orElse(null);
        if (latest == null || after != null && latest.getId() <= after || objects != null && !objects.contains(latest.getId()))
            return new InventoryPage(query.after(), true, List.of());
        return new InventoryPage(latest.getId().toString(), true, List.of(read(new Query(query.tenantId(), query.projectId(), TYPE, latest.getId().toString(), null))));
    }
    private void scope(Long tenant, Long project, Type type) {
        if (!TYPE.equals(type) || !Objects.equals(tenant, TenantContextHolder.getRequiredTenantId()) || project == null || project <= 0)
            throw new IllegalArgumentException("RESULT_QUERY_SCOPE_INVALID");
    }
}
