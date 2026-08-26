package cn.iocoder.yudao.module.pms.engineering.controller.admin.preparation.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Getter
public class PreparationItemPatchReqVO {
    @NotNull private Integer expectedPreparationVersion;
    @NotNull private Integer expectedInputVersion;
    @NotNull private Integer expectedReadinessVersion;
    @NotNull private Integer expectedFormVersion;
    @NotNull private Integer expectedProjectVersion;
    private String applicabilityCode;
    private Boolean outsourced;
    private Long assigneeUserId;
    @Size(max = 2000) private String notApplicableReason;
    @Size(max = 64) private String siteResultCode;
    @Size(max = 2000) private String siteResultDetail;
    private String formValueSnapshot;
    @Valid private List<PreparationEvidenceReferenceReqVO> evidenceReferences;
    @JsonIgnore private final Set<String> submittedFields = new LinkedHashSet<>();

    public void setExpectedPreparationVersion(Integer value) { expectedPreparationVersion = value; }
    public void setExpectedInputVersion(Integer value) { expectedInputVersion = value; }
    public void setExpectedReadinessVersion(Integer value) { expectedReadinessVersion = value; }
    public void setExpectedFormVersion(Integer value) { expectedFormVersion = value; }
    public void setExpectedProjectVersion(Integer value) { expectedProjectVersion = value; }
    public void setApplicabilityCode(String value) { applicabilityCode = value; submittedFields.add("applicabilityCode"); }
    public void setOutsourced(Boolean value) { outsourced = value; submittedFields.add("outsourced"); }
    public void setAssigneeUserId(Long value) { assigneeUserId = value; submittedFields.add("assignee"); }
    public void setNotApplicableReason(String value) { notApplicableReason = value; submittedFields.add("notApplicableReason"); }
    public void setSiteResultCode(String value) { siteResultCode = value; submittedFields.add("siteResultCode"); }
    public void setSiteResultDetail(String value) { siteResultDetail = value; submittedFields.add("siteResultDetail"); }
    public void setFormValueSnapshot(String value) { formValueSnapshot = value; submittedFields.add("formValueSnapshot"); }
    public void setEvidenceReferences(List<PreparationEvidenceReferenceReqVO> value) {
        evidenceReferences = value; submittedFields.add("evidenceReferences");
    }

    @JsonIgnore
    public Set<String> getSubmittedFields() { return Set.copyOf(submittedFields); }
}
