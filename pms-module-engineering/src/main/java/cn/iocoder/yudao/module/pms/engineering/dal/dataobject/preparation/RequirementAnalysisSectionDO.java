package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("sol_requirement_analysis_section")
@Data
public class RequirementAnalysisSectionDO implements Serializable {
    @TableId private Long id;
    private Long preparationId;
    private Long sourceSectionId;
    private String sectionCode;
    private String sectionName;
    private String sectionKindCode;
    private String fieldTypeCode;
    private Boolean requiredFlag;
    private String dictionaryType;
    private Integer sortOrder;
    private String schemaSnapshot;
    private String valueSnapshot;
    private String attachmentReferenceSnapshot;
    private Integer version;
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;
    private Long tenantId;
}
