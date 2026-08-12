package cn.iocoder.yudao.module.pms.project.dal.dataobject.servicelevel;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * PMS 客户服务等级 DO
 */
@TableName("pms_customer_service_level")
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerServiceLevelDO extends TenantBaseDO {

    /**
     * 服务等级编号
     */
    @TableId
    private Long id;
    /**
     * 客户编号
     */
    private Long customerId;
    /**
     * 服务等级 STRATEGIC 战略 / IMPORTANT 重要 / STANDARD 标准 / GENERAL 一般
     */
    private String level;
    /**
     * 生效开始日期
     */
    private LocalDate validFrom;
    /**
     * 生效结束日期
     */
    private LocalDate validTo;
    /**
     * 状态：0草稿 1已生效 2已停用 3已归档
     */
    private Integer status;
    /**
     * 响应时间（小时）
     */
    private Integer responseTimeHours;
    /**
     * 是否主动服务
     */
    private Boolean proactiveService;
    /**
     * 备注
     */
    private String remark;
    /**
     * 乐观锁版本
     */
    @Version
    private Integer version;

}
