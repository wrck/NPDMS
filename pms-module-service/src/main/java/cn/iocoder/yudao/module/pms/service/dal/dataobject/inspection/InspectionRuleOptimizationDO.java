package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
@Data @EqualsAndHashCode(callSuper = true) @TableName("srv_inspection_rule_optimization")
public class InspectionRuleOptimizationDO extends BaseBusinessEntity {
    private Long sourceAnnotationId, sourceRuleRevisionId, draftRuleRevisionId, createdBy;
    private String reason;
    private LocalDateTime createdAt;
}
