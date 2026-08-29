package cn.iocoder.yudao.module.pms.project.dal.dataobject.acceptancereport;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("acc_acceptance_report_version")
@Data
@EqualsAndHashCode(callSuper = true)
public class AcceptanceReportVersionDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long acceptanceId;
    private Integer reportVersionNo;
    private String reportStatus;
    private LocalDateTime acceptanceTime;
    private String conclusionCode;
    private String conclusionText;
    private String acceptorName;
    private Long previousVersionId;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Integer currentMarker;
    private Long uploaderUserId;
    private LocalDateTime uploadTime;
    private Long publisherUserId;
}
