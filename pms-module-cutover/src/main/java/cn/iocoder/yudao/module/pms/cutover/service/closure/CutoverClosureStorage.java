package cn.iocoder.yudao.module.pms.cutover.service.closure;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverClosureDeviceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDeviceScopeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.CutoverClosureDeviceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverClosureDeviceListQuery;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverFrozenDeviceMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.CutoverTaskDeviceScopeMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.taskv2.query.CutoverTaskDeviceListQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureApplicationException.Code.*;
import static cn.iocoder.yudao.module.pms.cutover.service.closure.CutoverClosureApplicationException.Reason.*;

/** P6设备范围按闭环一次冻结；活动占用释放不改变已归档设备事实。 */
@Service
@RequiredArgsConstructor
public class CutoverClosureStorage {
    private final CutoverClosureDeviceMapper devices;
    private final CutoverTaskDeviceScopeMapper activeDevices;
    private final CutoverFrozenDeviceMapper taskDevices;

    public record Source(CutoverClosureDO closure, CutoverTaskDO task) {}
    public record Freeze(Source source, Long actorId, LocalDateTime time) {}
    private record DeviceFact(Long deviceId, Long projectId, String sn, Long assignmentVersion) {}

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void freeze(Freeze command) {
        var source = command.source(); requireIdentity(source);
        var task = source.task(); var closure = source.closure();
        var scope = activeDevices.selectActiveByTaskForUpdate(new CutoverTaskDeviceListQuery(task.getTenantId(), task.getId()));
        var facts = taskFacts(source, scope);
        int order = 0;
        for (var fact : facts) {
            var row = CutoverClosureDeviceDO.builder().tenantId(task.getTenantId()).closureId(closure.getId())
                    .deviceId(fact.deviceId()).projectId(fact.projectId()).sn(fact.sn())
                    .projectAssignmentVersion(fact.assignmentVersion()).sortOrder(order++)
                    .createUserId(command.actorId()).createTime(command.time()).build();
            if (devices.insert(row) != 1) throw corrupted("闭环冻结设备保存失败");
        }
    }

    public void requireSame(Source source) {
        requireIdentity(source);
        var task = source.task();
        var frozen = frozen(source.closure()).stream().map(value -> new DeviceFact(value.getDeviceId(),
                value.getProjectId(), value.getSn(), value.getProjectAssignmentVersion())).sorted(Comparator.comparing(DeviceFact::deviceId)).toList();
        var current = taskFacts(source, taskDevices.byTask(new CutoverTaskDeviceListQuery(task.getTenantId(), task.getId())));
        if (!frozen.equals(current)) throw new CutoverClosureApplicationException(SOURCE_STALE, DEVICE_SCOPE_STALE,
                "CUT", task.getVersion(), source.closure().getVersion(), "P6冻结设备来源已变化");
    }

    public List<CutoverClosureDeviceDO> frozen(CutoverClosureDO closure) {
        requireTenant(closure.getTenantId());
        var rows = devices.selectListByParents(CutoverClosureDeviceListQuery.builder()
                .tenantId(closure.getTenantId()).closureIds(Set.of(closure.getId())).build());
        if (rows.isEmpty()) throw corrupted("闭环缺少冻结设备范围");
        var ids = new HashSet<Long>();
        for (var row : rows) {
            if (!Objects.equals(row.getTenantId(), closure.getTenantId()) || !Objects.equals(row.getClosureId(), closure.getId())
                    || !Objects.equals(row.getProjectId(), closure.getProjectId()) || !validDevice(row.getDeviceId(), row.getSn(), row.getProjectAssignmentVersion())
                    || !ids.add(row.getDeviceId())) throw corrupted("闭环冻结设备身份损坏");
        }
        return rows;
    }

    private List<DeviceFact> taskFacts(Source source, List<CutoverTaskDeviceScopeDO> rows) {
        if (rows.isEmpty()) throw corrupted("任务缺少冻结设备范围");
        var task = source.task(); var ids = new HashSet<Long>(); var result = new ArrayList<DeviceFact>();
        for (var row : rows) {
            if (!Objects.equals(row.getTenantId(), task.getTenantId()) || !Objects.equals(row.getCutoverTaskId(), task.getId())
                    || !Objects.equals(row.getProjectId(), task.getProjectId())
                    || !validDevice(row.getDeviceId(), row.getSerialNumberSnapshot(), row.getProjectAssignmentVersion())
                    || !ids.add(row.getDeviceId())) throw corrupted("任务冻结设备身份损坏");
            result.add(new DeviceFact(row.getDeviceId(), row.getProjectId(), row.getSerialNumberSnapshot(), row.getProjectAssignmentVersion()));
        }
        return result.stream().sorted(Comparator.comparing(DeviceFact::deviceId)).toList();
    }

    private static boolean validDevice(Long id, String sn, Long version) {
        return id != null && id > 0 && sn != null && !sn.isBlank() && version != null && version >= 0;
    }
    private static void requireIdentity(Source source) {
        var task = source.task(); var closure = source.closure(); requireTenant(task.getTenantId());
        if (!Objects.equals(task.getTenantId(), closure.getTenantId()) || !Objects.equals(task.getId(), closure.getTaskId())
                || !Objects.equals(task.getProjectId(), closure.getProjectId())) throw corrupted("闭环设备所属来源不一致");
    }
    private static void requireTenant(Long tenantId) {
        if (TenantContextHolder.isIgnore() || !Objects.equals(TenantContextHolder.getTenantId(), tenantId))
            throw corrupted("闭环设备租户不一致");
    }
    private static CutoverClosureApplicationException corrupted(String message) {
        return new CutoverClosureApplicationException(OWNER_DATA_CORRUPTED, OWNER_FACT_CORRUPTED, "CUT", null, null, message);
    }
}
