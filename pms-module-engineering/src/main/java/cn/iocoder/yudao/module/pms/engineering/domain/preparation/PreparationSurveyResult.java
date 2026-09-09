package cn.iocoder.yudao.module.pms.engineering.domain.preparation;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.LinkedHashSet;
import java.util.Set;

/** PRE-02 / F-SOL-002: explicit survey business values, never a generic form map. */
@Getter
public class PreparationSurveyResult {
    @Size(max = 1000) private String powerSupply;
    @Size(max = 1000) private String powerEnvironment;
    @Size(max = 1000) private String networkPort;
    @Size(max = 1000) private String fiber;
    @Size(max = 1000) private String cabinet;
    @Size(max = 1000) private String networkCable;
    @Size(max = 1000) private String opticalModule;
    private Boolean cabinetAvailable;
    private Boolean networkCableAvailable;
    private Boolean opticalModuleAvailable;
    private Boolean originalOpticalModule;
    @JsonIgnore private final Set<String> submittedFields = new LinkedHashSet<>();

    public void setPowerSupply(String value) { powerSupply = value; submittedFields.add("powerSupply"); }
    public void setPowerEnvironment(String value) { powerEnvironment = value; submittedFields.add("powerEnvironment"); }
    public void setNetworkPort(String value) { networkPort = value; submittedFields.add("networkPort"); }
    public void setFiber(String value) { fiber = value; submittedFields.add("fiber"); }
    public void setCabinet(String value) { cabinet = value; submittedFields.add("cabinet"); }
    public void setNetworkCable(String value) { networkCable = value; submittedFields.add("networkCable"); }
    public void setOpticalModule(String value) { opticalModule = value; submittedFields.add("opticalModule"); }
    public void setCabinetAvailable(Boolean value) { cabinetAvailable = value; submittedFields.add("cabinetAvailable"); }
    public void setNetworkCableAvailable(Boolean value) { networkCableAvailable = value; submittedFields.add("networkCableAvailable"); }
    public void setOpticalModuleAvailable(Boolean value) { opticalModuleAvailable = value; submittedFields.add("opticalModuleAvailable"); }
    public void setOriginalOpticalModule(Boolean value) { originalOpticalModule = value; submittedFields.add("originalOpticalModule"); }

    @JsonIgnore
    public Set<String> getSubmittedFields() { return Set.copyOf(submittedFields); }

    @JsonAnySetter
    public void rejectUnknown(String name, JsonNode ignored) {
        throw new IllegalArgumentException("Unsupported survey result field: " + name);
    }
}
