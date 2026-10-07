package cn.iocoder.yudao.module.pms.platform.service.businessmodel.declared;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

/** Plain ORM mapping deliberately has no autoResultMap or custom type handler. */
@TableName("it_declared_note")
@Data
@EqualsAndHashCode(callSuper = true)
public class PlainDeclaredNoteDO extends BaseBusinessEntity {
    private Long projectRef;
    private String title;
    private BigDecimal amount;
    private String internalMemo;
    private String referenceCode;
}
