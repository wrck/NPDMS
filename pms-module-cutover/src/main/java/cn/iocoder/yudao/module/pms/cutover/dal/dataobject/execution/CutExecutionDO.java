package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.execution;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * PMS 割接执行记录 DO（FR-CUT-011 / FR-CUT-012）。
 * <p>
 * 对应表 {@code pms_cut_execution}，承载割接窗口期逐步执行与异常记录。
 */
@TableName("pms_cut_execution")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutExecutionDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long taskId;
    private String code;
    private String stepName;
    private Long operatorUserId;
    private LocalDateTime operationTime;
    private String result;
    private String exceptionRecord;
    private String evidenceUrl;
    private Integer status;
    private String remark;
    @Version
    private Integer version;
}
