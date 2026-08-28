package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("sol_preparation_item_waiver")
@Data
public class PreparationItemWaiverDO implements Serializable {
    @TableId private Long id;
    private Long projectId;
    private Long preparationId;
    private Long itemId;
    private String itemCode;
    private Integer waiverNo;
    private String statusCode;
    private String blockerCodesSnapshot;
    private String reason;
    private String risk;
    private String compensation;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private String approvalRoleCode;
    private Long applicantUserId;
    private LocalDateTime submittedAt;
    private Long decidedBy;
    private LocalDateTime decidedAt;
    private String decisionOpinion;
    private LocalDateTime withdrawnAt;
    private Integer version;
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;
    private Long tenantId;
}
