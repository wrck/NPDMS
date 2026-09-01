package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("cut_step")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverPlanStepDO extends TenantBaseDO {
    @TableId private Long id;
    private Long planRevisionId;
    private String sectionCode;
    private Integer stepNo;
    private String content;
    @Version private Integer version;
}
