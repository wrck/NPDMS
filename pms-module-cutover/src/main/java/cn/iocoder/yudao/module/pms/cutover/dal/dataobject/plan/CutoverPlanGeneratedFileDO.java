package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.plan;

import lombok.Data;

/** 已生成文件的不可变引用；正文版本变化后保留旧记录但不作为当前文件。 */
@Data
public class CutoverPlanGeneratedFileDO {
    private Long id;
    private Long tenantId;
    private Long taskId;
    private Long planRevisionId;
    private Long planVersion;
    private Long artifactId;
    private Integer fileVersionNo;
    private String referenceKey;
    private Integer artifactFactVersion;
    private Integer referenceFactVersion;
    private Integer availabilityFactVersion;
    private Long scopeVersion;
    private String sha256;
    private Long generatedAt;
    private Long creator;
}
