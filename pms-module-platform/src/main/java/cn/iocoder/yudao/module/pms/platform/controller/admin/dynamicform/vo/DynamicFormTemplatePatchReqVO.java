package cn.iocoder.yudao.module.pms.platform.controller.admin.dynamicform.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

/** 通过setter记录PATCH字段是否出现。 */
public class DynamicFormTemplatePatchReqVO {
    private String templateName;
    private String categoryCode;
    private String description;
    @JsonIgnore private boolean templateNamePresent;
    @JsonIgnore private boolean categoryCodePresent;
    @JsonIgnore private boolean descriptionPresent;

    public String getTemplateName() { return templateName; }
    public String getCategoryCode() { return categoryCode; }
    public String getDescription() { return description; }
    public boolean isTemplateNamePresent() { return templateNamePresent; }
    public boolean isCategoryCodePresent() { return categoryCodePresent; }
    public boolean isDescriptionPresent() { return descriptionPresent; }

    @JsonSetter("templateName")
    public void setTemplateName(String value) { templateNamePresent = true; templateName = value; }
    @JsonSetter("categoryCode")
    public void setCategoryCode(String value) { categoryCodePresent = true; categoryCode = value; }
    @JsonSetter("description")
    public void setDescription(String value) { descriptionPresent = true; description = value; }
}
