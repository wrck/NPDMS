package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cut_cutover_support_arrangement")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverSupportArrangementDO extends TenantBaseDO {
    @TableId private Long id;
    private Long planRevisionId;
    private String roleCode;
    private String personName;
    private String dutyDescription;
    private String phone;
    private LocalDateTime arrivalTime;
    @Version private Integer version;
}
