package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.resource;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 资源与备件就绪 DO
 */
@TableName("pms_eng_resource_ready")
@Data
@EqualsAndHashCode(callSuper = true)
public class ResourceReadyDO extends TenantBaseDO {

    /**
     * 主键
     */
    @TableId
    private Long id;
    /**
     * 所属项目编号
     */
    private Long projectId;
    /**
     * 就绪编码，项目内唯一
     */
    private String code;
    /**
     * 资源名称
     */
    private String name;
    /**
     * PEOPLE 备件 / SPARE 物料 / TOOL 工具 / TEST_ENV 测试环境 / WINDOW 时间窗口 / APPROVAL 客户批准
     */
    private String resourceType;
    /**
     * 关联设备编号
     */
    private Long equipmentId;
    /**
     * 数量
     */
    private Integer quantity;
    /**
     * 0未就绪 1已就绪 2异常
     */
    private Integer readyStatus;
    /**
     * 就绪时间
     */
    private LocalDateTime readyTime;
    /**
     * 就绪确认人
     */
    private Long readyUserId;
    /**
     * 备注
     */
    private String remark;
    /**
     * 乐观锁
     */
    @Version
    private Integer version;

}
