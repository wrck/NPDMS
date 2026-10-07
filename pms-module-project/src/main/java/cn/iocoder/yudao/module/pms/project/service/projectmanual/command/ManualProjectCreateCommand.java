package cn.iocoder.yudao.module.pms.project.service.projectmanual.command;

import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;

import java.util.List;

/** 正式手工创建项目命令。contractId 非空时服务端按合同主档链解析CRM权威字段并绑定商务来源。 */
public record ManualProjectCreateCommand(
        ProjectMasterDO draft,
        Long orderOfficeCompanyId,
        Long orderOfficeDepartmentId,
        List<ProjectSiteCommand> sites,
        Long templateRevisionId,
        String candidateWatermark,
        Long serviceManagerUserId,
        Long contractId,
        String idempotencyKey,
        String requestDigest,
        Long salesOrderId,
        String sourceFingerprint) {
    public ManualProjectCreateCommand(ProjectMasterDO draft, Long companyId, Long departmentId,
            List<ProjectSiteCommand> sites, Long revisionId, String watermark, Long managerId, Long contractId,
            String key, String digest) {
        this(draft, companyId, departmentId, sites, revisionId, watermark, managerId, contractId, key, digest, null, null);
    }
}
