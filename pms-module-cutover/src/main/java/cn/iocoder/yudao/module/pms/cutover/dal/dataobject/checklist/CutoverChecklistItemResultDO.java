package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@TableName("cut_cutover_checklist_item_result")
@Data
public class CutoverChecklistItemResultDO {

    @TableId
    private Long id;
    private Long tenantId;
    private Long checklistItemId;
    private Integer resultVersion;
    private String resultSourceCode;
    private String answerSnapshot;
    private String factDescription;
    private Long collectionTaskId;
    private Long collectionResultReferenceId;
    private Long collectionResultVersion;
    private String externalSourceCode;
    private String queryConditionSnapshot;
    private LocalDateTime queriedAt;
    private String loadFailureCode;
    private String manualEvidenceFileReference;
    private LocalDateTime selectionStartedAt;
    private LocalDateTime selectionEndedAt;
    private Long selectedBy;
    private String selectionReasonCode;
    private Integer currentMarker;
    private Long createdBy;
    private LocalDateTime createdAt;
    @TableLogic
    private Boolean deleted;
}
