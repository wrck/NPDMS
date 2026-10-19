package cn.iocoder.yudao.module.pms.integration.dal.mysql.sync;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/** Only fields read or inserted by the approved supplementary product task. Existing rows are never updated. */
@Data
@EqualsAndHashCode(callSuper=true)
public class DirectProductRow extends BaseBusinessEntity {
    private String productCode;
    private String productName;
    private String productModel;
    private String productDesc;
    private String status;
    private String sourceSystem;
    private String sourceKey;
    private String syncStatus;
    private LocalDateTime syncedAt;
}
