package cn.iocoder.yudao.module.pms.asset.service.assignment;

import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceAncestorProjectionOperationDO;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment.DeviceProjectAncestorDO;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.DeviceAssignmentMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.assignment.query.DeviceAncestorProjectionWatermarkQuery;
import cn.iocoder.yudao.module.pms.asset.service.assignment.command.RebuildDeviceAncestorProjectionCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceAncestorProjectionService {

    private final DeviceAssignmentMapper assignmentMapper;

    @Transactional(rollbackFor = Exception.class)
    public boolean rebuild(RebuildDeviceAncestorProjectionCommand command) {
        validate(command);
        assignmentMapper.selectProjectionDeviceForUpdate(
                command.tenantId(), command.deviceSn());
        if (assignmentMapper.existsAncestorProjectionOperation(
                command.tenantId(), command.eventId())) {
            return false;
        }
        DeviceAncestorProjectionOperationDO latest =
                assignmentMapper.selectLatestAncestorProjectionOperation(
                        new DeviceAncestorProjectionWatermarkQuery(
                                command.tenantId(), command.deviceSn()));
        if (latest != null
                && latest.getAssignmentVersion() != null
                && (command.assignmentVersion()
                        < latest.getAssignmentVersion()
                || command.assignmentVersion()
                        .equals(latest.getAssignmentVersion())
                && latest.getTreeVersion() != null
                && command.treeVersion()
                        <= latest.getTreeVersion())) {
            return false;
        }
        assignmentMapper.deleteDeviceAncestors(command.tenantId(), command.deviceSn());
        for (Long ancestorProjectId : command.ancestorProjectIds()) {
            DeviceProjectAncestorDO ancestor = new DeviceProjectAncestorDO();
            ancestor.setTenantId(command.tenantId());
            ancestor.setDeviceSn(command.deviceSn());
            ancestor.setProjectId(command.projectId());
            ancestor.setAncestorProjectId(ancestorProjectId);
            ancestor.setTreeVersion(command.treeVersion());
            ancestor.setAssignmentVersion(command.assignmentVersion());
            assignmentMapper.insertProjectAncestor(ancestor);
        }
        DeviceAncestorProjectionOperationDO operation = new DeviceAncestorProjectionOperationDO();
        operation.setTenantId(command.tenantId());
        operation.setEventId(command.eventId());
        operation.setOperationId(command.operationId());
        operation.setDeviceSn(command.deviceSn());
        operation.setProjectId(command.projectId());
        operation.setTreeVersion(command.treeVersion());
        operation.setAssignmentVersion(command.assignmentVersion());
        assignmentMapper.insertAncestorProjectionOperation(operation);
        return true;
    }

    private void validate(RebuildDeviceAncestorProjectionCommand command) {
        if (command == null || command.tenantId() == null || command.deviceSn() == null
                || command.deviceSn().isBlank() || command.projectId() == null
                || command.ancestorProjectIds() == null
                || command.ancestorProjectIds().stream().anyMatch(value -> value == null)
                || command.treeVersion() == null || command.assignmentVersion() == null
                || command.eventId() == null || command.eventId().isBlank()
                || command.operationId() == null || command.operationId().isBlank()) {
            throw new IllegalArgumentException("设备祖先投影命令不完整");
        }
    }
}
