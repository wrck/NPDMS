package cn.iocoder.yudao.module.pms.platform.dal.dataobject.export;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("plt_export_audit")
@Data
public class PlatformExportAuditDO implements Serializable {

    @TableId
    private Long id;
    private Long tenantId;
    private Long exportTaskId;
    private Integer auditSequence;
    private String actionCode;
    private Long actorUserId;
    private String detailSnapshot;
    private LocalDateTime occurredAt;
    private String creator;
    private LocalDateTime createTime;
}
