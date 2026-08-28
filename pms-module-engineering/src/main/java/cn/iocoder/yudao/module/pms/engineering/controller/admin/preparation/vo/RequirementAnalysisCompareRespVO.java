package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
public class RequirementAnalysisCompareRespVO {
    private Long sourcePreparationId;
    private Integer sourceBusinessVersion;
    private Long targetPreparationId;
    private Integer targetBusinessVersion;
    private List<SectionDifference> sections;
    private List<FieldDifference> fields;

    @Data
    @AllArgsConstructor
    public static class SectionDifference {
        private String sectionCode;
        private String changeType;
        private Boolean contentChanged;
        private Boolean attachmentsChanged;
    }

    @Data
    @AllArgsConstructor
    public static class FieldDifference {
        private String fieldKey;
        private String fieldLabel;
        private String changeType;
        private Object sourceValue;
        private Object targetValue;
        private Boolean controlledFilesChanged;
    }
}
