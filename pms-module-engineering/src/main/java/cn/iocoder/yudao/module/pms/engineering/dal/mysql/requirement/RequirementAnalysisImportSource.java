package cn.iocoder.yudao.module.pms.engineering.dal.mysql.requirement;

import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.requirement.RequirementAnalysisRevisionDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Read-only legacy projection. Aliased source columns reuse the new domain's typed metadata. */
@Data
@EqualsAndHashCode(callSuper = true)
public class RequirementAnalysisImportSource extends RequirementAnalysisRevisionDO {
    private Long dynamicFormInstanceId;
    private String entityValueJson;
}
