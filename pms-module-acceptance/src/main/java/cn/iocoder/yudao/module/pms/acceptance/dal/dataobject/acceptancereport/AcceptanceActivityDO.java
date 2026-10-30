package cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("acc_acceptance")
@Data
@EqualsAndHashCode(callSuper = true)
public class AcceptanceActivityDO extends BaseBusinessEntity {
    private Long projectId;
    private Long projectTaskId;
    private Long executionContractId;
    private Long deliverableId;
    private String acceptanceType;
    private String activityStatus;
    @com.baomidou.mybatisplus.annotation.TableField(updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
    private Long currentReportVersionId;
    private String originKind;
    private String originKey;
    private String originSnapshot;
    private String ruleSnapshot;
}
