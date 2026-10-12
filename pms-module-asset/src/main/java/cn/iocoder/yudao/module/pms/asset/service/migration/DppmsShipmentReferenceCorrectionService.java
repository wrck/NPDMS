package cn.iocoder.yudao.module.pms.asset.service.migration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsShipmentReferenceCorrectionApi;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.query.*;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.ObjectNode;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DppmsShipmentReferenceCorrectionService implements DppmsShipmentReferenceCorrectionApi {
    private final ShipmentReferenceCorrectionMapper mapper;
    private final DeviceShipmentMapper shipments;
    private final ProjectDeviceAssignmentGuardApi projects;

    @Override
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public List<Baseline> preview(Command command) {
        validate(command);
        var fields=JsonUtils.getObjectMapper().rebuild().changeDefaultPropertyInclusion(ignored ->
                com.fasterxml.jackson.annotation.JsonInclude.Value.construct(
                        com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS,
                        com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)).build();
        var result=new ArrayList<Baseline>();
        for(var reference:command.references()) {
            var shipment=mapper.selectShipmentForUpdate(new ShipmentReferenceRecordQuery(command.tenantId(),reference.shipmentId()));
            var device=mapper.selectDeviceForUpdate(new ShipmentReferenceRecordQuery(command.tenantId(),reference.deviceId()));
            if(shipment==null || device==null || Boolean.TRUE.equals(shipment.getDeleted()) || Boolean.TRUE.equals(device.getDeleted())
                    || !Objects.equals(shipment.getTenantId(),command.tenantId()) || !Objects.equals(device.getTenantId(),command.tenantId())
                    || !Objects.equals(shipment.getId(),reference.shipmentId()) || !Objects.equals(device.getId(),reference.deviceId())
                    || !Objects.equals(device.getProjectId(),command.projectId()) || !"DPPMS".equals(shipment.getSourceSystem())
                    || !Objects.equals(shipment.getSourceKey(),reference.sourceKey()) || shipment.getDeviceSn()==null || device.getSn()==null
                    || !shipment.getDeviceSn().equalsIgnoreCase(reference.deviceSn()) || !device.getSn().equalsIgnoreCase(reference.deviceSn())
                    || !Objects.equals(shipment.getOrderNo(),reference.orderNo()) || !Objects.equals(shipment.getLineNo(),reference.originalLineNo())
                    || shipment.getVersion()==null || device.getVersion()==null) fail("IDENTITY_CHANGED");
            var latest=shipments.selectLatest(new LatestDeviceShipmentQuery(command.tenantId(),reference.deviceSn()));
            if(latest==null || !Objects.equals(latest.getId(),shipment.getId())
                    || !Objects.equals(device.getShipmentTime(),shipment.getShipmentTime())
                    || !Objects.equals(device.getPackageNo(),shipment.getPackageNo())
                    || !Objects.equals(device.getContractNo(),shipment.getContractNo())) fail("LATEST_PROJECTION_CHANGED");
            if(shipment.getOrderLineId()!=null && !Objects.equals(shipment.getOrderLineId(),reference.orderLineId()))
                fail("ORDER_LINE_CONFLICT");
            if(!Objects.equals(device.getShipmentRecordId(),shipment.getId()) && device.getShipmentRecordId()!=null
                    && mapper.selectShipmentForUpdate(new ShipmentReferenceRecordQuery(command.tenantId(),device.getShipmentRecordId()))!=null)
                fail("EXISTING_SHIPMENT_REFERENCE_CONFLICT");
            result.add(new Baseline(reference,fields.writeValueAsString(shipment),fields.writeValueAsString(device)));
        }
        return List.copyOf(result);
    }

    @Override
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public void correct(Command command,List<Baseline> baselines) {
        requireBaselines(command,baselines);
        var actual=preview(command);
        // Lock and compare every record before the first write; historical audit values remain untouched.
        for(int i=0;i<actual.size();i++) {
            if(!same(actual.get(i).shipmentFieldsJson(),baselines.get(i).shipmentFieldsJson())
                    || !same(actual.get(i).deviceFieldsJson(),baselines.get(i).deviceFieldsJson())) fail("TARGET_CHANGED");
        }
        for(var baseline:baselines) {
            var reference=baseline.reference();
            var shipment=JsonUtils.parseObject(baseline.shipmentFieldsJson(),cn.iocoder.yudao.module.pms.asset.dal.dataobject.shipment.DeviceShipmentDO.class);
            var device=JsonUtils.parseObject(baseline.deviceFieldsJson(),cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO.class);
            if(shipment.getOrderLineId()==null && mapper.fillOrderLine(new ShipmentReferenceCorrectionMapper.ShipmentWrite(
                    command.tenantId(),reference.shipmentId(),reference.orderLineId(),shipment.getVersion()))!=1) fail("ORDER_LINE_WRITE_FAILED");
            if(!Objects.equals(device.getShipmentRecordId(),reference.shipmentId()) && mapper.repairDeviceShipment(
                    new ShipmentReferenceCorrectionMapper.DeviceWrite(command.tenantId(),reference.deviceId(),reference.shipmentId(),
                            device.getShipmentRecordId(),device.getVersion()))!=1) fail("DEVICE_REFERENCE_WRITE_FAILED");
        }
        verify(command,baselines);
    }

    @Override
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public void verify(Command command,List<Baseline> baselines) {
        requireBaselines(command,baselines);
        var actual=preview(command);
        for(int i=0;i<actual.size();i++) {
            var expected=baselines.get(i);
            ObjectNode shipment=object(expected.shipmentFieldsJson()),device=object(expected.deviceFieldsJson());
            shipment.set("orderLineId",JsonUtils.parseTree(JsonUtils.toJsonString(expected.reference().orderLineId())));
            device.set("shipmentRecordId",JsonUtils.parseTree(JsonUtils.toJsonString(expected.reference().shipmentId())));
            if(!shipment.equals(JsonUtils.parseTree(actual.get(i).shipmentFieldsJson()))
                    || !device.equals(JsonUtils.parseTree(actual.get(i).deviceFieldsJson()))) fail("APPLIED_TARGET_CHANGED");
        }
    }

    private void validate(Command command) {
        if(command==null || !Objects.equals(command.tenantId(),TenantContextHolder.getRequiredTenantId())
                || command.actorId()==null || command.actorId()<=0 || command.projectId()==null || command.projectId()<=0
                || command.references()==null || command.references().isEmpty() || command.references().size()>2000) fail("COMMAND_INVALID");
        var access=projects.validate(new ProjectDeviceAssignmentGuardQuery(command.tenantId(),command.projectId(),command.actorId()));
        if(access==null || !access.assignable()) fail("PROJECT_FORBIDDEN");
        var ids=new HashSet<Long>();var devices=new HashSet<Long>();var keys=new HashSet<String>();
        for(var reference:command.references()) {
            if(reference==null || reference.sourceKey()==null || reference.sourceKey().isBlank() || !keys.add(reference.sourceKey())
                    || reference.shipmentId()==null || reference.shipmentId()<=0 || !ids.add(reference.shipmentId())
                    || reference.deviceId()==null || reference.deviceId()<=0 || !devices.add(reference.deviceId())
                    || reference.orderLineId()==null || reference.orderLineId()<=0 || reference.deviceSn()==null || reference.deviceSn().isBlank()
                    || reference.orderNo()==null || reference.orderNo().isBlank() || reference.originalLineNo()==null
                    || reference.originalLineNo().isBlank()) fail("REFERENCE_INVALID");
        }
    }
    private static void requireBaselines(Command command,List<Baseline> baselines) {
        if(command==null || baselines==null || baselines.isEmpty() || baselines.stream().anyMatch(Objects::isNull)
                || !Objects.equals(command.references(),baselines.stream().map(Baseline::reference).toList())) fail("BASELINE_IDENTITY_CHANGED");
        for(var row:baselines) {object(row.shipmentFieldsJson());object(row.deviceFieldsJson());}
    }
    private static ObjectNode object(String json) {
        if(json==null || !(JsonUtils.parseTree(json) instanceof ObjectNode node)) {
            fail("BASELINE_INVALID");throw new IllegalArgumentException();
        }
        return node;
    }
    private static boolean same(String left,String right) {return object(left).equals(object(right));}
    private static void fail(String reason) {throw new IllegalArgumentException("DPPMS_SHIPMENT_REFERENCE_"+reason);}
}
