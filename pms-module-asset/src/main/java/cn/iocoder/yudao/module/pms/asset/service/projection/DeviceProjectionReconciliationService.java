package cn.iocoder.yudao.module.pms.asset.service.projection;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceCustomerRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceProjectRelationshipDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.location.DeviceLocationDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.shipment.DeviceShipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceNetworkVersionDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty.DeviceWarrantyDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceAssignmentLockQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.DeviceLocationMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.location.query.CurrentDeviceLocationQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.DeviceShipmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.query.LatestDeviceShipmentQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceNetworkVersionMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyMapper;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class DeviceProjectionReconciliationService {

    private final DeviceMapper deviceMapper;
    private final DeviceShipmentMapper shipmentMapper;
    private final DeviceAssignmentMapper assignmentMapper;
    private final DeviceLocationMapper locationMapper;
    private final DeviceWarrantyMapper warrantyMapper;
    private final DeviceNetworkVersionMapper versionMapper;
    private final OperationAuditApi auditApi;

    public DeviceProjectionReconciliationService(
            DeviceMapper deviceMapper,
            DeviceShipmentMapper shipmentMapper,
            DeviceAssignmentMapper assignmentMapper,
            DeviceLocationMapper locationMapper,
            DeviceWarrantyMapper warrantyMapper,
            DeviceNetworkVersionMapper versionMapper,
            OperationAuditApi auditApi) {
        this.deviceMapper = deviceMapper;
        this.shipmentMapper = shipmentMapper;
        this.assignmentMapper = assignmentMapper;
        this.locationMapper = locationMapper;
        this.warrantyMapper = warrantyMapper;
        this.versionMapper = versionMapper;
        this.auditApi = auditApi;
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceProjectionReconciliationResult rebuild(DeviceProjectionReconciliationCommand command) {
        validate(command);
        DeviceDO device = deviceMapper.selectByTenantAndSn(command.tenantId(), command.deviceSn());
        if (device == null) {
            throw new IllegalStateException("DEVICE_NOT_EXISTS");
        }
        DeviceProjectionSources sources = loadSources(device);
        List<DeviceProjectionType> missingSources = missingSources(device, sources);
        List<DeviceProjectionType> drifts = detectDrifts(device, sources, missingSources);
        if (!drifts.isEmpty()) {
            deviceMapper.updateById(toUpdate(device, sources, drifts));
        }
        audit(command, drifts, missingSources);
        return new DeviceProjectionReconciliationResult(
                !drifts.isEmpty(),
                List.copyOf(drifts), List.copyOf(missingSources));
    }

    private DeviceProjectionSources loadSources(DeviceDO device) {
        Long tenantId = device.getTenantId();
        String deviceSn = device.getSn();
        DeviceAssignmentLockQuery assignmentQuery = new DeviceAssignmentLockQuery(tenantId, device.getId());
        return new DeviceProjectionSources(
                shipmentMapper.selectLatest(new LatestDeviceShipmentQuery(tenantId, deviceSn)),
                assignmentMapper.selectCurrentProject(assignmentQuery),
                assignmentMapper.selectCurrentCustomer(assignmentQuery),
                locationMapper.selectCurrent(new CurrentDeviceLocationQuery(tenantId, deviceSn)),
                warrantyMapper.selectByTenantAndDeviceSn(tenantId, deviceSn),
                versionMapper.selectByTenantAndSn(tenantId, deviceSn));
    }

    private List<DeviceProjectionType> missingSources(DeviceDO device, DeviceProjectionSources sources) {
        List<DeviceProjectionType> missing = new ArrayList<>();
        if (sources.shipment() == null && hasShipmentProjection(device)) missing.add(DeviceProjectionType.SHIPMENT);
        if (sources.project() == null && hasProjectProjection(device)) missing.add(DeviceProjectionType.PROJECT);
        if (sources.customer() == null && hasCustomerProjection(device)) missing.add(DeviceProjectionType.CUSTOMER);
        if (sources.location() == null && hasLocationProjection(device)) missing.add(DeviceProjectionType.LOCATION);
        if (sources.warranty() == null && hasWarrantyProjection(device)) missing.add(DeviceProjectionType.WARRANTY);
        if (sources.version() == null && hasConpProjection(device)) missing.add(DeviceProjectionType.CONP);
        return missing;
    }

    private List<DeviceProjectionType> detectDrifts(
            DeviceDO device,
            DeviceProjectionSources sources,
            List<DeviceProjectionType> missingSources) {
        List<DeviceProjectionType> drifts = new ArrayList<>();
        if (!missingSources.contains(DeviceProjectionType.SHIPMENT)
                && sources.shipment() != null && shipmentDrifted(device, sources.shipment())) {
            drifts.add(DeviceProjectionType.SHIPMENT);
        }
        if (!missingSources.contains(DeviceProjectionType.PROJECT)
                && sources.project() != null && projectDrifted(device, sources.project())) {
            drifts.add(DeviceProjectionType.PROJECT);
        }
        if (!missingSources.contains(DeviceProjectionType.CUSTOMER)
                && sources.customer() != null && customerDrifted(device, sources.customer())) {
            drifts.add(DeviceProjectionType.CUSTOMER);
        }
        if (!missingSources.contains(DeviceProjectionType.LOCATION)
                && sources.location() != null && locationDrifted(device, sources.location())) {
            drifts.add(DeviceProjectionType.LOCATION);
        }
        if (!missingSources.contains(DeviceProjectionType.WARRANTY)
                && sources.warranty() != null && warrantyDrifted(device, sources.warranty())) {
            drifts.add(DeviceProjectionType.WARRANTY);
        }
        if (!missingSources.contains(DeviceProjectionType.CONP)
                && sources.version() != null && conpDrifted(device, sources.version())) {
            drifts.add(DeviceProjectionType.CONP);
        }
        return drifts;
    }

    private DeviceDO toUpdate(
            DeviceDO device,
            DeviceProjectionSources sources,
            List<DeviceProjectionType> drifts) {
        DeviceDO update = new DeviceDO();
        update.setId(device.getId());
        update.setVersion(device.getVersion());
        if (drifts.contains(DeviceProjectionType.SHIPMENT)) applyShipment(update, sources.shipment());
        if (drifts.contains(DeviceProjectionType.PROJECT)) applyProject(update, sources.project());
        if (drifts.contains(DeviceProjectionType.CUSTOMER)) applyCustomer(update, sources.customer());
        if (drifts.contains(DeviceProjectionType.LOCATION)) applyLocation(update, sources.location());
        if (drifts.contains(DeviceProjectionType.WARRANTY)) applyWarranty(update, sources.warranty());
        if (drifts.contains(DeviceProjectionType.CONP)) applyConp(update, sources.version());
        return update;
    }

    private void audit(
            DeviceProjectionReconciliationCommand command,
            List<DeviceProjectionType> drifts,
            List<DeviceProjectionType> missingSources) {
        String resultCode = missingSources.isEmpty()
                ? drifts.isEmpty() ? "CONSISTENT" : "SUCCESS"
                : "SOURCE_MISSING";
        String operationCode = "SUCCESS".equals(resultCode)
                ? "DEVICE_PROJECTION_REBUILD"
                : "DEVICE_PROJECTION_RECONCILE";
        auditApi.record(command.tenantId(), command.actorId(), command.correlationId(), operationCode,
                "Device", command.deviceSn(), resultCode,
                Map.of("driftTypes", drifts.stream().map(Enum::name).toList(),
                        "missingSourceTypes", missingSources.stream().map(Enum::name).toList()));
    }

    private void validate(DeviceProjectionReconciliationCommand command) {
        if (command == null || command.tenantId() == null || command.deviceSn() == null
                || command.deviceSn().isBlank() || command.actorId() == null
                || command.correlationId() == null || command.correlationId().isBlank()) {
            throw new IllegalArgumentException("INVALID_RECONCILIATION_COMMAND");
        }
    }

    private boolean hasShipmentProjection(DeviceDO device) {
        return device.getShipmentRecordId() != null || device.getShipmentTime() != null
                || device.getPackageNo() != null || device.getContractNo() != null;
    }

    private boolean hasProjectProjection(DeviceDO device) {
        return device.getProjectId() != null;
    }

    private boolean hasCustomerProjection(DeviceDO device) {
        return device.getCustomerId() != null;
    }

    private boolean hasLocationProjection(DeviceDO device) {
        return device.getLocationRecordId() != null || device.getSiteId() != null
                || device.getSiteLocationId() != null || device.getLocationSnapshot() != null;
    }

    private boolean hasWarrantyProjection(DeviceDO device) {
        return device.getWarrantyStartDate() != null || device.getWarrantyEndDate() != null
                || device.getWarrantyStatus() != null;
    }

    private boolean hasConpProjection(DeviceDO device) {
        return device.getConpVersion() != null || device.getConpType() != null
                || device.getConpSeries() != null || device.getConpMark() != null;
    }

    private boolean shipmentDrifted(DeviceDO device, DeviceShipmentDO source) {
        return !Objects.equals(device.getShipmentRecordId(), source.getId())
                || !Objects.equals(device.getShipmentTime(), source.getShipmentTime())
                || !Objects.equals(device.getPackageNo(), source.getPackageNo())
                || !Objects.equals(device.getContractNo(), source.getContractNo());
    }

    private boolean projectDrifted(DeviceDO device, DeviceProjectRelationshipDO source) {
        return !Objects.equals(device.getProjectId(), source.getProjectId())
                || !Objects.equals(device.getProjectAssignmentVersion(), source.getAssignmentVersion());
    }

    private boolean customerDrifted(DeviceDO device, DeviceCustomerRelationshipDO source) {
        return !Objects.equals(device.getCustomerId(), source.getCustomerId())
                || !Objects.equals(device.getCustomerAssignmentVersion(), source.getAssignmentVersion());
    }

    private boolean locationDrifted(DeviceDO device, DeviceLocationDO source) {
        return !Objects.equals(device.getLocationRecordId(), source.getId())
                || !Objects.equals(device.getSiteId(), source.getSiteId())
                || !Objects.equals(device.getSiteLocationId(), source.getSiteLocationId())
                || !Objects.equals(device.getLocationResolutionStatus(), source.getResolutionStatus())
                || !Objects.equals(device.getLocationSnapshot(), source.getLocationSnapshot())
                || !Objects.equals(device.getLocationEffectiveFrom(), source.getEffectiveFrom());
    }

    private boolean warrantyDrifted(DeviceDO device, DeviceWarrantyDO source) {
        return !Objects.equals(device.getWarrantyStartDate(), source.getWarrantyStartDate())
                || !Objects.equals(device.getWarrantyEndDate(), source.getWarrantyEndDate())
                || !Objects.equals(device.getWarrantyStatus(), source.getWarrantyStatus());
    }

    private boolean conpDrifted(DeviceDO device, DeviceNetworkVersionDO source) {
        return !Objects.equals(device.getConpVersion(), source.getConpVersion())
                || !Objects.equals(device.getConpType(), source.getConpType())
                || !Objects.equals(device.getConpSeries(), source.getConpSeries())
                || !Objects.equals(device.getConpMark(), source.getConpMark());
    }

    private void applyShipment(DeviceDO update, DeviceShipmentDO source) {
        update.setShipmentRecordId(source.getId());
        update.setShipmentTime(source.getShipmentTime());
        update.setPackageNo(source.getPackageNo());
        update.setContractNo(source.getContractNo());
    }

    private void applyProject(DeviceDO update, DeviceProjectRelationshipDO source) {
        update.setProjectId(source.getProjectId());
        update.setProjectAssignmentVersion(source.getAssignmentVersion());
    }

    private void applyCustomer(DeviceDO update, DeviceCustomerRelationshipDO source) {
        update.setCustomerId(source.getCustomerId());
        update.setCustomerAssignmentVersion(source.getAssignmentVersion());
    }

    private void applyLocation(DeviceDO update, DeviceLocationDO source) {
        update.setLocationRecordId(source.getId());
        update.setSiteId(source.getSiteId());
        update.setSiteLocationId(source.getSiteLocationId());
        update.setLocationResolutionStatus(source.getResolutionStatus());
        update.setLocationSnapshot(source.getLocationSnapshot());
        update.setLocationEffectiveFrom(source.getEffectiveFrom());
    }

    private void applyWarranty(DeviceDO update, DeviceWarrantyDO source) {
        update.setWarrantyStartDate(source.getWarrantyStartDate());
        update.setWarrantyEndDate(source.getWarrantyEndDate());
        update.setWarrantyStatus(source.getWarrantyStatus());
    }

    private void applyConp(DeviceDO update, DeviceNetworkVersionDO source) {
        update.setConpVersion(source.getConpVersion());
        update.setConpType(source.getConpType());
        update.setConpSeries(source.getConpSeries());
        update.setConpMark(source.getConpMark());
    }

    private record DeviceProjectionSources(
            DeviceShipmentDO shipment,
            DeviceProjectRelationshipDO project,
            DeviceCustomerRelationshipDO customer,
            DeviceLocationDO location,
            DeviceWarrantyDO warranty,
            DeviceNetworkVersionDO version) {
    }
}
