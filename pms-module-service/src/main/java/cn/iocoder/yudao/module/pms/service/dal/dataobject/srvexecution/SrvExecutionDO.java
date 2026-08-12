package cn.iocoder.yudao.module.pms.service.dal.dataobject.srvexecution;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 在线巡检执行记录 DO
 */
@TableName("pms_srv_execution")
@Data
@EqualsAndHashCode(callSuper = true)
public class SrvExecutionDO extends TenantBaseDO {

    /**
     * 执行记录编号
     */
    @TableId
    private Long id;
    /**
     * 所属巡检任务编号
     */
    private Long taskId;
    /**
     * 执行编码，任务内唯一
     */
    private String code;
    /**
     * 关联规则编号
     */
    private Long ruleId;
    /**
     * 执行时间
     */
    private LocalDateTime executionTime;
    /**
     * 执行人
     */
    private Long executorUserId;
    /**
     * 执行结果
     */
    private String result;
    /**
     * 异常记录
     */
    private String exceptionRecord;
    /**
     * 证据附件
     */
    private String evidenceUrl;
    /**
     * 状态
     *
     * 枚举 0待执行 1执行中 2已完成 3异常
     */
    private Integer status;
    /**
     * 备注
     */
    private String remark;
    /**
     * 乐观锁版本号
     */
    private Integer version;

}
