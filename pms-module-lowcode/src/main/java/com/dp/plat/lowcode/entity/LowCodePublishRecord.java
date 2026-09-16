package com.dp.plat.lowcode.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("pms_lowcode_publish_record")
public class LowCodePublishRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String configType;
    private Long configId;
    private String configCode;
    private Integer version;
    /** DRAFT / SUBMITTED / APPROVING / APPROVED / REJECTED / PUBLISHED */
    private String status;
    /** 当前审批级别（多级审批链生效时从 1 开始递增；单步审批为 null） */
    private Integer currentLevel;
    /** 审批链 ID（关联 pms_lowcode_approval_chain，无审批链时为 null） */
    private Long approvalChainId;
    private Long applicantId;
    private String applicant;
    private Long approverId;
    private String approver;
    private String changeLog;
    private String rejectReason;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime publishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
