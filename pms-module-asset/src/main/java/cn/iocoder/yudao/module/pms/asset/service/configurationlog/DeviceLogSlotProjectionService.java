package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceLogParsedFactApi;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceLogSlotFacts;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceOrganizationProjectionApi;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.DeviceAssemblyMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.query.DeviceLogAssemblyConflictQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.DeviceLogDeviceLockQuery;
import cn.iocoder.yudao.module.pms.asset.service.assembly.DeviceAssemblyService;
import cn.iocoder.yudao.module.pms.asset.service.assembly.command.ApplyDeviceAssemblyCommand;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Only observed slots are applied; absent slots and all previous intervals remain intact. */
@Service @RequiredArgsConstructor
public class DeviceLogSlotProjectionService {
    private final DeviceMapper devices;
    private final DeviceAssemblyMapper relations;
    private final DeviceAssemblyService assemblies;
    private final DeviceLogParsedFactService facts;
    private final DeviceOrganizationProjectionApi organizations;

    public void prepare(Long tenantId) {
        if (!Objects.equals(tenantId,TenantContextHolder.getTenantId())) throw new IllegalArgumentException("插槽租户无效");
        relations.acquireGraph(new cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.query.DeviceAssemblyTenantLockQuery(tenantId));
    }

    @Transactional(rollbackFor=Exception.class)
    public void record(DeviceLogParsedFactApi.ParsedLog parent,DeviceLogSlotFacts slots,
                       DeviceLogParsedFactApi.Outcome association) {
        if (slots.devices().isEmpty()) return;
        if (parent.tenantId()==null || !Objects.equals(parent.tenantId(),TenantContextHolder.getTenantId()))
            throw new IllegalArgumentException("插槽日志租户无效");
        if (association==null || !"LINKED".equals(association.status()) || "HISTORICAL".equals(association.versionStatus()))
            return;
        prepare(parent.tenantId());
        var root=devices.selectLogDeviceForUpdate(new DeviceLogDeviceLockQuery(parent.tenantId(),association.deviceId()));
        if (root==null || !Objects.equals(root.getProjectId(),parent.projectId())
                || !root.getSn().equalsIgnoreCase(parent.serialNumber()))
            throw new IllegalArgumentException("插槽整机归属已变化");
        // Stable child order plus device/edge locks serializes overlapping assemblies. Deadlocks roll back the whole log.
        for (var slot:slots.devices().stream().sorted(Comparator.comparing(s -> s.serialNumber().toUpperCase(Locale.ROOT))).toList()) {
            if (root.getSn().equalsIgnoreCase(slot.serialNumber())) throw new IllegalArgumentException("插槽子设备不能是整机自身");
            var child=devices.selectByTenantAndSn(parent.tenantId(),slot.serialNumber());
            if (child==null) {
                child=new DeviceDO();child.setId(IdWorker.getId());child.setTenantId(parent.tenantId());
                child.setSn(slot.serialNumber());child.setName(slot.model()==null ? slot.serialNumber() : slot.model());
                child.setProductModel(slot.model());child.setVersion(0L);
                child.setStatus(cn.iocoder.yudao.module.pms.asset.enums.DeviceArchiveStatusEnum.IN_STOCK);
                child.setSourceSystem("DEVICE_LOG");child.setSourceKey("ast-file:"+parent.sourceFileId()+":slot:"+slot.slot());
                child.setSourceVersion(parent.parserTaskId());child.setSourceUpdatedAt(parent.logReceivedAt());
                child.setSyncStatus("PENDING_RECONCILIATION");
                child.setCreator(parent.actorId().toString());child.setUpdater(parent.actorId().toString());
                if (devices.insert(child)!=1) throw new IllegalStateException("日志来源子设备建立失败");
                organizations.refresh(new DeviceOrganizationProjectionApi.Refresh(parent.tenantId(),Set.of(child.getId()),Set.of(),Set.of()));
            }
            child=lockOwned(parent,child);
            String position="SLOT_"+slot.slot();
            var conflicts=relations.lockLogConflicts(new DeviceLogAssemblyConflictQuery(parent.tenantId(),root.getSn(),child.getSn(),position));
            for (var edge:conflicts) {
                if (edge.getEffectiveFrom().isAfter(parent.logReceivedAt()))
                    throw new IllegalArgumentException("日志早于现有装配关系，不能回退装配历史");
                // Replacing another parent's edge also requires that parent's project ownership to be compatible.
                var oldParent=devices.selectByTenantAndSn(parent.tenantId(),edge.getParentDeviceSn());
                var oldChild=devices.selectByTenantAndSn(parent.tenantId(),edge.getChildDeviceSn());
                if (oldParent==null || oldChild==null) throw new IllegalArgumentException("现有装配设备主档缺失");
                lockOwned(parent,oldParent);lockOwned(parent,oldChild);
            }
            assemblies.apply(new ApplyDeviceAssemblyCommand(parent.tenantId(),root.getSn(),child.getSn(),position,"SLOT",
                    parent.logReceivedAt(),"ast-file:"+parent.sourceFileId(),"DEVICE_LOG",
                    "ast-file:"+parent.sourceFileId()+":slot:"+slot.slot(),parent.parserTaskId()));
            // Parent archive counters are not comparable with child/direct logs; use evidence receipt time for children.
            facts.recordSlot(new DeviceLogParsedFactApi.ParsedLog(parent.tenantId(),parent.actorId(),parent.projectId(),
                    parent.requestKey()+":slot:"+slot.slot(),"ast-file:"+parent.sourceFileId()+":slot:"+slot.slot()
                    +(slot.softwareInherited() ? ":software-parent" : ":software-child")
                    +(slot.basicBootloaderVersion()==null ? ":boot-parent" : ":boot-child")
                    +(slot.cpldVersion()==null ? ":cpld-parent" : ":cpld-child")
                    +(slot.pcbVersion()==null ? ":pcb-parent" : ":pcb-child"),parent.logReceivedAt(),
                    parent.parserTaskId(),parent.parserVersion(),parent.sourceFileId(),child.getSn(),
                    slot.effectiveSoftware(parent.conpVersion()),slot.effectiveBoot(parent.bootVersion()),
                    slot.effectiveCpld(parent.cpldVersion()),slot.effectivePcb(parent.pcbVersion()),null),
                    root.getId(),position);
        }
    }

    private DeviceDO lockOwned(DeviceLogParsedFactApi.ParsedLog parent,DeviceDO device) {
        var locked=devices.selectLogDeviceForUpdate(new DeviceLogDeviceLockQuery(parent.tenantId(),device.getId()));
        if (locked==null || !Objects.equals(locked.getSn(),device.getSn())
                || (locked.getProjectId()!=null && !Objects.equals(locked.getProjectId(),parent.projectId())))
            throw new IllegalArgumentException("插槽设备属于其他项目或归属已变化");
        return locked;
    }
}
