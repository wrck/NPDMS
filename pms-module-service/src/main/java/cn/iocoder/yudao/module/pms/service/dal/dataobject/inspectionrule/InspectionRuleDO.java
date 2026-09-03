package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("srv_inspection_rule")
@Data
@EqualsAndHashCode(callSuper = true)
public class InspectionRuleDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String detectionId;
    private String ruleName;
    @Version
    private Integer version;
}
