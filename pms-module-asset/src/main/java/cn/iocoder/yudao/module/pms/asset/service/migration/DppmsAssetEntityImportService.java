package cn.iocoder.yudao.module.pms.asset.service.migration;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsAssetEntityImportApi;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty.DeviceWarrantyRecordDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceAssignmentLockQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMigrationIdentityMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.DeviceShipmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.query.DeviceShipmentSourceQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.shipment.query.LatestDeviceShipmentQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.DeviceNetworkVersionSourceQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.DeviceWarrantyRecordMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.query.DeviceWarrantySourceQuery;
import cn.iocoder.yudao.module.pms.asset.service.assignment.DeviceProjectAssignmentService;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.AssignDeviceProjectCommand;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.ExternalTargetMapping;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.NativeEntityMigrationRow;
import cn.iocoder.yudao.module.pms.project.api.reference.ProjectDeviceAssignmentGuardApi;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardQuery;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.shipment.DeviceShipmentDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.DeviceFactoryVersionDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.DeviceFactoryVersionMapper;
import static cn.iocoder.yudao.module.pms.platform.support.migration.NativeFieldEnrichment.*;

/** AST owns all writes; existing device assignments and later software facts are never overwritten. */
@Service
@RequiredArgsConstructor
public class DppmsAssetEntityImportService implements DppmsAssetEntityImportApi {
    private final DeviceMapper devices;
    private final DeviceMigrationIdentityMapper identities;
    private final DeviceShipmentMapper shipments;
    private final DeviceAssignmentMapper relationships;
    private final DeviceProjectAssignmentService assignments;
    private final DeviceWarrantyRecordMapper warranties;
    private final ProjectDeviceAssignmentGuardApi projectGuard;
    private final DeviceFactoryVersionMapper factories;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<NativeEntityMigrationRow> importEntities(Command command) {
        if (!Objects.equals(command.tenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw new IllegalArgumentException("DPPMS_NATIVE_TENANT_MISMATCH");
        }
        var guard = projectGuard.validate(new ProjectDeviceAssignmentGuardQuery(
                command.tenantId(), command.projectId(), command.actorId()));
        if (guard == null || !guard.assignable()) throw new IllegalArgumentException("DPPMS_NATIVE_PROJECT_FORBIDDEN");
        List<NativeEntityMigrationRow> result = new ArrayList<>();
        if (!command.software().isEmpty()) throw new IllegalArgumentException("DPPMS_SOFTWARE_REQUIRES_FORMAL_HISTORY_OWNER");
        for (var source : command.shipments()) result.add(importShipment(command, source));
        for (var source : command.warranties()) {
            if (!hasCurrentProject(command,source.deviceSn())) {
                result.add(NativeEntityMigrationRow.retained("warranties", source.sourceKey(), "DEVICE_NOT_RECONCILED"));
                continue;
            }
            result.add(importWarranty(command, source));
        }
        for (var source : command.factoryVersions()) {
            if (!hasCurrentProject(command,source.deviceSn())) {
                result.add(NativeEntityMigrationRow.retained("factory-versions",source.sourceKey(),"DEVICE_NOT_RECONCILED"));
            } else result.add(importFactory(command,source));
        }
        return List.copyOf(result);
    }

    private NativeEntityMigrationRow importShipment(Command command, Shipment source) {
        var shipment = shipments.selectBySourceForUpdate(new DeviceShipmentSourceQuery(command.tenantId(), "DPPMS", source.sourceKey()));
        if (shipment == null || !Objects.equals(shipment.getDeviceSn(), source.deviceSn())
                || !sameContract(shipment.getContractNo(), command.contractNo())) {
            return NativeEntityMigrationRow.retained("shipment-facts", source.sourceKey(), "SHIPMENT_SOURCE_IDENTITY_UNRESOLVED");
        }
        DeviceShipmentDO details = source.details()==null ? new DeviceShipmentDO()
                : JsonUtils.parseObject(JsonUtils.toJsonString(source.details()),DeviceShipmentDO.class);
        if (source.productDetails()!=null) {
            // Product labels belong to the official product directory, keyed by tenant and code.
            // The original shipment update clock has an existing formal field.
            details.setSourceUpdatedAt(source.productDetails().sourceUpdatedTime());
        }
        if (!compatible(shipment,details,SHIPMENT_FIELDS))
            return NativeEntityMigrationRow.retained("shipment-facts",source.sourceKey(),"SHIPMENT_SOURCE_CONTENT_CHANGED");
        var device = identities.selectIdentityForUpdate(new DeviceMigrationIdentityMapper.DeviceIdentityQuery(command.tenantId(), source.deviceSn()));
        if (device != null && Boolean.TRUE.equals(device.getDeleted())) {
            return NativeEntityMigrationRow.retained("shipment-facts", source.sourceKey(), "TARGET_IDENTITY_CONFLICT");
        }
        if (device != null) {
            if (device.getProjectId() != null
                    ? !Objects.equals(device.getProjectId(),command.projectId())
                    : !sameContract(device.getContractNo(),command.contractNo())) {
                return NativeEntityMigrationRow.retained("shipment-facts", source.sourceKey(), "DEVICE_CURRENT_OWNER_CONFLICT");
            }
        } else {
            var latest = shipments.selectLatest(new LatestDeviceShipmentQuery(command.tenantId(), source.deviceSn()));
            if (latest == null || !sameContract(latest.getContractNo(), command.contractNo())) {
                return NativeEntityMigrationRow.retained("shipment-facts", source.sourceKey(), "LATER_SHIPMENT_CONFLICT");
            }
            device = new DeviceDO();
            device.setId(IdWorker.getId()); device.setTenantId(command.tenantId()); device.setSn(source.deviceSn());
            device.setName(source.productName()); device.setProductCode(source.productCode());
            device.setProductModel(source.productModel()); device.setProductName(source.productName());
            device.setShipmentTime(latest.getShipmentTime()); device.setPackageNo(latest.getPackageNo());
            device.setContractNo(latest.getContractNo()); device.setShipmentRecordId(latest.getId());
            device.setProjectAssignmentVersion(0L); device.setCustomerAssignmentVersion(0L);
            device.setStatus("IN_STOCK"); device.setVersion(0L); device.setSourceSystem("DPPMS");
            device.setSourceKey("SN:" + source.deviceSn()); device.setSourceVersion(command.sourceVersion());
            device.setSyncedAt(command.extractedAt()); device.setSyncStatus("SUCCESS");
            device.setRemark("DPPMS项目资料迁移；未推断安装或验收完成");
            devices.insert(device);
        }
        if (device.getProjectId() == null) {
            String key = "DPPMS-NATIVE:" + command.projectId() + ":" + source.sourceKey();
            assignments.assign(new AssignDeviceProjectCommand(command.tenantId(), device.getId(), command.projectId(),
                    device.getProjectAssignmentVersion(), "DPPMS来源关联资料迁移",
                    key, DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(command.projectId(), source.deviceSn()))),
                    command.actorId(), "DPPMS-NATIVE:" + command.snapshotId(), command.extractedAt()));
        }
        var relation = relationships.selectCurrentProject(new DeviceAssignmentLockQuery(command.tenantId(), device.getId()));
        if (relation == null || !Objects.equals(relation.getProjectId(), command.projectId())) {
            throw new IllegalStateException("DPPMS_NATIVE_RELATION_WRITE_FAILED");
        }
        if (fillMissing(shipment,details,SHIPMENT_FIELDS) && shipments.updateById(shipment)!=1)
            throw new IllegalStateException("DPPMS_NATIVE_SHIPMENT_CONCURRENT_CHANGE");
        // Device auxiliary serials belong to the currently selected shipment; old shipments remain history.
        if (Objects.equals(device.getShipmentRecordId(),shipment.getId())) {
            var extra = new DeviceDO();
            extra.setInternalSerialNo(details.getInternalSerialNo());
            extra.setSecondarySn(details.getSecondarySn()); extra.setSecondaryItem(details.getSecondaryItem());
            if (compatible(device,extra,List.of("internalSerialNo","secondarySn","secondaryItem"))
                    && fillMissing(device,extra,List.of("internalSerialNo","secondarySn","secondaryItem"))
                    && devices.updateById(device)!=1) throw new IllegalStateException("DPPMS_NATIVE_DEVICE_CONCURRENT_CHANGE");
        }
        return NativeEntityMigrationRow.mapped("shipment-facts", source.sourceKey(),
                target("Device", "ast_device", device.getId(), "DEVICE"),
                target("DeviceShipment", "ast_device_shipment", shipment.getId(), "SHIPMENT"),
                target("DeviceProjectRelationship", "ast_device_project_relationship", relation.getId(), "PROJECT_RELATION"));
    }

    private boolean hasCurrentProject(Command command,String deviceSn) {
        var device=identities.selectIdentityForUpdate(new DeviceMigrationIdentityMapper.DeviceIdentityQuery(command.tenantId(),deviceSn));
        return device!=null&&!Boolean.TRUE.equals(device.getDeleted())&&Objects.equals(device.getProjectId(),command.projectId());
    }

    private NativeEntityMigrationRow importWarranty(Command command, Warranty source) {
        String key = "view_warranty:" + source.sourceKey();
        var row = warranties.selectSourceIdentity(new DeviceWarrantySourceQuery(command.tenantId(), "DPPMS", key));
        if (row != null && Boolean.TRUE.equals(row.getDeleted())) {
            return NativeEntityMigrationRow.retained("warranties", source.sourceKey(), "TARGET_IDENTITY_CONFLICT");
        }
        if (row != null && (!Objects.equals(row.getDeviceSn(), source.deviceSn())
                || !Objects.equals(row.getWarrantyStartDate(), source.startDate())
                || !Objects.equals(row.getWarrantyEndDate(), source.endDate())
                || !Objects.equals(row.getWarrantyMonths(), source.months())
                || !Objects.equals(row.getWarrantyGrade(), source.grade())
                || !Objects.equals(row.getWarrantyContractNo(), source.contractNo()))) {
            return NativeEntityMigrationRow.retained("warranties", source.sourceKey(), "WARRANTY_SOURCE_CONTENT_CHANGED");
        }
        var details = source.details()==null ? new DeviceWarrantyRecordDO()
                : JsonUtils.parseObject(JsonUtils.toJsonString(source.details()),DeviceWarrantyRecordDO.class);
        if (row!=null && !compatible(row,details,WARRANTY_FIELDS))
            return NativeEntityMigrationRow.retained("warranties",source.sourceKey(),"WARRANTY_SOURCE_CONTENT_CHANGED");
        if (row == null) {
            row = new DeviceWarrantyRecordDO(); row.setId(IdWorker.getId()); row.setTenantId(command.tenantId());
            row.setDeviceSn(source.deviceSn()); row.setWarrantyStartDate(source.startDate());
            row.setWarrantyEndDate(source.endDate()); row.setWarrantyMonths(source.months());
            row.setWarrantyGrade(source.grade()); row.setWarrantyContractNo(source.contractNo());
            row.setSourceSystem("DPPMS"); row.setSourceKey(key); row.setSourceVersion(command.sourceVersion());
            row.setRemark("DPPMS历史保修事实；不生成续保或当前有效状态"); row.setVersion(0);
            fillMissing(row,details,WARRANTY_FIELDS);
            warranties.insert(row);
        }
        else if (fillMissing(row,details,WARRANTY_FIELDS) && warranties.updateById(row)!=1)
            throw new IllegalStateException("DPPMS_NATIVE_WARRANTY_CONCURRENT_CHANGE");
        return NativeEntityMigrationRow.mapped("warranties", source.sourceKey(),
                target("DeviceWarrantyRecord", "ast_device_warranty_record", row.getId(), "WARRANTY_RECORD"));
    }

    private static final List<String> SHIPMENT_FIELDS = List.of("sourceUpdatedAt","legacyPackageKey","rmaRelatedSn","rmaMarked","orderNo","lineNo","productCode","secondarySn","secondaryItem","internalSerialNo","isRma","profitCenter","soleAgentSuffix","syncTime","uuid","warrantyStartDate","warrantyMonths","rmaNo","relatedDeviceSn");
    private static final List<String> WARRANTY_FIELDS = List.of("combinedDeviceSn","productCode","productName","productDescription","gradeName","shipmentTime","warrantyFlag","sourceWarrantyStartTime","sourceWarrantyEndTime","originalWarrantyStartTime","originalWarrantyEndTime","remainingDaysAtSource","originalRemainingDaysAtSource","contractType","contractTypeName","projectName","customerName","finalCustomerName","agentName","officeCode","officeName","marketCode","marketName","systemCode","systemName","secondarySn","secondaryItem","secondaryProductName","secondaryProductDescription","sourceSyncTime","sourceShipmentKey");
    private NativeEntityMigrationRow importFactory(Command command,FactoryVersion source) {
        var query = new DeviceFactoryVersionMapper.IdentityQuery(command.tenantId(),source.deviceSn());
        var row = factories.selectIdentity(query);
        if (row!=null && (Boolean.TRUE.equals(row.getDeleted()) || !"DPPMS".equals(row.getSourceSystem())
                || !Objects.equals(row.getSourceKey(),source.sourceKey())))
            return NativeEntityMigrationRow.retained("factory-versions",source.sourceKey(),"TARGET_IDENTITY_CONFLICT");
        var incoming = new DeviceFactoryVersionDO(); incoming.setConpVersion(source.conpVersion());
        incoming.setBootVersion(source.bootVersion()); incoming.setCpldVersion(source.cpldVersion());
        incoming.setPcbVersion(source.pcbVersion());
        var fields = List.of("conpVersion","bootVersion","cpldVersion","pcbVersion");
        if (row!=null && !compatible(row,incoming,fields))
            return NativeEntityMigrationRow.retained("factory-versions",source.sourceKey(),"FACTORY_SOURCE_CONTENT_CHANGED");
        if (row==null) {
            row=incoming;row.setId(IdWorker.getId()); row.setTenantId(command.tenantId());
            row.setDeviceSn(source.deviceSn());row.setSourceSystem("DPPMS");row.setSourceKey(source.sourceKey());
            row.setSourceVersion(command.sourceVersion());row.setSyncedAt(command.extractedAt());
            row.setSyncStatus("SUCCESS");row.setVersion(0);factories.insert(row);
        } else if (fillMissing(row,incoming,fields) && factories.updateById(row)!=1)
            throw new IllegalStateException("DPPMS_NATIVE_FACTORY_CONCURRENT_CHANGE");
        return NativeEntityMigrationRow.mapped("factory-versions",source.sourceKey(),
                target("DeviceFactoryVersion","ast_device_factory_version",row.getId(),"FACTORY_VERSION"));
    }

    private static ExternalTargetMapping target(String type, String table, Long id, String role) {
        return new ExternalTargetMapping("AST", type, table, id, role, 0);
    }

    static boolean sameContract(String left, String right) {
        return left != null && right != null && left.replaceFirst("-[CL]$", "").equals(right.replaceFirst("-[CL]$", ""));
    }
}
