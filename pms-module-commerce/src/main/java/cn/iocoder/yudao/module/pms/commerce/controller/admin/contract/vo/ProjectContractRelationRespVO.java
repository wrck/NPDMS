package cn.iocoder.yudao.module.pms.commerce.controller.admin.contract.vo;

import cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query.ProjectContractRelationRow;
import java.time.LocalDateTime;

/** Formal relationship fields; this read does not grant access to the current contract. */
public record ProjectContractRelationRespVO(Long id, Long projectId, Long contractId, String contractNo,
        String contractName, String companyCode, String companyName,
        String relationRole, String status, LocalDateTime effectiveFrom, LocalDateTime effectiveTo,
        String creator, LocalDateTime createTime, String updater, LocalDateTime updateTime, Integer version) {
    public static ProjectContractRelationRespVO from(ProjectContractRelationRow row) {
        return new ProjectContractRelationRespVO(row.getId(), row.getProjectId(), row.getContractId(), row.getContractNo(),
                row.getContractName(), row.getCompanyCode(), row.getCompanyName(),
                row.getRelationRole(), row.getStatus(), row.getEffectiveFrom(), row.getEffectiveTo(),
                row.getCreator(), row.getCreateTime(), row.getUpdater(), row.getUpdateTime(), row.getVersion());
    }
}
