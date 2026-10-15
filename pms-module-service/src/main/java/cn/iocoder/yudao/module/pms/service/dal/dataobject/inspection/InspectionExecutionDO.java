package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_inspection_execution")
public class InspectionExecutionDO extends BaseBusinessEntity {
    private Long taskId;
    private Long attemptId;
    private Long deviceId;
    private String purposeCode;
    private String statusCode;
    private Long artifactId;
    private Integer fileVersionNo;
    private String fileReferenceKey;
    private String fileName;
    private String fileSha256;
    private String originalFileSha256;
    private String softwareVersion;
    private String commandHint;
    private Long collectionExecutionId;
    private String collectionRequestKey;
    private String collectionTaskId;
    private Integer collectionResultVersion;
    private Long collectionFileVersionId;
    private String collectionStatus;
    private String collectionFailureCategory;
    private String parserRequestId;
    private String parserTaskId;
    private String parserRelease;
    private String recognizedSerial;
    private String failureCode;
    private String failureMessage;
    private Long submittedBy;
    private java.time.LocalDateTime submittedAt;
    private java.time.LocalDateTime completedAt;
    private String protocol;
    private String host;
    private Integer port;
    private String username;
    private Long credentialId;
    private Long credentialVersion;
    private Long credentialGrantId;
    private Boolean credentialSnapshotRecorded;
    private Long precheckExecutionId;
    private java.time.LocalDateTime precheckExpiresAt;
    private Integer precheckTtlSeconds;
    private java.time.LocalDateTime deviceStartedAt;
    private java.time.LocalDateTime deviceCompletedAt;
    private String ruleDigest;
}
