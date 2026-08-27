package cn.iocoder.yudao.module.pms.project.api.reference;

import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardQuery;
import cn.iocoder.yudao.module.pms.project.api.reference.dto.ProjectDeviceAssignmentGuardResult;

public interface ProjectDeviceAssignmentGuardApi {

    ProjectDeviceAssignmentGuardResult validate(ProjectDeviceAssignmentGuardQuery query);
}
