package cn.iocoder.yudao.module.pms.asset.dal.dataobject.assignment;

import lombok.Data;

@Data
public class DeviceProjectAncestorDO {

    private String deviceSn;
    private Long projectId;
    private Long ancestorProjectId;
    private Long treeVersion;
    private Long assignmentVersion;
    private Long tenantId;
}
