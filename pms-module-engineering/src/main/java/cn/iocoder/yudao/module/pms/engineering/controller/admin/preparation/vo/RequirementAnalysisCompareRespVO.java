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

    @Data
    @AllArgsConstructor
    public static class SectionDifference {
        private String sectionCode;
        private String changeType;
        private Boolean contentChanged;
        private Boolean attachmentsChanged;
    }
}
