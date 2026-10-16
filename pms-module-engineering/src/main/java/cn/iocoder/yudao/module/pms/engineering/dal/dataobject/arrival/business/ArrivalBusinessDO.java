package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.arrival.business;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseProjectBusinessEntity;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.BusinessModelField;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.model.ProjectBusinessModel;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/** Default business contract over the existing imp_eng_arrival table. */
@TableName("imp_eng_arrival")
@Data
@EqualsAndHashCode(callSuper = true)
@ProjectBusinessModel(ownerModule = "IMP", entityType = "arrival", stableCode = "IMP_ARRIVAL",
        name = "到货签收", permissionPrefix = "pms:imp-arrival", nativeEntityType = "ARRIVAL")
public class ArrivalBusinessDO extends BaseProjectBusinessEntity {
    @BusinessModelField(name = "签收编码", displayOrder = 10, writable = false)
    private String code;
    @BusinessModelField(name = "到货时间", displayOrder = 20)
    @jakarta.validation.constraints.NotNull
    private LocalDateTime arrivalTime;
    @BusinessModelField(name = "签收人", displayOrder = 30)
    private Long receiverUserId;
    @BusinessModelField(name = "关联设备", displayOrder = 40)
    private Long equipmentId;
    @BusinessModelField(name = "到货数量", displayOrder = 50)
    private Integer quantity;
    @BusinessModelField(name = "检查结果", displayOrder = 60)
    private String inspectionResult;
    @BusinessModelField(name = "异常记录", displayOrder = 70)
    private String exceptionRecord;
    @BusinessModelField(name = "签收单附件", displayOrder = 80)
    private String attachmentUrl;
    @BusinessModelField(name = "状态", displayOrder = 90, writable = false)
    private Integer status;
    @BusinessModelField(name = "备注", displayOrder = 100)
    private String remark;
}

