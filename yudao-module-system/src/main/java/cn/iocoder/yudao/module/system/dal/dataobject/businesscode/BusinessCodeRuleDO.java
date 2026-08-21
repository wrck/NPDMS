package cn.iocoder.yudao.module.system.dal.dataobject.businesscode;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

@TableName("plt_business_code_rule")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessCodeRuleDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String ruleCode;
    private String ruleVersion;
    private String prefix;
    private Integer paddingWidth;
    private Long nextValue;
    private String status;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Integer version;
    private Long creator;
    private LocalDateTime createTime;
    private Long updater;
    private LocalDateTime updateTime;
}
