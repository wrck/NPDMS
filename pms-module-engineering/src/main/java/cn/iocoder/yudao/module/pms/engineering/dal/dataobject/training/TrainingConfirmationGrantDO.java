package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.training;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;import lombok.EqualsAndHashCode;import java.time.LocalDateTime;
/** Immutable issuance evidence, not a business aggregate or customer account. */
@Data @EqualsAndHashCode(callSuper=true) @TableName("imp_training_confirmation_grant")
public class TrainingConfirmationGrantDO extends TenantBaseDO {
 private Long id;private Long trainingId;private Long issuanceVersion;private String tokenDigest;private Long issuedByUserId;
 private LocalDateTime issuedAt;private LocalDateTime expiresAt;private Long scopeVersion;private Long confirmationRevisionId;
 private String confirmationRulesSha256;
}
