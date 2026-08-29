package cn.iocoder.yudao.module.pms.cutover.controller.admin.configuration.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CutoverConfigurationValidationRespVO {
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
