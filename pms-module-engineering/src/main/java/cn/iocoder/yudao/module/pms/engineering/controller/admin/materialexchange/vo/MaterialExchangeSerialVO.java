package cn.iocoder.yudao.module.pms.engineering.controller.admin.materialexchange.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 换货申请设备行明细：入参只信任清单行引用（scopeDetailId/scopeId 二选一）、换货数量与换货产品（productId）；
 * 旧行兼容 deviceId；订单行与换货产品快照字段由服务端按引用生成并用于回显。
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
    private String productName;
    private String productCode;
    private String deviceTypeCode;
    private String deviceTypeName;
    /** 换货产品：产品信息引用；可留空草稿后补 */
    private Long productId;
    /** 兼容旧序列号快照：原设备ID */
    private Long deviceId;
    /** 兼容旧序列号快照 */
    private String sn;
    /** 兼容旧序列号快照 */
    private String productModel;
    /** 兼容旧序列号快照 */
    private String contractNo;
}
