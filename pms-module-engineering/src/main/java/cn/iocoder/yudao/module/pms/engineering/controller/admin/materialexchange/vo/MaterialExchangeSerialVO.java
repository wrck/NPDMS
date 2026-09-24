package cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 换货申请设备行明细：入参只信任清单行引用（scopeDetailId/scopeId 二选一）与换货数量；
 * 旧行兼容 equipmentId；订单行快照字段由服务端生成并用于回显。
 */
@Data
public class MaterialExchangeSerialVO {
    /** 清单行引用：交付范围明细拆分行 */
    private Long scopeDetailId;
    /** 清单行引用：无明细拆分的交付范围行 */
    private Long scopeId;
    /** 换货数量，按清单行填写；缺省按 1 台处理 */
    @Positive(message = "换货数量必须大于 0")
    private BigDecimal quantity;
    private String orderNo;
    private String lineNo;
    private String itemCode;
    private String name;
    private String productCode;
    private String deviceTypeCode;
    private String deviceTypeName;
    /** 兼容旧序列号快照：原设备ID */
    private Long equipmentId;
    /** 兼容旧序列号快照 */
    private String sn;
    /** 兼容旧序列号快照 */
    private String productModel;
    /** 兼容旧序列号快照 */
    private String contractNo;
}
