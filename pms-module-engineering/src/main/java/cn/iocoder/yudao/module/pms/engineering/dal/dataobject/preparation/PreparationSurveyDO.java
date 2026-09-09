package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** PRE-02 / F-SOL-002 survey metadata. Only location history is JSON. */
@Data
public class PreparationSurveyDO {
    private Long preparationId;
    private Long tenantId;
    private LocalDate surveyDate;
    private Long surveyorUserId;
    private String location;
    private String locationResolutionStatus;
    private Long addressId;
    private Integer addressVersion;
    private Long siteId;
    private Integer siteVersion;
    private Long siteLocationId;
    private Integer siteLocationVersion;
    private String addressSnapshot;
    private String locationSnapshot;
    private String grounding;
    private String constructionResource;
    private String conclusion;
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;
}
