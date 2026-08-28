package cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("plt_dynamic_form_template_revision")
@Data
@EqualsAndHashCode(callSuper = true)
public class DynamicFormTemplateRevisionDO extends TenantBaseDO {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long templateId;
    private Integer revisionNo;
    private String statusCode;
    private Integer draftMarker;
    private Long sourceRevisionId;
    private String formConfJson;
    private String formRulesJson;
    private String engineCode;
    private String designerVersion;
    private String rendererVersion;
    private Long publishedBy;
    private LocalDateTime publishedAt;
    private Integer version;
}
