package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import lombok.Data;

import java.util.List;

@Data
public class RequirementAnalysisSectionRespVO {
    private Long sectionId;
    private Long sourceSectionId;
    private String sectionCode;
    private String sectionName;
    private String sectionKind;
    private String fieldType;
    private Boolean required;
    private String dictionaryType;
    private Integer sortOrder;
    private String schemaSnapshot;
    private String valueSnapshot;
    private List<RequirementAnalysisAttachmentRespVO> attachments;
    private Integer version;
    private List<String> allowedActions;
}
