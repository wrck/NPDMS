package cn.iocoder.yudao.module.pms.engineering.dal.dataobject.preparation;

import cn.iocoder.yudao.module.pms.engineering.domain.preparation.PreparationSurveyResult;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** PRE-02: row identity is the preparation item identity. */
@Getter
@Setter
public class PreparationSurveyResultDO extends PreparationSurveyResult {
    private Long itemId;
    private Long preparationId;
    private Long tenantId;
    private String creator;
    private LocalDateTime createTime;
    private String updater;
    private LocalDateTime updateTime;
}
