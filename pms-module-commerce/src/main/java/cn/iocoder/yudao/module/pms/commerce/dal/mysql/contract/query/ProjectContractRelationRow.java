package cn.iocoder.yudao.module.pms.commerce.dal.mysql.contract.query;

import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.contract.ProjectContractRelationDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Read projection of the relationship and its existing contract number; not a new entity column. */
@Data @EqualsAndHashCode(callSuper = true)
public class ProjectContractRelationRow extends ProjectContractRelationDO {
    private String contractNo;
    private String contractName;
    private String companyCode;
    private String companyName;
}
