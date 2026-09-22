package cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** 入参只信任设备ID，其余字段由服务端生成并用于回显。 */
@Data
public class MaterialExchangeSerialVO {
    @NotNull
    @Positive
    private Long equipmentId;
    private String sn;
    private String name;
    private String productCode;
    private String productModel;
    private String contractNo;
}
