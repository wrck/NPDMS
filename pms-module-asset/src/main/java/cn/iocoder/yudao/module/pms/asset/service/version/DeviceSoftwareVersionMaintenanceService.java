package cn.iocoder.yudao.module.pms.asset.service.version;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.version.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.version.query.*;
import cn.iocoder.yudao.module.pms.asset.service.security.DeviceAccessScopeService;
import cn.iocoder.yudao.module.pms.platform.api.entity.*;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.*;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.asset.enums.ErrorCodeConstants.*;

/** AST owns normal software registration; legacy imports must not call this current-value writer. */
@Service
@RequiredArgsConstructor
public class DeviceSoftwareVersionMaintenanceService {
    private final DeviceMapper devices;
    private final DeviceNetworkVersionMapper currents;
    private final DeviceSoftwareVersionRecordMapper records;
    private final SoftwareVersionWriteMapper writes;
    private final ProjectScopeApi projects;
    private final DeviceAccessScopeService access;
    private final PermissionApi permissions;
    private final EntityExtensionApi extensions;

    public record Current(SoftwareVersionValues values, int version, LocalDateTime recordedAt, boolean canMaintain) {}
    public record Registration(Long recordId, boolean replayed) {}
    public record History(DeviceSoftwareVersionRecordDO record, SoftwareVersionValues previous,
                          Map<String, Object> legacyAttributes) {}

    /** Called inside the existing parsed-fact transaction after its scope and device association locks. */
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY, rollbackFor=Exception.class)
    public String recordLog(cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.DeviceLogParsedFactDO fact,
                            DeviceDO device, boolean newerLogExists) {
        requireContext(fact.getTenantId(),fact.getActorId());
        if (fact.getId()==null || fact.getProjectId()==null || fact.getLogReceivedAt()==null
                || !"LINKED".equals(fact.getStatus()) || !Objects.equals(fact.getDeviceId(),device.getId())
                || !Objects.equals(fact.getTenantId(),device.getTenantId())) throw exception(AST_SOFTWARE_VERSION_FORBIDDEN);
        if (java.util.stream.Stream.of(fact.getConpVersion(),fact.getBootVersion(),fact.getCpldVersion(),fact.getPcbVersion())
                .allMatch(value -> value==null || value.isBlank())) return "VERSION_NOT_IDENTIFIED";
        var current=writes.selectCurrentForUpdate(fact.getTenantId(),device.getSn());
        LocalDateTime now=LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        boolean adopt=!newerLogExists && SoftwareVersionLogPolicy.mayAdopt(current,fact.getLogReceivedAt());
        var values=adopt ? SoftwareVersionLogPolicy.merge(current,fact.getConpVersion(),fact.getBootVersion(),fact.getCpldVersion(),fact.getPcbVersion())
                : new SoftwareVersionValues(fact.getConpVersion(),null,null,null,fact.getBootVersion(),fact.getCpldVersion(),fact.getPcbVersion(),null,null,null);
        var record=newRecord(fact.getTenantId(),fact.getActorId(),device,values,fact.getLogReceivedAt(),now,"DEVICE_LOG");
        // Unassigned slot children inherit only the project association actually validated by the log Owner.
        record.setProjectId(fact.getProjectId());record.setParsedFactId(fact.getId());
        if (adopt) record.setPreviousRecordId(predecessor(fact.getTenantId(),fact.getActorId(),device,current,now));
        insert(record);
        if (adopt) replaceCurrent(fact.getTenantId(),fact.getActorId(),device,current,values,fact.getLogReceivedAt(),now,
                "DEVICE_LOG","SOFTWARE_RECORD:"+record.getId(),"PARSED_FACT:"+fact.getId());
        return adopt?"UPDATED":"HISTORICAL";
    }

    public Current current(Long tenant, Long actor, Long deviceId) {
        requireContext(tenant, actor);
        access.assertVisible(tenant, actor, deviceId);
        var device = devices.selectByTenantAndId(tenant, deviceId);
        if (device == null) throw exception(AST_EQUIPMENT_NOT_EXISTS);
        var row = currents.selectByTenantAndSn(tenant, device.getSn());
        boolean allowed = permissions.hasAnyPermissions(actor, "pms:device:update") && device.getProjectId() != null;
        if (allowed) {
            var scope = projects.resolveCurrent(new ProjectCurrentScopeQuery(tenant, actor, device.getProjectId(), ProjectScopeApi.ACTION_EDIT));
            allowed = scope != null && scope.fullProjectIds() != null && scope.fullProjectIds().contains(device.getProjectId());
        }
        return new Current(SoftwareVersionValues.of(row), row == null ? 0 : row.getVersion(),
                row == null ? null : row.getEffectiveFrom(), allowed);
    }

    @Transactional(rollbackFor=Exception.class)
    public Registration register(Long tenant, Long actor, Long deviceId, Long projectId,
                                 int expectedVersion, String requestKey, SoftwareVersionValues values) {
        requireContext(tenant, actor);
        validate(requestKey, values, expectedVersion);
        if (!permissions.hasAnyPermissions(actor, "pms:device:update")) throw exception(AST_SOFTWARE_VERSION_FORBIDDEN);
        lockProject(tenant, actor, projectId);
        DeviceDO device = writes.selectDeviceForUpdate(new SoftwareVersionDeviceLockQuery(tenant, deviceId));
        if (device == null || !Objects.equals(device.getProjectId(), projectId)) throw exception(AST_SOFTWARE_VERSION_FORBIDDEN);
        var replay = records.selectByTenantAndRequest(tenant, requestKey);
        if (replay != null) {
            if (!"PLATFORM_MANUAL".equals(replay.getDataSource()) || !Objects.equals(replay.getDeviceId(), deviceId)
                    || !Objects.equals(replay.getProjectId(), projectId) || !Objects.equals(replay.getCreator(), actor.toString())
                    || !Objects.equals(SoftwareVersionValues.of(replay), values)) throw exception(AST_SOFTWARE_VERSION_CONFLICT);
            return new Registration(replay.getId(), true);
        }
        var current = writes.selectCurrentForUpdate(tenant, device.getSn());
        if (expectedVersion != (current == null ? 0 : current.getVersion())) throw exception(AST_SOFTWARE_VERSION_CONFLICT);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        if (current != null && current.getEffectiveFrom() != null && current.getEffectiveFrom().isAfter(now))
            throw exception(AST_SOFTWARE_VERSION_CONFLICT);
        Long previous = predecessor(tenant, actor, device, current, now);
        var record = newRecord(tenant, actor, device, values, now, now, "PLATFORM_MANUAL");
        record.setPreviousRecordId(previous); record.setRequestKey(requestKey);
        insert(record);
        // Current source_key is VARCHAR(128); request_key has its own approved VARCHAR(180) column.
        replaceCurrent(tenant, actor, device, current, values, now, now, "PLATFORM_MANUAL", "SOFTWARE_RECORD:" + record.getId(), null);
        return new Registration(record.getId(), false);
    }

    public PageResult<History> history(Long tenant, Long actor, Long deviceId, int pageNo, int pageSize) {
        requireContext(tenant, actor);
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) throw exception(AST_SOFTWARE_VERSION_INVALID);
        access.assertVisible(tenant, actor, deviceId);
        Set<Long> visible = access.visibleProjectIds(tenant, actor);
        var page = records.selectHistoryPage(new DeviceSoftwareVersionHistoryPageQuery(tenant, deviceId, visible, pageNo, pageSize));
        List<History> rows = new ArrayList<>();
        for (var row : page.getList()) {
            SoftwareVersionValues previous = null;
            if (row.getPreviousRecordId() != null) {
                var before = records.selectById(row.getPreviousRecordId());
                if (before != null && Objects.equals(before.getTenantId(), tenant) && Objects.equals(before.getDeviceId(), deviceId)
                        && visible.contains(before.getProjectId())) previous = SoftwareVersionValues.of(before);
            }
            Map<String, Object> attributes = Map.of();
            if ("DPPMS".equals(row.getDataSource())) {
                var target = EntityDataRef.current(new EntityRef(tenant, "AST", "SoftwareVersionRecord", row.getId()));
                attributes = extensions.read(target, new EntityActor(tenant, actor, "SOFTWARE_HISTORY_READ")).fields();
            }
            rows.add(new History(row, previous, attributes));
        }
        return new PageResult<>(rows, page.getTotal());
    }

    /** Locks always follow PROJ tree -> device -> current to serialize manual and log writers. */
    void lockProject(Long tenant, Long actor, Long projectId) {
        if (projectId == null) throw exception(AST_SOFTWARE_VERSION_FORBIDDEN);
        var observed = projects.resolveCurrent(new ProjectCurrentScopeQuery(tenant, actor, projectId, ProjectScopeApi.ACTION_EDIT));
        if (observed == null || observed.treeVersion() == null || observed.fullProjectIds() == null
                || !observed.fullProjectIds().contains(projectId)) throw exception(AST_SOFTWARE_VERSION_FORBIDDEN);
        var locked = projects.lockAndRevalidate(new ProjectScopeRevalidationQuery(tenant, actor, projectId,
                ProjectScopeApi.ACTION_EDIT, observed.treeVersion()));
        if (locked == null || !Objects.equals(observed.treeVersion(), locked.treeVersion())
                || locked.fullProjectIds() == null || !locked.fullProjectIds().contains(projectId))
            throw exception(AST_SOFTWARE_VERSION_CONFLICT);
    }

    Long predecessor(Long tenant, Long actor, DeviceDO device, DeviceNetworkVersionDO current, LocalDateTime now) {
        if (current == null) return null;
        if (Set.of("PLATFORM_MANUAL","DEVICE_LOG").contains(current.getSourceSystem()==null?"":current.getSourceSystem()) && current.getSourceKey() != null
                && current.getSourceKey().startsWith("SOFTWARE_RECORD:")) {
            Long id;
            try { id=Long.valueOf(current.getSourceKey().substring("SOFTWARE_RECORD:".length())); }
            catch (NumberFormatException invalid) { throw exception(AST_SOFTWARE_VERSION_CONFLICT); }
            var before=records.selectById(id);
            if (before == null || !Objects.equals(before.getTenantId(), tenant) || !Objects.equals(before.getDeviceId(), device.getId())
                    || !Objects.equals(SoftwareVersionValues.of(before), SoftwareVersionValues.of(current)))
                throw exception(AST_SOFTWARE_VERSION_CONFLICT);
            return before.getId();
        }
        var previous = records.selectCurrentPredecessor(new CurrentSoftwareRecordQuery(tenant, device.getId(),
                "PLATFORM_MANUAL".equals(current.getSourceSystem()) ? current.getSourceKey() : null,
                current.getSourceSystem(), current.getEffectiveFrom()));
        if (previous != null && Objects.equals(previous.getTenantId(), tenant) && Objects.equals(previous.getDeviceId(), device.getId())
                && Objects.equals(SoftwareVersionValues.of(previous), SoftwareVersionValues.of(current))) return previous.getId();
        var baseline = newRecord(tenant, actor, device, SoftwareVersionValues.of(current), current.getEffectiveFrom(), now, "CURRENT_BASELINE");
        insert(baseline);
        return baseline.getId();
    }

    DeviceSoftwareVersionRecordDO newRecord(Long tenant, Long actor, DeviceDO device, SoftwareVersionValues values,
                                             LocalDateTime recordedAt, LocalDateTime now, String source) {
        var row = new DeviceSoftwareVersionRecordDO();
        row.setTenantId(tenant); row.setDeviceId(device.getId()); row.setDeviceSn(device.getSn()); row.setProjectId(device.getProjectId());
        values.copyTo(row); row.setRecordedAt(recordedAt); row.setDataSource(source);
        row.setCreator(actor.toString()); row.setUpdater(actor.toString()); row.setCreateTime(now); row.setUpdateTime(now); row.setDeleted(false);
        return row;
    }

    void insert(DeviceSoftwareVersionRecordDO row) {
        if (row.getId() == null) row.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
        if (records.insert(row) != 1) throw new IllegalStateException("软件版本历史保存失败");
    }

    void replaceCurrent(Long tenant, Long actor, DeviceDO device, DeviceNetworkVersionDO current, SoftwareVersionValues values,
                        LocalDateTime recordedAt, LocalDateTime now, String source, String sourceKey, String sourceVersion) {
        var row = new DeviceNetworkVersionDO(); row.setTenantId(tenant); row.setDeviceSn(device.getSn()); values.copyTo(row);
        row.setEffectiveFrom(recordedAt); row.setSourceSystem(source); row.setSourceKey(sourceKey); row.setSourceVersion(sourceVersion);
        row.setSourceUpdatedAt(recordedAt); row.setSyncedAt(now); row.setSyncStatus("SYNCED");
        row.setUpdater(actor.toString()); row.setUpdateTime(now);
        if (current == null) {
            row.setCreator(actor.toString()); row.setCreateTime(now); row.setVersion(0); row.setDeleted(false);
            if (currents.insert(row) != 1) throw new IllegalStateException("软件当前值保存失败");
        } else {
            row.setId(current.getId()); row.setVersion(current.getVersion());
            if (writes.replaceCurrent(row) != 1) throw exception(AST_SOFTWARE_VERSION_CONFLICT);
        }
        var projection = new DeviceDO(); projection.setId(device.getId()); projection.setTenantId(tenant); projection.setVersion(device.getVersion());
        projection.setConpVersion(values.conpVersion()); projection.setConpType(values.conpType()); projection.setConpSeries(values.conpSeries());
        projection.setConpMark(values.conpMark()); projection.setUpdater(actor.toString()); projection.setUpdateTime(now);
        if (writes.updateDeviceProjection(projection) != 1) throw exception(AST_SOFTWARE_VERSION_CONFLICT);
    }

    private void requireContext(Long tenant, Long actor) {
        if (actor == null || actor <= 0 || !Objects.equals(tenant, TenantContextHolder.getRequiredTenantId()))
            throw exception(AST_SOFTWARE_VERSION_FORBIDDEN);
    }
    private void validate(String key, SoftwareVersionValues values, int expectedVersion) {
        if (key == null || key.isBlank() || key.length() > 180 || values == null || expectedVersion < 0)
            throw exception(AST_SOFTWARE_VERSION_INVALID);
        if (List.of(values.conpVersion() == null ? "" : values.conpVersion(), values.bootVersion() == null ? "" : values.bootVersion(),
                values.cpldVersion() == null ? "" : values.cpldVersion(), values.pcbVersion() == null ? "" : values.pcbVersion())
                .stream().allMatch(String::isBlank)) throw exception(AST_SOFTWARE_VERSION_INVALID);
    }
}
