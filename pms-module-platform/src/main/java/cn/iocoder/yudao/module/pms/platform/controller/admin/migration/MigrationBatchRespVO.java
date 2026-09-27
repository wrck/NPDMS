package cn.iocoder.yudao.module.pms.platform.controller.admin.migration;

import cn.iocoder.yudao.module.pms.platform.dal.dataobject.migration.MigrationBatchDO;
import lombok.Data;

import java.time.LocalDateTime;

/** 迁移批次台账视图。 */
@Data
public class MigrationBatchRespVO {

    private Long id;
    private String ownerContextCode;
    private String purposeCode;
    private String releaseId;
    private String sourceSystem;
    private String sourceTable;
    private String manifestSchemaVersion;
    private Long expectedRowCount;
    private String contentSha256;
    private LocalDateTime exportedAt;
    private Long previousBatchId;
    private Long previousIssueId;
    /** 批次状态：IMPORTING/STAGED_READY/RECONCILING/COMPLETED/FAILED。 */
    private String status;
    private Long sourceCount;
    private Long mappedCount;
    private Long issueCount;
    private Long retainedCount;
    private String failureCode;
    private String ruleVersion;
    private Integer version;
    private LocalDateTime createTime;

    public static MigrationBatchRespVO of(MigrationBatchDO batch) {
        MigrationBatchRespVO vo = new MigrationBatchRespVO();
        vo.setId(batch.getId());
        vo.setOwnerContextCode(batch.getOwnerContextCode());
        vo.setPurposeCode(batch.getPurposeCode());
        vo.setReleaseId(batch.getReleaseId());
        vo.setSourceSystem(batch.getSourceSystem());
        vo.setSourceTable(batch.getSourceTable());
        vo.setManifestSchemaVersion(batch.getManifestSchemaVersion());
        vo.setExpectedRowCount(batch.getExpectedRowCount());
        vo.setContentSha256(batch.getContentSha256());
        vo.setExportedAt(batch.getExportedAt());
        vo.setPreviousBatchId(batch.getPreviousBatchId());
        vo.setPreviousIssueId(batch.getPreviousIssueId());
        vo.setStatus(batch.getBatchStatus());
        vo.setSourceCount(batch.getSourceCount());
        vo.setMappedCount(batch.getMappedCount());
        vo.setIssueCount(batch.getIssueCount());
        vo.setRetainedCount(batch.getRetainedCount());
        vo.setFailureCode(batch.getFailureCode());
        vo.setRuleVersion(batch.getRuleVersion());
        vo.setVersion(batch.getVersion());
        vo.setCreateTime(batch.getCreateTime());
        return vo;
    }
}
