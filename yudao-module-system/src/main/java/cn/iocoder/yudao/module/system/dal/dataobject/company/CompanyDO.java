package cn.iocoder.yudao.module.system.dal.dataobject.company;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 公司主数据。
 */
@TableName("system_company")
@Data
@EqualsAndHashCode(callSuper = true)
public class CompanyDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String code;
    private String name;
    /**
     * 状态，见 {@link CommonStatusEnum}。
     */
    private Integer status;
    private Integer version;

}
