package cn.iocoder.yudao.module.pms.commerce.dal.dataobject.associated;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/** Contract collection plan. Actual receipt and acceptance completion have separate Owners. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("com_contract_collection_plan")
public class ContractCollectionPlanDO extends TenantBaseDO {
    @TableId private Long id;
    private Long contractId;
    private String contractNo;
    private String batchCode;
    private String collectionItemName;
    private String referenceEventName;
    private LocalDateTime eventPlannedAt;
    private Integer afterDays;
    private LocalDateTime eventActualFinishedAt;
    private String marketingFeedback;
    private String dataSource;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
}
