package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("sol_preparation_item")
@Data
public class PreparationItemDO implements Serializable {
    @TableId private Long id;
    private Long preparationId;
    private Long sourceItemId;
    private String itemCode;
    private String itemName;
    private Integer sortOrder;
    private String applicabilityCode;
    private String confirmationStatusCode;
    private String formCode;
    private Integer formVersion;
    private String formSchemaSnapshot;
    private String evidencePolicySnapshot;
    private String sourcePolicySnapshot;
    private String waiverPolicySnapshot;
    private Boolean outsourced;
    private Long assigneeUserId;
    private LocalDateTime assigneeEffectiveFrom;
    private String siteResultCode;
    private String siteResultDetail;
    private String evidenceReferenceSnapshot;
    private String notApplicableReason;
    private Long notApplicableConfirmedBy;
    private LocalDateTime notApplicableConfirmedAt;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private Long returnedBy;
    private LocalDateTime returnedAt;
    private String returnReason;
    private Integer version;
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;
    private Long tenantId;
}
