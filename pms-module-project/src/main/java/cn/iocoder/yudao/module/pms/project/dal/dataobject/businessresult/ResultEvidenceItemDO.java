package cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult;

import lombok.Data;

/** 原生结果在特定扫描边界的只读判断，后续撤销以新的判断记录表达。 */
@Data
public class ResultEvidenceItemDO {
    private Long id;
    private Long tenantId;
    private Long projectId;
    private Long scanId;
    private Long candidateId;
    private String objectId;
    private String resultId;
    private Long formationSequence;
    private String eligibility;
    private String reason;
    private String observation;
}
