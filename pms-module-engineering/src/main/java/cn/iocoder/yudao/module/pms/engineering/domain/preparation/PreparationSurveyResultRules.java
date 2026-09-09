package cn.iocoder.yudao.module.pms.engineering.domain.preparation;

import java.util.Set;
import java.util.stream.Stream;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.engineering.enums.ErrorCodeConstants.*;

/** PRE-02: incomplete drafts are allowed; no synthetic PASS/siteCondition values. */
public final class PreparationSurveyResultRules {
    private PreparationSurveyResultRules() {}

    public static void validatePatch(String itemCode, PreparationSurveyResult result) {
        if (result == null || !fields(itemCode).containsAll(result.getSubmittedFields())) {
            throw exception(PREPARATION_COMMAND_INVALID);
        }
        if (Stream.of(result.getPowerSupply(), result.getPowerEnvironment(), result.getNetworkPort(),
                result.getFiber(), result.getCabinet(), result.getNetworkCable(), result.getOpticalModule())
                .anyMatch(value -> value != null && value.length() > 1000)) {
            throw exception(PREPARATION_COMMAND_INVALID);
        }
    }

    public static Set<String> fields(String itemCode) {
        return switch (itemCode == null ? "" : itemCode) {
            case "POWER" -> Set.of("powerSupply", "powerEnvironment");
            case "NETWORK_PORT" -> Set.of("networkPort");
            case "FIBER" -> Set.of("fiber");
            case "CABINET" -> Set.of("cabinet", "cabinetAvailable");
            case "NETWORK_CABLE" -> Set.of("networkCable", "networkCableAvailable");
            case "OPTICAL_MODULE" -> Set.of("opticalModule", "opticalModuleAvailable", "originalOpticalModule");
            default -> throw exception(PREPARATION_COMMAND_INVALID);
        };
    }

    public static void requireComplete(String itemCode, PreparationSurveyResult result) {
        boolean complete = switch (itemCode == null ? "" : itemCode) {
            case "POWER" -> text(result.getPowerSupply()) || text(result.getPowerEnvironment());
            case "NETWORK_PORT" -> text(result.getNetworkPort());
            case "FIBER" -> text(result.getFiber());
            case "CABINET" -> text(result.getCabinet()) || result.getCabinetAvailable() != null;
            case "NETWORK_CABLE" -> text(result.getNetworkCable()) || result.getNetworkCableAvailable() != null;
            case "OPTICAL_MODULE" -> text(result.getOpticalModule()) || result.getOpticalModuleAvailable() != null;
            default -> false;
        };
        if (!complete) throw exception(PREPARATION_STATUS_INVALID);
    }

    public static String unavailableBlocker(String itemCode, PreparationSurveyResult result) {
        return switch (itemCode) {
            case "CABINET" -> Boolean.FALSE.equals(result.getCabinetAvailable()) ? "CABINET_UNAVAILABLE" : null;
            case "NETWORK_CABLE" -> Boolean.FALSE.equals(result.getNetworkCableAvailable()) ? "NETWORK_CABLE_UNAVAILABLE" : null;
            case "OPTICAL_MODULE" -> Boolean.FALSE.equals(result.getOpticalModuleAvailable()) ? "OPTICAL_MODULE_UNAVAILABLE" : null;
            default -> null;
        };
    }

    private static boolean text(String value) { return value != null && !value.isBlank(); }
}
