package cn.iocoder.yudao.module.pms.project.dal.dataobject.customercontact;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PMS 客户联系人 DO
 *
 * 注意：active_primary_customer_id 为数据库生成列，不应在此声明与读写。
 */
@TableName("pms_customer_contact")
@Data
@EqualsAndHashCode(callSuper = true)
public class CustomerContactDO extends TenantBaseDO {

    /**
     * 联系人编号
     */
    @TableId
    private Long id;
    /**
     * 客户编号
     */
    private Long customerId;
    /**
     * 姓名
     */
    private String name;
    /**
     * 部门
     */
    private String department;
    /**
     * 职务
     */
    private String title;
    /**
     * 手机
     */
    private String mobile;
    /**
     * 电话
     */
    private String phone;
    /**
     * 邮箱
     */
    private String email;
    /**
     * 是否主联系人
     */
    private Boolean primaryFlag;
    /**
     * 状态：0启用，1停用
     */
    private Integer status;
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
