package cn.iocoder.yudao.module.pms.asset.service.migration;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.migration.DppmsWarrantySourceRecordsApi;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.warranty.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.warranty.query.*;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
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

/** Immutable typed warranty history and project associations, independent of current device ownership. */
@Service
@RequiredArgsConstructor
public class DppmsWarrantySourceRecordsService implements DppmsWarrantySourceRecordsApi {
    private final DeviceWarrantyServiceRecordMapper services;
    private final DeviceWarrantyRegistrationMapper registrations;
    private final DeviceWarrantyChangeRecordMapper changes;
    private final DeviceWarrantyFactProjectMapper relations;
    private final ProjectDeviceAssignmentGuardApi projectGuard;
    private final DeviceAccessScopeService scope;

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<NativeEntityMigrationRow> importRecords(Command command) {
        if (command == null) throw invalid();
        requireTenant(command.tenantId());
        if (command.actorId() == null || command.projectId() == null || command.actorId() <= 0
                || command.projectId() <= 0 || command.sourceProjectKey() == null
                || command.sourceProjectKey().isBlank() || command.sourceProjectKey().length() > 128
                || command.sourceReadAt() == null || command.services() == null
                || command.registrations() == null || command.changes() == null) throw invalid();
        // Validate all technical identities before any business row is inserted.
        for (var source : command.services()) {
            if (source == null) throw invalid();
            requireKey(source.sourceKey());
        }
        for (var source : command.registrations()) {
            if (source == null || source.sourceId() == null) throw invalid();
        }
        for (var source : command.changes()) {
            if (source == null || source.sourceId() == null || source.sourceUsername() == null) throw invalid();
        }
        var guard = projectGuard.validate(new ProjectDeviceAssignmentGuardQuery(
                command.tenantId(), command.projectId(), command.actorId()));
        if (guard == null || !guard.assignable())
            throw new IllegalArgumentException("DPPMS_WARRANTY_PROJECT_FORBIDDEN");
        var result = new ArrayList<NativeEntityMigrationRow>();
        for (var source : command.services()) result.add(importService(command, source));
        for (var source : command.registrations()) result.add(importRegistration(command, source));
        for (var source : command.changes()) result.add(importChange(command, source));
        return List.copyOf(result);
    }

    private NativeEntityMigrationRow importService(Command command, ServiceRecord source) {
        String key = source.sourceKey();
        String checksum = checksum(rawService(source));
        var row = services.selectSourceForUpdate(new WarrantyFactSourceIdentityQuery(
                command.tenantId(), "fb_service", key, checksum));
        if (row != null) {
            if (Boolean.TRUE.equals(row.getDeleted()) || !Objects.equals(row.getTenantId(), command.tenantId())
                    || !"DPPMS".equals(row.getSourceSystem()) || !"fb_service".equals(row.getSourceTable())
                    || !key.equals(row.getSourceKey()) || !checksum.equals(row.getSourceChecksum())
                    || !Objects.equals(rawService(source), rawService(row)))
                throw new IllegalStateException("DPPMS_WARRANTY_FACT_IDENTITY_CONFLICT");
        } else {
            row = new DeviceWarrantyServiceRecordDO();
            row.setId(IdWorker.getId());
            row.setTenantId(command.tenantId());
            row.setSourceSystem("DPPMS");
            row.setSourceTable("fb_service");
            row.setSourceKey(key);
            row.setSourceVersion(command.sourceVersion());
            row.setSourceReadAt(command.sourceReadAt());
            row.setSourceChecksum(checksum);
            row.setCreator(String.valueOf(command.actorId()));
            row.setUpdater(String.valueOf(command.actorId()));
            row.setSourceId(source.sourceId());
            row.setRenewalContractNo(source.renewalContractNo());
            row.setDeviceSn(source.deviceSn());
            row.setGrade(source.grade());
            row.setBeginDate(source.beginDate());
            row.setEndDate(source.endDate());
            row.setWarrantyFlag(source.warrantyFlag());
            row.setRemark(source.remark());
            row.setExtensionFlag(source.extensionFlag());
            row.setSourceState(source.sourceState());
            services.insert(row);
        }
        var relation = associate(command, "SERVICE", row.getId());
        return NativeEntityMigrationRow.mapped("warranty-services", key,
                target("DeviceWarrantyServiceRecord", "ast_device_warranty_service_record", row.getId(), "SOURCE_FACT"),
                target("DeviceWarrantyFactProject", "ast_device_warranty_fact_project", relation.getId(), "PROJECT_RELATION"));
    }

    private static ServiceRecord rawService(DeviceWarrantyServiceRecordDO row) {
        return new ServiceRecord(null, row.getSourceId(), row.getRenewalContractNo(), row.getDeviceSn(), row.getGrade(), row.getBeginDate(), row.getEndDate(), row.getWarrantyFlag(), row.getRemark(), row.getExtensionFlag(), row.getSourceState());
    }

    private static ServiceRecord rawService(ServiceRecord source) {
        return new ServiceRecord(null, source.sourceId(), source.renewalContractNo(), source.deviceSn(), source.grade(), source.beginDate(), source.endDate(), source.warrantyFlag(), source.remark(), source.extensionFlag(), source.sourceState());
    }

    private static Row rowService(DeviceWarrantyServiceRecordDO row) {
        var values = new LinkedHashMap<String, Object>();
        values.put("id", row.getSourceId());
        values.put("con_xb", row.getRenewalContractNo());
        values.put("barcode", row.getDeviceSn());
        values.put("grade", row.getGrade());
        values.put("begin_date", row.getBeginDate());
        values.put("end_date", row.getEndDate());
        values.put("warranty", row.getWarrantyFlag());
        values.put("remark", row.getRemark());
        values.put("isyb", row.getExtensionFlag());
        values.put("state", row.getSourceState());
        values.put("sourceVersion", row.getSourceVersion());
        values.put("sourceReadAt", row.getSourceReadAt());
        values.put("sourceChecksum", row.getSourceChecksum());
        return new Row(row.getId(), row.getSourceKey(), Collections.unmodifiableMap(values));
    }

    private NativeEntityMigrationRow importRegistration(Command command, Registration source) {
        String key = String.valueOf(source.sourceId());
        String checksum = checksum(source);
        var row = registrations.selectSourceForUpdate(new WarrantyFactSourceIdentityQuery(
                command.tenantId(), "warranty_info", key, checksum));
        if (row != null) {
            if (Boolean.TRUE.equals(row.getDeleted()) || !Objects.equals(row.getTenantId(), command.tenantId())
                    || !"DPPMS".equals(row.getSourceSystem()) || !"warranty_info".equals(row.getSourceTable())
                    || !key.equals(row.getSourceKey()) || !checksum.equals(row.getSourceChecksum())
                    || !Objects.equals(source, rawRegistration(row)))
                throw new IllegalStateException("DPPMS_WARRANTY_FACT_IDENTITY_CONFLICT");
        } else {
            row = new DeviceWarrantyRegistrationDO();
            row.setId(IdWorker.getId());
            row.setTenantId(command.tenantId());
            row.setSourceSystem("DPPMS");
            row.setSourceTable("warranty_info");
            row.setSourceKey(key);
            row.setSourceVersion(command.sourceVersion());
            row.setSourceReadAt(command.sourceReadAt());
            row.setSourceChecksum(checksum);
            row.setCreator(String.valueOf(command.actorId()));
            row.setUpdater(String.valueOf(command.actorId()));
            row.setSourceId(source.sourceId());
            row.setPackageSyncId(source.packageSyncId());
            row.setContractSyncId(source.contractSyncId());
            row.setDeviceSn(source.deviceSn());
            row.setOfficeCode(source.officeCode());
            row.setSourceProjectName(source.sourceProjectName());
            row.setContractNo(source.contractNo());
            row.setContractType(source.contractType());
            row.setCustomerName(source.customerName());
            row.setItemCode(source.itemCode());
            row.setItemName(source.itemName());
            row.setWarrantyLevel(source.warrantyLevel());
            row.setWarrantyStartTime(source.warrantyStartTime());
            row.setWarrantyEndTime(source.warrantyEndTime());
            row.setWarrantyLimit(source.warrantyLimit());
            registrations.insert(row);
        }
        var relation = associate(command, "REGISTRATION", row.getId());
        return NativeEntityMigrationRow.mapped("warranty-registrations", key,
                target("DeviceWarrantyRegistration", "ast_device_warranty_registration", row.getId(), "SOURCE_FACT"),
                target("DeviceWarrantyFactProject", "ast_device_warranty_fact_project", relation.getId(), "PROJECT_RELATION"));
    }

    private static Registration rawRegistration(DeviceWarrantyRegistrationDO row) {
        return new Registration(row.getSourceId(), row.getPackageSyncId(), row.getContractSyncId(), row.getDeviceSn(), row.getOfficeCode(), row.getSourceProjectName(), row.getContractNo(), row.getContractType(), row.getCustomerName(), row.getItemCode(), row.getItemName(), row.getWarrantyLevel(), row.getWarrantyStartTime(), row.getWarrantyEndTime(), row.getWarrantyLimit());
    }

    private static Row rowRegistration(DeviceWarrantyRegistrationDO row) {
        var values = new LinkedHashMap<String, Object>();
        values.put("id", row.getSourceId());
        values.put("packlistId", row.getPackageSyncId());
        values.put("contractId", row.getContractSyncId());
        values.put("barCode", row.getDeviceSn());
        values.put("officeCode", row.getOfficeCode());
        values.put("projectName", row.getSourceProjectName());
        values.put("contractNo", row.getContractNo());
        values.put("contractType", row.getContractType());
        values.put("customerName", row.getCustomerName());
        values.put("itemCode", row.getItemCode());
        values.put("itemName", row.getItemName());
        values.put("warrantyLevel", row.getWarrantyLevel());
        values.put("warrantyStartTime", row.getWarrantyStartTime());
        values.put("warrantyEndTime", row.getWarrantyEndTime());
        values.put("warrantyLimit", row.getWarrantyLimit());
        values.put("sourceVersion", row.getSourceVersion());
        values.put("sourceReadAt", row.getSourceReadAt());
        values.put("sourceChecksum", row.getSourceChecksum());
        return new Row(row.getId(), row.getSourceKey(), Collections.unmodifiableMap(values));
    }

    private NativeEntityMigrationRow importChange(Command command, Change source) {
        String key = String.valueOf(source.sourceId());
        String checksum = checksum(source);
        var row = changes.selectSourceForUpdate(new WarrantyFactSourceIdentityQuery(
                command.tenantId(), "warranty_change_logs", key, checksum));
        if (row != null) {
            if (Boolean.TRUE.equals(row.getDeleted()) || !Objects.equals(row.getTenantId(), command.tenantId())
                    || !"DPPMS".equals(row.getSourceSystem()) || !"warranty_change_logs".equals(row.getSourceTable())
                    || !key.equals(row.getSourceKey()) || !checksum.equals(row.getSourceChecksum())
                    || !Objects.equals(source, rawChange(row)))
                throw new IllegalStateException("DPPMS_WARRANTY_FACT_IDENTITY_CONFLICT");
        } else {
            row = new DeviceWarrantyChangeRecordDO();
            row.setId(IdWorker.getId());
            row.setTenantId(command.tenantId());
            row.setSourceSystem("DPPMS");
            row.setSourceTable("warranty_change_logs");
            row.setSourceKey(key);
            row.setSourceVersion(command.sourceVersion());
            row.setSourceReadAt(command.sourceReadAt());
            row.setSourceChecksum(checksum);
            row.setCreator(String.valueOf(command.actorId()));
            row.setUpdater(String.valueOf(command.actorId()));
            row.setSourceId(source.sourceId());
            row.setSourceUsername(source.sourceUsername());
            row.setUpdateType(source.updateType());
            row.setDeviceSn(source.deviceSn());
            row.setWarrantyStartTime(source.warrantyStartTime());
            row.setWarrantyEndTime(source.warrantyEndTime());
            row.setWarrantyTimes(source.warrantyTimes());
            row.setSourceUpdatedAt(source.sourceUpdatedAt());
            changes.insert(row);
        }
        var relation = associate(command, "CHANGE", row.getId());
        return NativeEntityMigrationRow.mapped("warranty-changes", key,
                target("DeviceWarrantyChangeRecord", "ast_device_warranty_change_record", row.getId(), "SOURCE_FACT"),
                target("DeviceWarrantyFactProject", "ast_device_warranty_fact_project", relation.getId(), "PROJECT_RELATION"));
    }

    private static Change rawChange(DeviceWarrantyChangeRecordDO row) {
        return new Change(row.getSourceId(), row.getSourceUsername(), row.getUpdateType(), row.getDeviceSn(), row.getWarrantyStartTime(), row.getWarrantyEndTime(), row.getWarrantyTimes(), row.getSourceUpdatedAt());
    }

    private static Row rowChange(DeviceWarrantyChangeRecordDO row) {
        var values = new LinkedHashMap<String, Object>();
        values.put("id", row.getSourceId());
        values.put("username", row.getSourceUsername());
        values.put("updateType", row.getUpdateType());
        values.put("barcode", row.getDeviceSn());
        values.put("warrantyStartTime", row.getWarrantyStartTime());
        values.put("warrantyEndTime", row.getWarrantyEndTime());
        values.put("warrantyTimes", row.getWarrantyTimes());
        values.put("updateTime", row.getSourceUpdatedAt());
        values.put("sourceVersion", row.getSourceVersion());
        values.put("sourceReadAt", row.getSourceReadAt());
        values.put("sourceChecksum", row.getSourceChecksum());
        return new Row(row.getId(), row.getSourceKey(), Collections.unmodifiableMap(values));
    }

    private DeviceWarrantyFactProjectDO associate(Command command, String factType, Long factId) {
        var relation = relations.selectProjectRelationForUpdate(new WarrantyFactProjectIdentityQuery(
                command.tenantId(), command.projectId(), factType, factId));
        if (relation != null) {
            if (Boolean.TRUE.equals(relation.getDeleted()) || !Objects.equals(relation.getTenantId(), command.tenantId())
                    || !Objects.equals(relation.getProjectId(), command.projectId())
                    || !Objects.equals(relation.getFactType(), factType)
                    || !Objects.equals(relation.getFactId(), factId)
                    || !Objects.equals(relation.getSourceProjectKey(), command.sourceProjectKey()))
                throw new IllegalStateException("DPPMS_WARRANTY_PROJECT_RELATION_CONFLICT");
            return relation;
        }
        relation = new DeviceWarrantyFactProjectDO();
        relation.setId(IdWorker.getId());
        relation.setTenantId(command.tenantId());
        relation.setProjectId(command.projectId());
        relation.setSourceProjectKey(command.sourceProjectKey());
        relation.setFactType(factType);
        relation.setFactId(factId);
        relation.setCreator(String.valueOf(command.actorId()));
        relation.setUpdater(String.valueOf(command.actorId()));
        relations.insert(relation);
        return relation;
    }

    @Override
    public Page page(Query query) {
        if (query == null) throw invalid();
        requireTenant(query.tenantId());
        if (query.actorId() == null || query.projectId() == null || query.actorId() <= 0
                || query.projectId() <= 0 || query.pageNo() < 1 || query.pageSize() < 1
                || query.pageSize() > 100 || !Set.of("warranty-services", "warranty-registrations",
                "warranty-changes").contains(query.kind() == null ? "" : query.kind())) throw invalid();
        if (!scope.visibleProjectIds(query.tenantId(), query.actorId()).contains(query.projectId()))
            return new Page(List.of(), 0L);
        var page = new WarrantyFactProjectPageQuery(query.tenantId(), query.projectId(),
                ((long) query.pageNo() - 1) * query.pageSize(), query.pageSize());
        return switch (query.kind()) {
            case "warranty-services" -> new Page(services.selectProjectPage(page).stream()
                    .map(DppmsWarrantySourceRecordsService::rowService).toList(), services.countProject(page));
            case "warranty-registrations" -> new Page(registrations.selectProjectPage(page).stream()
                    .map(DppmsWarrantySourceRecordsService::rowRegistration).toList(), registrations.countProject(page));
            case "warranty-changes" -> new Page(changes.selectProjectPage(page).stream()
                    .map(DppmsWarrantySourceRecordsService::rowChange).toList(), changes.countProject(page));
            default -> throw invalid();
        };
    }

    private static String checksum(Object source) {
        return DigestUtil.sha256Hex(JsonUtils.toJsonString(source));
    }
    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) throw invalid();
    }
    private static void requireTenant(Long tenantId) {
        if (tenantId == null || tenantId <= 0
                || !Objects.equals(tenantId, TenantContextHolder.getRequiredTenantId()))
            throw new IllegalArgumentException("DPPMS_WARRANTY_TENANT_MISMATCH");
    }
    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("DPPMS_WARRANTY_COMMAND_INVALID");
    }
    private static ExternalTargetMapping target(String type, String table, Long id, String role) {
        return new ExternalTargetMapping("AST", type, table, id, role, 0);
    }
}
