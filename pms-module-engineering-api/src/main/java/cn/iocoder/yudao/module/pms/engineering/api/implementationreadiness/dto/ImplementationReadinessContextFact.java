package cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto;

import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.ImplementationReadinessException;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

public record ImplementationReadinessContextFact(
        Long projectScopeVersion,
        List<DeviceFact> devices,
        ApprovedPlanFact approvedPlan,
        List<SourceFact> sourceFacts) {

    public ImplementationReadinessContextFact {
        if (projectScopeVersion == null || projectScopeVersion < 0 || devices == null || devices.isEmpty()
                || approvedPlan == null || sourceFacts == null || sourceFacts.size() != 4) {
            throw corrupted("invalid implementation readiness context");
        }
        devices = devices.stream().sorted(Comparator.comparing(DeviceFact::deviceId)).toList();
        requireUnique(devices.stream().map(DeviceFact::deviceId).toList(), "deviceId");
        requireUnique(devices.stream().map(device -> ImplementationReadinessQuery.comparisonKey(
                device.serialNumber())).toList(), "serialNumber");
        sourceFacts = sourceFacts.stream().sorted(Comparator.comparing(SourceFact::sourceCode)).toList();
        if (!sourceFacts.stream().map(SourceFact::sourceCode).toList().equals(List.of(
                SourceCode.EXE_01, SourceCode.EXE_02, SourceCode.EXE_03, SourceCode.EXE_04))) {
            throw corrupted("sourceFacts must contain EXE_01..EXE_04 exactly once");
        }
    }

    private static void requireUnique(List<?> values, String field) {
        if (new HashSet<>(values).size() != values.size()) {
            throw corrupted("duplicate " + field);
        }
    }

    static ImplementationReadinessException corrupted(String message) {
        return new ImplementationReadinessException(
                ImplementationReadinessException.Code.OWNER_DATA_CORRUPTED, message);
    }

    public record DeviceFact(Long deviceId, String serialNumber, Long projectAssignmentVersion) {
        public DeviceFact {
            if (deviceId == null || deviceId <= 0 || serialNumber == null || serialNumber.isBlank()
                    || !serialNumber.equals(serialNumber.trim()) || serialNumber.length() > 128
                    || projectAssignmentVersion == null || projectAssignmentVersion < 0) {
                throw corrupted("invalid device fact");
            }
        }
    }

    public record ApprovedPlanFact(Long planId, Long planVersion) {
        public ApprovedPlanFact {
            if (planId == null || planId <= 0 || planVersion == null || planVersion < 0) {
                throw corrupted("invalid approved plan fact");
            }
        }
    }

    public record SourceFact(
            SourceCode sourceCode,
            CompletionStatus completionStatus,
            Long factVersion,
            List<Long> sourceObjectIds,
            List<WatermarkEntry> watermarkEntries,
            Boolean reopened) {
        public SourceFact {
            if (sourceCode == null || completionStatus == null || factVersion == null || factVersion < 0
                    || sourceObjectIds == null || sourceObjectIds.isEmpty()
                    || watermarkEntries == null || watermarkEntries.isEmpty() || reopened == null) {
                throw corrupted("invalid source fact");
            }
            if (sourceCode == SourceCode.EXE_01 && completionStatus == CompletionStatus.COMPLETED
                    || sourceCode != SourceCode.EXE_01 && completionStatus == CompletionStatus.ACCEPTED) {
                throw corrupted("source completion status does not match its requirement");
            }
            sourceObjectIds = sourceObjectIds.stream().sorted().toList();
            requireUnique(sourceObjectIds, "sourceObjectId");
            watermarkEntries = watermarkEntries.stream().sorted(Comparator
                    .comparing(WatermarkEntry::axisCode)
                    .thenComparing(WatermarkEntry::objectId)).toList();
            requireUnique(watermarkEntries.stream()
                    .map(entry -> entry.axisCode() + ":" + entry.objectId()).toList(), "watermark axis");
        }
    }

    public record WatermarkEntry(String axisCode, Long objectId, Long version) {
        public WatermarkEntry {
            if (axisCode == null || axisCode.isBlank() || !axisCode.equals(axisCode.trim())
                    || axisCode.length() > 64 || !axisCode.matches("[A-Z][A-Z0-9_]*")
                    || objectId == null || objectId <= 0 || version == null || version < 0) {
                throw corrupted("invalid source watermark entry");
            }
        }
    }

    public enum SourceCode { EXE_01, EXE_02, EXE_03, EXE_04 }

    public enum CompletionStatus { ACCEPTED, COMPLETED, NOT_COMPLETED }
}
