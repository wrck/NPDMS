package cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.acceptancereport.business;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.*;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName("acc_acceptance")
@ProjectBusinessModel(ownerModule="ACC",entityType="acceptanceActivity",stableCode="ACC_ACCEPTANCE_ACTIVITY",name="初验终验报告",permissionPrefix="pms:acceptance:report",nativeEntityType="AcceptanceActivity")
public class AcceptanceBusinessDO extends BaseProjectBusinessEntity {
    @NotBlank @Pattern(regexp="PRELIMINARY|FINAL") @BusinessModelField(name="验收类型") private String acceptanceType;
    @BusinessModelField(name="活动状态",writable=false) private String activityStatus;
    @BusinessModelField(name="当前报告版本",writable=false) private Long currentReportVersionId;
    @BusinessModelField(name="来源类型",writable=false) private String originKind;
    @BusinessModelField(name="原项目任务",writable=false) private Long projectTaskId;
    @BusinessModelField(name="原执行契约",writable=false) private Long executionContractId;
    @BusinessModelField(name="原交付要求",writable=false) private Long deliverableId;
    private String originKey;
    private String originSnapshot;
    private String ruleSnapshot;
}
