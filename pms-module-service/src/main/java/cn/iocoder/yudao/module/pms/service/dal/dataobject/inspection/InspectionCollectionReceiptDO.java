package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_inspection_collection_receipt")
public class InspectionCollectionReceiptDO extends BaseBusinessEntity {
    private Long taskId;
    private Long executionId;
    private Long collectionExecutionId;
    private String collectionTaskId;
    private Long resultVersion;
    private Long fileVersionId;
    private String externalStatus;
    private String failureCategory;
    private Long actorId;
    private java.time.LocalDateTime receivedAt;
}
