package cn.iocoder.yudao.module.pms.cutover.dal.dataobject.checklist;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@TableName("cut_cutover_checklist_item_result")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CutoverChecklistItemResultDO {

    @TableId
    private Long id;
    private Long tenantId;
    private Long checklistItemId;
    private Integer resultVersion;
    private String resultSourceCode;
    @TableField(exist = false)
    private String answerSnapshot;
    private String factDescription;
    private Long collectionTaskId;
    private Long collectionResultReferenceId;
    private Long collectionResultVersion;
    private String externalSourceCode;
    @TableField(exist = false)
    private String queryConditionSnapshot;
    @TableField(value = "query_time")
    private LocalDateTime queriedAt;
    private String loadFailureCode;
    @TableField(exist = false)
    private String manualEvidenceFileReference;
    @TableField(value = "selection_start_time")
    private LocalDateTime selectionStartedAt;
    @TableField(value = "selection_end_time")
    private LocalDateTime selectionEndedAt;
    @TableField(value = "select_user_id")
    private Long selectedBy;
    private String selectionReasonCode;
    private Integer currentMarker;
    @TableField(value = "create_user_id")
    private Long createdBy;
    @TableField(value = "create_time")
    private LocalDateTime createdAt;
    @TableLogic
    private Boolean deleted;

    /** 本结果写入时的精确根字段定义，重匹配不能改变历史结果的解释依据。 */
    private Long fieldDefinitionId;


    /** 被分析/采用的不可变原结果ID */
    private Long baseChecklistResultId;
    /** 单设备来源对应的设备；多设备外部查询按查询关系逐个保存 */
    private Long deviceId;
    /** 本次实际采集命令模板ID */
    private Long commandTemplateId;
    /** 本次采用的采集回执 */
    private Long collectionLogId;
    /** 采用时采集状态事实 */
    private String collectionStatusCode;
    /** 采用时采集失败编码 */
    private String collectionFailureCode;
    /** 人工替代对应的失败采集任务 */
    private Long originalFailedCollectionTaskId;
    /** 原失败结果版本 */
    private Long originalFailedResultVersion;
    /** 原失败原因 */
    private String originalFailureCode;
}
