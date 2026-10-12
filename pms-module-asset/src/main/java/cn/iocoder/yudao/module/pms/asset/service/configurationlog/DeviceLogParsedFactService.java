package cn.iocoder.yudao.module.pms.asset.service.configurationlog;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.asset.api.device.DeviceLogParsedFactApi;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.DeviceLogParsedFactDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.device.DeviceDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.*;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.DeviceParsedLogHistoryQuery;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.DeviceMapper;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/** Immutable parsed observations and a serialized, latest-log projection; no ITR event ordering. */
@Service @RequiredArgsConstructor
public class DeviceLogParsedFactService implements DeviceLogParsedFactApi {
    private final DeviceMapper devices;
    private final DeviceLogParsedFactMapper facts;
    private final cn.iocoder.yudao.module.pms.asset.service.version.DeviceSoftwareVersionMaintenanceService software;
    private final ProjectScopeApi scopes;
    private final cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.DeviceAssemblyMapper assemblies;

    @Override @Transactional(rollbackFor=Exception.class)
    public Outcome record(ParsedLog command) {
        return record(command,null,null);
    }

    /** Internal AST path: unassigned children require an effective, authorized assembly parent. */
    @Transactional(rollbackFor=Exception.class)
    public Outcome recordSlot(ParsedLog command,Long parentId,String position) {
        if (parentId==null || position==null) throw new IllegalArgumentException("插槽来源缺失");
        return record(command,parentId,position);
    }

    private Outcome record(ParsedLog command,Long parentId,String position) {
        validate(command);
        var current=scopes.resolveCurrent(new ProjectCurrentScopeQuery(command.tenantId(),command.actorId(),
                command.projectId(),ProjectScopeApi.ACTION_EDIT));
        if (current==null || current.treeVersion()==null || current.fullProjectIds()==null
                || !current.fullProjectIds().contains(command.projectId())) throw new IllegalArgumentException("日志解析项目不在当前写入范围");
        var scope=scopes.lockAndRevalidate(new ProjectScopeRevalidationQuery(command.tenantId(),command.actorId(),
                command.projectId(),ProjectScopeApi.ACTION_EDIT,current.treeVersion()));
        if (scope==null || !Objects.equals(scope.treeVersion(),current.treeVersion()))
            throw new IllegalArgumentException("项目范围已变化，请刷新后重试");
        if (scope==null || scope.fullProjectIds()==null || !scope.fullProjectIds().contains(command.projectId()))
            throw new IllegalArgumentException("日志解析项目不在当前写入范围");
        if (parentId!=null) {
            var parent=devices.selectLogDeviceForUpdate(new cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.DeviceLogDeviceLockQuery(command.tenantId(),parentId));
            if (parent==null || !Objects.equals(parent.getProjectId(),command.projectId())
                    || assemblies.selectCurrentTree(new cn.iocoder.yudao.module.pms.asset.dal.mysql.assembly.query.DeviceAssemblyTreeQuery(command.tenantId(),parent.getSn())).stream()
                    .noneMatch(edge -> parent.getSn().equalsIgnoreCase(edge.getParentDeviceSn())
                            && command.serialNumber()!=null && command.serialNumber().equalsIgnoreCase(edge.getChildDeviceSn())
                            && position.equals(edge.getPositionCode())))
                throw new IllegalArgumentException("子设备没有当前项目的有效插槽关系");
        }
        var previous=facts.byRequest(command.tenantId(),command.requestKey());
        if (previous!=null) return replay(previous,command);
        var row=toFact(command);
        var device=command.serialNumber()==null ? null : devices.selectByTenantAndSn(command.tenantId(),command.serialNumber());
        if (device==null || !allowedProject(device,command.projectId(),parentId)) {
            row.setStatus(command.serialNumber()==null ? "SERIAL_NOT_IDENTIFIED" : "DEVICE_NOT_IN_PROJECT");
            row.setVersionStatus("NOT_UPDATED");
            if (facts.insert(row)!=1) throw new IllegalStateException("设备解析记录保存失败");
            return outcome(row);
        }
        var locked=devices.selectLogDeviceForUpdate(new cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog.query.DeviceLogDeviceLockQuery(command.tenantId(),device.getId()));
        if (locked==null || !allowedProject(locked,command.projectId(),parentId)
                || !Objects.equals(locked.getSn(),device.getSn())) throw new IllegalArgumentException("设备归属已变化");
        previous=facts.byRequest(command.tenantId(),command.requestKey());
        if (previous!=null) return replay(previous,command);
        row.setDeviceId(device.getId());row.setStatus("LINKED");
        var newer=facts.history(new DeviceParsedLogHistoryQuery(command.tenantId(),device.getId())).stream()
                .filter(v -> java.util.stream.Stream.of(v.getConpVersion(),v.getBootVersion(),v.getCpldVersion(),v.getPcbVersion())
                        .anyMatch(value -> value!=null && !value.isBlank()))
                .anyMatch(v -> v.getLogReceivedAt().isAfter(command.logReceivedAt())
                        || v.getLogReceivedAt().equals(command.logReceivedAt()) && v.getLogVersion()!=null
                        && command.logVersion()!=null && v.getLogVersion()>command.logVersion());
        row.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
        row.setVersionStatus(software.recordLog(row,locked,newer));
        // Fact, named version history, current values and device projection commit atomically.
        if (facts.insert(row)!=1) throw new IllegalStateException("设备解析记录保存失败");
        return outcome(row);
    }

    private boolean allowedProject(DeviceDO device,Long projectId,Long parentId) {
        return Objects.equals(device.getProjectId(),projectId) || (parentId!=null && device.getProjectId()==null);
    }

    private DeviceLogParsedFactDO toFact(ParsedLog c) {
        var row=new DeviceLogParsedFactDO();row.setTenantId(c.tenantId());row.setProjectId(c.projectId());row.setActorId(c.actorId());
        row.setRequestKey(c.requestKey());row.setLogKey(c.logKey());row.setLogReceivedAt(c.logReceivedAt());
        row.setLogVersion(c.logVersion());row.setParserTaskId(c.parserTaskId());row.setParserVersion(c.parserVersion());row.setSourceFileId(c.sourceFileId());
        row.setSerialNumber(c.serialNumber());row.setConpVersion(c.conpVersion());row.setBootVersion(c.bootVersion());
        row.setCpldVersion(c.cpldVersion());row.setPcbVersion(c.pcbVersion());row.setCreator(c.actorId().toString());
        row.setUpdater(c.actorId().toString());return row;
    }
    private Outcome replay(DeviceLogParsedFactDO row, ParsedLog c) {
        if (!Objects.equals(row.getLogVersion(),c.logVersion()) || !Objects.equals(row.getProjectId(),c.projectId()) || !Objects.equals(row.getActorId(),c.actorId())
                || !Objects.equals(row.getLogKey(),c.logKey()) || !Objects.equals(row.getLogReceivedAt(),c.logReceivedAt())
                || !Objects.equals(row.getParserTaskId(),c.parserTaskId()) || !Objects.equals(row.getParserVersion(),c.parserVersion())
                || !Objects.equals(row.getSourceFileId(),c.sourceFileId()) || !Objects.equals(row.getSerialNumber(),c.serialNumber())
                || !Objects.equals(row.getConpVersion(),c.conpVersion()) || !Objects.equals(row.getBootVersion(),c.bootVersion())
                || !Objects.equals(row.getCpldVersion(),c.cpldVersion()) || !Objects.equals(row.getPcbVersion(),c.pcbVersion()))
            throw new IllegalArgumentException("同一解析任务不能替换设备、日志来源或版本事实");
        return outcome(row);
    }
    static Outcome outcome(DeviceLogParsedFactDO row) {
        String message=switch(row.getStatus()) {
            case "LINKED" -> "UPDATED".equals(row.getVersionStatus()) ? "已关联设备并更新在网版本"
                    : "HISTORICAL".equals(row.getVersionStatus()) ? "已关联设备；此日志为历史版本，未回退当前版本"
                    : "已关联设备；日志未识别出软件版本";
            case "DEVICE_NOT_IN_PROJECT" -> "日志序列号未匹配当前项目设备，未更新设备";
            default -> "日志未识别出唯一设备序列号，未更新设备";
        };
        return new Outcome(row.getStatus(),row.getDeviceId(),row.getSerialNumber(),row.getVersionStatus(),message);
    }
    private void validate(ParsedLog c) {
        if (c==null || c.tenantId()==null || !Objects.equals(c.tenantId(),TenantContextHolder.getTenantId())
                || c.actorId()==null || c.projectId()==null || c.logReceivedAt()==null)
            throw new IllegalArgumentException("设备解析事实来源无效");
        text(c.requestKey(),180);text(c.logKey(),180);text(c.parserTaskId(),128);
        optional(c.parserVersion(),128);optional(c.serialNumber(),128);
        optional(c.conpVersion(),255);optional(c.bootVersion(),255);optional(c.cpldVersion(),255);optional(c.pcbVersion(),255);
    }
    private void text(String value,int max) {
        if (value==null || value.isBlank()) throw new IllegalArgumentException("解析事实标识不能为空");
        optional(value,max);
    }
    private void optional(String value,int max) {
        if (value!=null && (value.isBlank() || value.length()>max || value.indexOf('\0')>=0 || value.contains("\n") || value.contains("\r")))
            throw new IllegalArgumentException("解析事实字段无效");
    }
}
