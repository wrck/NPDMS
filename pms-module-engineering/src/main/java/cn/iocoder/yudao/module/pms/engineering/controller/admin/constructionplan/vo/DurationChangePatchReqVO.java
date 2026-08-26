package cn.iocoder.yudao.module.pms.engineering.controller.admin.constructionplan.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
public class DurationChangePatchReqVO {
    @NotNull @Min(0) private Integer expectedProjectVersion;
    @Size(max = 32) private String calculationBasis;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer durationDays;
    @Size(max = 64) private String reasonType;
    @Size(max = 1000) private String reasonDetail;
    private Long customerEvidenceFileId;
    private Integer customerEvidenceFileVersion;
    @Size(max = 128) private String customerEvidenceReferenceKey;
    @JsonIgnore private final Set<String> submittedFields = new LinkedHashSet<>();

    public void setExpectedProjectVersion(Integer value) { expectedProjectVersion = value; }
    public void setCalculationBasis(String value) { calculationBasis = value; submittedFields.add("calculationBasis"); }
    public void setStartDate(LocalDate value) { startDate = value; submittedFields.add("startDate"); }
    public void setEndDate(LocalDate value) { endDate = value; submittedFields.add("endDate"); }
    public void setDurationDays(Integer value) { durationDays = value; submittedFields.add("durationDays"); }
    public void setReasonType(String value) { reasonType = value; submittedFields.add("reasonType"); }
    public void setReasonDetail(String value) { reasonDetail = value; submittedFields.add("reasonDetail"); }
    public void setCustomerEvidenceFileId(Long value) {
        customerEvidenceFileId = value; submittedFields.add("customerEvidenceFileId");
    }
    public void setCustomerEvidenceFileVersion(Integer value) {
        customerEvidenceFileVersion = value; submittedFields.add("customerEvidenceFileVersion");
    }
    public void setCustomerEvidenceReferenceKey(String value) {
        customerEvidenceReferenceKey = value; submittedFields.add("customerEvidenceReferenceKey");
    }

    @JsonIgnore
    public Set<String> getSubmittedFields() { return Set.copyOf(submittedFields); }
}
