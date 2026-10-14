package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@TableName("cut_cutover_collection_evidence")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CutoverCollectionEvidenceDO {
    @TableId
    private Long id;
    private Long tenantId;
    private Long closureId;
    private Long taskId;
    private Long projectId;
    private Long deviceId;
    private String collectionStageCode;
    private String evidenceTypeCode;
    private String collectionTaskId;
    private String callbackEventId;
    private String resultRef;
    private String resultVersion;
    private String originalFailedCollectionTaskId;
    private Long manualAttachmentId;
    private Integer dispatchMarker;
    private Integer callbackMarker;
    private Integer manualMarker;
    @TableField(value = "occur_time")
    private LocalDateTime occurredAt;
    @TableField(value = "record_user_id")
    private Long recordedBy;
    private String creator;
    private LocalDateTime createTime;
    private Boolean deleted;
}
