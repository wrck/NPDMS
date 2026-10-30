package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspectionrule;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("srv_inspection_rule")
@Data
@EqualsAndHashCode(callSuper = true)
public class InspectionRuleDO extends BaseBusinessEntity {

    private String detectionId;
    private String ruleName;
}
