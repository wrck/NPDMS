package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import cn.iocoder.yudao.module.pms.asset.api.location.dto.LocationMaintenanceCommand;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
public class PreparationSurveyPatchReqVO {
    @NotNull @PositiveOrZero private Integer expectedProjectVersion;
    @JsonFormat(pattern = "yyyy-MM-dd") private LocalDate surveyDate;
    @Positive private Long surveyorUserId;
    @Valid private LocationMaintenanceCommand locationCommand;
    @Size(max = 1000) private String grounding;
    @Size(max = 1000) private String constructionResource;
    @Size(max = 1000) private String conclusion;
    @JsonIgnore private final Set<String> submittedFields = new LinkedHashSet<>();

    public void setExpectedProjectVersion(Integer value) { expectedProjectVersion = value; }
    public void setSurveyDate(LocalDate value) { surveyDate = value; submittedFields.add("surveyDate"); }
    public void setSurveyorUserId(Long value) { surveyorUserId = value; submittedFields.add("surveyorUserId"); }
    public void setLocationCommand(LocationMaintenanceCommand value) { locationCommand = value; submittedFields.add("locationCommand"); }
    public void setGrounding(String value) { grounding = value; submittedFields.add("grounding"); }
    public void setConstructionResource(String value) { constructionResource = value; submittedFields.add("constructionResource"); }
    public void setConclusion(String value) { conclusion = value; submittedFields.add("conclusion"); }
    @JsonIgnore public Set<String> getSubmittedFields() { return Set.copyOf(submittedFields); }
}
