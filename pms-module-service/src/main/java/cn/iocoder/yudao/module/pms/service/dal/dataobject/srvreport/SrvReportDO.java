package cn.iocoder.yudao.module.pms.service.dal.dataobject.srvreport;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 巡检报告 DO
 */
@TableName("pms_srv_report")
@Data
@EqualsAndHashCode(callSuper = true)
public class SrvReportDO extends TenantBaseDO {

    /**
     * 报告编号
     */
    @TableId
    private Long id;
    /**
     * 所属巡检任务编号
     */
    private Long taskId;
    /**
     * 报告编码，任务内唯一
     */
    private String code;
    /**
     * 报告类型 STANDARD 标准 / PDF / DOC / XML
     */
    private String reportType;
    /**
     * 报告内容
     */
    private String content;
    /**
     * 巡检快照
     */
    private String snapshot;
    /**
     * 生成人
     */
    private Long generatedBy;
    /**
     * 生成时间
     */
    private LocalDateTime generatedTime;
    /**
     * 状态
     *
     * 枚举 0草稿 1已生成 2已归档
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
