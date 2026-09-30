package cn.iocoder.yudao.module.pms.commerce.api.scope.dto;

import java.math.BigDecimal;

/** 设备清单行服务器快照；只读事实，消费方持久化为保存时快照，不回写交付范围。
 * productCode 取明细行产品编码，缺值回填范围行订单行产品编码（两者同源于订单行）。 */
public record DeliveryScopeLineFact(Long scopeDetailId, Long scopeId, String orderNo, String lineNo,
                                    String productCode, String productName,
                                    String deviceTypeCode, String deviceTypeName,
                                    BigDecimal allocatedQuantity) { }
