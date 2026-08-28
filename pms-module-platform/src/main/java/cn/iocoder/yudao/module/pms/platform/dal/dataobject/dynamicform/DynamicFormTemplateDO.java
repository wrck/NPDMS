package cn.iocoder.yudao.module.pms.platform.dal.dataobject.dynamicform;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("plt_dynamic_form_template")
@Data
@EqualsAndHashCode(callSuper = true)
public class DynamicFormTemplateDO extends TenantBaseDO {

    @TableId(type = IdType.INPUT)
    private Long id;
    private String templateCode;
    private String templateName;
    private String categoryCode;
    private String description;
    private String availabilityCode;
    private Long currentPublishedRevisionId;
    private Integer version;

    @TableField(exist = false)
    private Long currentDraftRevisionId;
    @TableField(exist = false)
    private Integer currentDraftRevisionNo;
    @TableField(exist = false)
    private Integer currentDraftVersion;
    @TableField(exist = false)
    private Long currentDraftSourceRevisionId;
    @TableField(exist = false)
    private String currentDraftEngineCode;
    @TableField(exist = false)
    private String currentDraftDesignerVersion;
    @TableField(exist = false)
    private String currentDraftRendererVersion;
    @TableField(exist = false)
    private Integer currentPublishedRevisionNo;
    @TableField(exist = false)
    private Integer currentPublishedRevisionVersion;
    @TableField(exist = false)
    private Long currentPublishedSourceRevisionId;
    @TableField(exist = false)
    private String currentPublishedEngineCode;
    @TableField(exist = false)
    private String currentPublishedDesignerVersion;
    @TableField(exist = false)
    private String currentPublishedRendererVersion;
    @TableField(exist = false)
    private Long currentPublishedBy;
    @TableField(exist = false)
    private LocalDateTime currentPublishedAt;
}
