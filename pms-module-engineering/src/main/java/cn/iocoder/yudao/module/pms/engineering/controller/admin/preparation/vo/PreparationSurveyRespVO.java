package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class PreparationSurveyRespVO {
    private Long preparationId;
    @JsonFormat(pattern = "yyyy-MM-dd") private LocalDate surveyDate;
    private Long surveyorUserId;
    private String location;
    private String locationResolutionStatus;
    private Long addressId;
    private Integer addressVersion;
    private Long siteId;
    private Integer siteVersion;
    private Long siteLocationId;
    private Integer siteLocationVersion;
    private String grounding;
    private String constructionResource;
    private String conclusion;
    private Integer version;
    private List<String> allowedActions = List.of();
}
