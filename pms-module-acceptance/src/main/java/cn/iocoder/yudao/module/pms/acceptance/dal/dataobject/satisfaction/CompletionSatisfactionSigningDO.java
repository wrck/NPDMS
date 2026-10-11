package cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/** One signature applies to the frozen certificate and this questionnaire response together. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("acc_completion_satisfaction_signing")
public class CompletionSatisfactionSigningDO extends BaseBusinessEntity {
    private String signatureImageDataUrl;
    private Long bundleId;
    private Long questionnaireId;
    private Long responseId;
    private Long grantId;
    private String signatureFileFact;
    private LocalDateTime signedAt;
}
