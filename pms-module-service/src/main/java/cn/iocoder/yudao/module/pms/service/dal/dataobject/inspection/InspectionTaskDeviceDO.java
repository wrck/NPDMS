package cn.iocoder.yudao.module.pms.service.dal.dataobject.inspection;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("srv_inspection_task_device")
public class InspectionTaskDeviceDO extends BaseBusinessEntity {
    private Long taskId;
    private Long deviceId;
    private String serialNumber;
    private String deviceName;
    private String productTypeCode;
    private String productTypeName;
    private String productModel;
    private String contractNo;
}
