package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Deprecated // 使用动态表单声明式校验与SOL完成阻断投影。
public class RequirementAnalysisCompletionBlockerRespVO {
    private String code;
    private String sectionCode;
    private String fieldKey;
    private String message;

    public RequirementAnalysisCompletionBlockerRespVO(String code, String sectionCode) {
        this.code = code;
        this.sectionCode = sectionCode;
        this.fieldKey = sectionCode;
    }

    public RequirementAnalysisCompletionBlockerRespVO(String code, String fieldKey, String message) {
        this.code = code;
        this.sectionCode = fieldKey;
        this.fieldKey = fieldKey;
        this.message = message;
    }
}
