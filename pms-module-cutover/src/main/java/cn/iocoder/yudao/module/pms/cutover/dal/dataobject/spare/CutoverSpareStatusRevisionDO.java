package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.spare;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("cut_spare_status_revision")
@Data
public class CutoverSpareStatusRevisionDO {
    @TableId
    private Long id;
    private Long tenantId;
    private Long applicationReferenceId;
    private Long statusVersion;
    private String externalStatusRaw;
    private String statusSnapshot;
    private String sourceType;
    private LocalDateTime observedAt;
    private LocalDateTime externalOccurredAt;
    private String eventId;
    private String correlationId;
    private Integer currentMarker;
    private Long createdBy;
    private LocalDateTime createdAt;
}
