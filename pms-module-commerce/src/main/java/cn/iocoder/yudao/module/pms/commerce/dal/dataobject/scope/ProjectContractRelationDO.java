package cn.iocoder.yudao.module.pms.commerce.dal.dataobject.scope;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("com_project_contract_relation")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectContractRelationDO extends TenantBaseDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private Long contractId;
    private String relationStatus;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    @Version
    private Integer version;
}
