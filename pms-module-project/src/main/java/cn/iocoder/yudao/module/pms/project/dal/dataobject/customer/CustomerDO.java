package cn.iocoder.yudao.module.pms.project.dal.dataobject.customer;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PMS 客户 DO
 */
@TableName("pms_customer")
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerDO extends TenantBaseDO {

    /**
     * 客户编号
     */
    @TableId
    private Long id;
    /**
     * 全局唯一且不可变的客户编码
     */
    private String code;
    /**
     * 客户名称
     */
    private String name;
    /**
     * 客户简称
     */
    private String shortName;
    /**
     * 状态：0启用，1停用
     */
    private Integer status;
    /**
     * 地址
     */
    private String address;
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
