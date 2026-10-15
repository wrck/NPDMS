package cn.iocoder.yudao.module.pms.customer.dal.dataobject.inspection;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cus_inspection_reference")
public class CustomerInspectionReferenceDO extends BaseBusinessEntity {
    private Long historyId;
    private String referenceKind;
    private Long sourceId;
}
