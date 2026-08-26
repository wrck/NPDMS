package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("sol_dynamic_form_instance")
@Data
public class DynamicFormInstanceDO implements Serializable {
    @TableId private Long id;
    private Long preparationId;
    private Long itemId;
    private String formCode;
    private Integer formVersion;
    private String schemaSnapshot;
    private String valueSnapshot;
    private String statusCode;
    private LocalDateTime frozenAt;
    private Long frozenBy;
    private Integer version;
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;
    private Long tenantId;
}
