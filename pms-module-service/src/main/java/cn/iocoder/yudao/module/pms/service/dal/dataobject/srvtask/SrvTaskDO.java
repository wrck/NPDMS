package cn.iocoder.yudao.module.pms.service.dal.dataobject.srvtask;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 巡检任务主表 DO
 */
@TableName("pms_srv_task")
@Data
@EqualsAndHashCode(callSuper = true)
public class SrvTaskDO extends TenantBaseDO {

    /**
     * 任务编号
     */
    @TableId
    private Long id;
    /**
     * 所属项目编号
     */
    private Long projectId;
    /**
     * 设备编号
     */
    private Long equipmentId;
    /**
     * 巡检任务编码，项目内唯一
     */
    private String code;
    /**
     * 巡检任务名称
     */
    private String name;
    /**
     * 巡检方式 ONLINE 在线 / OFFLINE 离线
     */
    private String inspectionMode;
    /**
     * 来源 PROJECT 项目 / PLAN 服务计划 / MANUAL 手工
     */
    private String sourceType;
    /**
     * 来源业务编号
     */
    private Long sourceId;
    /**
     * 计划巡检时间
     */
    private LocalDateTime scheduledTime;
    /**
     * 实际巡检时间
     */
    private LocalDateTime actualTime;
    /**
     * 状态
     *
     * 枚举 0草稿 1待执行 2执行中 3待确认 4已完成 5已取消
     */
    private Integer status;
    /**
     * 设备账号有效性检查结果
     */
    private String accountCheckResult;
    /**
     * 备注
     */
    private String remark;
    /**
     * 乐观锁版本号
     */
    private Integer version;

}
