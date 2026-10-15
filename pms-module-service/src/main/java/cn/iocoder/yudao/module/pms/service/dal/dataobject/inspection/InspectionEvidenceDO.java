package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_inspection_evidence")
public class InspectionEvidenceDO extends BaseBusinessEntity {
    private Long taskId;
    private Long remediationId;
    private Long artifactId;
    private Integer fileVersionNo;
    private String referenceKey;
    private String fileName;
    private String sha256;
}
