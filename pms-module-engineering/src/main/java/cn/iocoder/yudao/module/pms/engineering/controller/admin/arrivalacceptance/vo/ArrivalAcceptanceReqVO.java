package cn.iocoder.yudao.module.pms.engineering.controller.admin.arrivalacceptance.vo;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Task 8 到货签收严格请求模型。 */
public final class ArrivalAcceptanceReqVO {

    private ArrivalAcceptanceReqVO() {
    }

    public record Create(Long projectId, String batchCode, String logisticsNo,
                         LocalDateTime arrivedAt, String signerName,
                         Long expectedDeliveryScopeVersion) implements StrictRequest {
    }

    public record Patch(String logisticsNo, LocalDateTime arrivedAt, String signerName,
                        List<DraftLine> lines, FileRevision evidenceRevision) implements StrictRequest {
    }

    public record RaiseDifference(Long arrivalLineId, Integer expectedLineVersion,
                                  String differenceTypeCode, Scope scopeSnapshot,
                                  String reason, String riskDescription,
                                  FileRevision evidenceRevision) implements StrictRequest {
    }

    public record FileRevision(Long artifactId, String referenceKey, Integer versionNo,
                               Long scopeVersion, FileFactVersion fileFactVersion, String hash) implements StrictRequest {
    }

    public record FileFactVersion(Integer artifactVersion, Integer referenceVersion,
                                  Integer availabilityVersion) implements StrictRequest {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
            property = "scopeType", visible = true)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = DeviceScope.class, name = "DEVICE"),
            @JsonSubTypes.Type(value = QuantityScope.class, name = "ORDER_MODEL_QUANTITY")
    })
    public sealed interface Scope extends StrictRequest permits DeviceScope, QuantityScope {
        String scopeType();
    }

    public record DeviceScope(String scopeType, Long deviceId) implements Scope {
    }

    public record QuantityScope(String scopeType, Long orderLineId, String productCode,
                                String modelCode, BigDecimal quantity, String unitCode) implements Scope {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
            property = "scopeType", visible = true)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = DeviceDraftLine.class, name = "DEVICE"),
            @JsonSubTypes.Type(value = QuantityDraftLine.class, name = "ORDER_MODEL_QUANTITY")
    })
    public sealed interface DraftLine extends StrictRequest permits DeviceDraftLine, QuantityDraftLine {
        String scopeType();
        Long lineId();
        Integer expectedLineVersion();
    }

    public record DeviceDraftLine(String scopeType, Long lineId, Integer expectedLineVersion,
                                  Long deviceId, Boolean received) implements DraftLine {
    }

    public record QuantityDraftLine(String scopeType, Long lineId, Integer expectedLineVersion,
                                    Long orderLineId, String productCode, String modelCode,
                                    BigDecimal acceptedQuantity, String unitCode) implements DraftLine {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
            property = "resolutionType", visible = true)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Supplement.class, name = "SUPPLEMENT"),
            @JsonSubTypes.Type(value = KeepRejected.class, name = "KEEP_REJECTED"),
            @JsonSubTypes.Type(value = Exempt.class, name = "EXEMPT"),
            @JsonSubTypes.Type(value = Close.class, name = "CLOSE"),
            @JsonSubTypes.Type(value = CorrectInformation.class, name = "CORRECT_INFORMATION")
    })
    public sealed interface Resolution extends StrictRequest permits Supplement, KeepRejected, Exempt, Close, CorrectInformation {
        String resolutionType();
    }

    public record Supplement(String resolutionType, Long differenceId,
                             Integer expectedDifferenceRevision, Integer expectedDifferenceVersion,
                             Scope supplementScope, String reason,
                             FileRevision evidenceRevision) implements Resolution {
    }

    public record KeepRejected(String resolutionType, Long differenceId,
                               Integer expectedDifferenceRevision, Integer expectedDifferenceVersion,
                               String reason, FileRevision evidenceRevision) implements Resolution {
    }

    public record Exempt(String resolutionType, Long differenceId,
                         Integer expectedDifferenceRevision, Integer expectedDifferenceVersion,
                         String reason, String riskDescription, LocalDateTime expiresAt,
                         FileRevision evidenceRevision) implements Resolution {
    }

    public record Close(String resolutionType, Long differenceId,
                        Integer expectedDifferenceRevision, Integer expectedDifferenceVersion,
                        String reason, FileRevision evidenceRevision) implements Resolution {
    }

    public record CorrectInformation(String resolutionType, Integer expectedSourceVersion,
                                     String reason, CorrectionPatch correctionPatch,
                                     FileRevision evidenceRevision) implements Resolution {
    }

    public record CorrectionPatch(String logisticsNo, LocalDateTime arrivedAt,
                                  String signerName, List<DraftLine> lines) implements StrictRequest {
    }

    public interface StrictRequest {
        @JsonAnySetter
        default void rejectUnknown(String field, Object ignored) {
            throw new IllegalArgumentException("unknown arrival request field: " + field);
        }
    }
}
