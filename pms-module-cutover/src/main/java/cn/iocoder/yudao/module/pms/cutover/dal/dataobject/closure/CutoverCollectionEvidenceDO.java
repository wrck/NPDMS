package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("cut_cutover_collection_evidence")
@Data
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
    private LocalDateTime occurredAt;
    private Long recordedBy;
    private String creator;
    private LocalDateTime createTime;
    private Boolean deleted;
}
