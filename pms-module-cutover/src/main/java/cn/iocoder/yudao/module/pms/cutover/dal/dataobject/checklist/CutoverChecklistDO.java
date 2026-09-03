package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("cut_cutover_checklist")
@Data
@EqualsAndHashCode(callSuper = true)
public class CutoverChecklistDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long cutoverTaskId;
    private Long assessmentId;
    private Integer assessmentVersion;
    private Integer checklistVersion;
    private String statusCode;
    private String inputSnapshot;
    private String inputSnapshotHash;
    private String configRevisionSnapshot;
    private String matchTrace;
    private String configGapSnapshot;
    private Long submittedBy;
    private LocalDateTime submittedAt;
    private LocalDateTime invalidatedAt;
    private String invalidatedReason;
    private Integer currentMarker;
    @Version
    private Integer version;
}
