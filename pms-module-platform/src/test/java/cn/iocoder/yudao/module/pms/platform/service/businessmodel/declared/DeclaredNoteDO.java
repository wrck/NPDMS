package cn.iocoder.yudao.module.pms.platform.service.businessmodel.declared;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.util.List;

/** Third model: persisted fields only, no history or entity-specific behavior. */
@TableName(value = "it_declared_note", autoResultMap = true)
@Data
@EqualsAndHashCode(callSuper = true)
public class DeclaredNoteDO extends BaseBusinessEntity {
    private Long projectRef;
    private String title;
    private BigDecimal amount;
    private String internalMemo;
    private String referenceCode;
    @TableField(typeHandler = Jackson3TypeHandler.class)
    private List<String> tags;
}
