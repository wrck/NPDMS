package cn.iocoder.yudao.module.pms.commerce.api.scope.dto;

import java.math.BigDecimal;

/** 设备清单行服务器快照；只读事实，消费方持久化为保存时快照，不回写交付范围。 */
public record DeliveryScopeLineFact(Long scopeDetailId, Long scopeId, String orderNo, String lineNo,
                                    String itemCode, String productName, String productCode,
                                    String deviceTypeCode, String deviceTypeName,
                                    BigDecimal allocatedQuantity) { }
