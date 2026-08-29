package cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CutoverConfigurationValidationRespVO {

    public static final String BASE_LOCATION_PREFIX = "base";
    public static final String RISK_LOCATION_PREFIX = "risk";
    public static final String SURVEY_LOCATION_PREFIX = "survey";

    private boolean valid;
    private List<ValidationErrorVO> errors;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationErrorVO {
        private String location;
        private String message;
    }
}
