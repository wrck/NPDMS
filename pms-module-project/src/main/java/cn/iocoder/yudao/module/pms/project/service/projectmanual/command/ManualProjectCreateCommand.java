package cn.iocoder.yudao.module.pms.project.service.projectmanual.command;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;

/** 正式手工创建项目命令。 */
public record ManualProjectCreateCommand(
        ProjectMasterDO draft,
        String orderOfficeCompanyCode,
        String orderOfficeDepartmentCode,
        Long templateId,
        Long serviceManagerUserId,
        String idempotencyKey,
        String requestDigest) {
}
