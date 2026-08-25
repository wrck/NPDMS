package cn.iocoder.yudao.module.pms.project.controller.admin.taskworkbench.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
public class ProjectTaskUpdateReqVO {
    @Size(max = 128) private String name;
    @Size(max = 64) private String businessLevelCode;
    private LocalDateTime planStartTime;
    private LocalDateTime planEndTime;
    private Integer priority;
    private Integer sortOrder;
    @Size(max = 500) private String description;
    @JsonIgnore private final Set<String> submittedFields = new LinkedHashSet<>();

    public void setName(String value) { name = value; submittedFields.add("name"); }
    public void setBusinessLevelCode(String value) {
        businessLevelCode = value; submittedFields.add("businessLevelCode");
    }
    public void setPlanStartTime(LocalDateTime value) { planStartTime = value; submittedFields.add("planStartTime"); }
    public void setPlanEndTime(LocalDateTime value) { planEndTime = value; submittedFields.add("planEndTime"); }
    public void setPriority(Integer value) { priority = value; submittedFields.add("priority"); }
    public void setSortOrder(Integer value) { sortOrder = value; submittedFields.add("sortOrder"); }
    public void setDescription(String value) { description = value; submittedFields.add("description"); }

    @JsonIgnore
    public Set<String> getSubmittedFields() {
        return Set.copyOf(submittedFields);
    }
}
