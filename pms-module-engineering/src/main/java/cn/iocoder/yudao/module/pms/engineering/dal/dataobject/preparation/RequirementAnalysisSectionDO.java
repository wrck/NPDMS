package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@TableName("sol_requirement_analysis_section")
@Data
@Deprecated // F-SOL-003已改用PLT动态表单实例；本DO仅保留历史候选解释。
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
