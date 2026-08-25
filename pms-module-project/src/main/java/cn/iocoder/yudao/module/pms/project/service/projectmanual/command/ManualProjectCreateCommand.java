package cn.iocoder.yudao.module.pms.project.service.projectmanual.command;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;

import java.util.List;

/** 正式手工创建项目命令。 */
public record ManualProjectCreateCommand(
        ProjectMasterDO draft,
        Long orderOfficeCompanyId,
        Long orderOfficeDepartmentId,
        List<ProjectSiteCommand> sites,
        Long templateRevisionId,
        String candidateWatermark,
        String idempotencyKey,
        String requestDigest) {
}
